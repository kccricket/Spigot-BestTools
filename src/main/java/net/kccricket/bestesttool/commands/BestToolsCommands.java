package net.kccricket.bestesttool.commands;

import net.kccricket.bestesttool.BestestToolPlugin;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kccricket.bestesttool.security.Permissions;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.kccricket.bestesttool.benchmark.BenchmarkWorkload;
import net.kccricket.bestesttool.listeners.BestToolsListener;

/**
 * Builds the {@code /bestesttool} Brigadier command tree, plus the {@code /refill}/{@code /rf}
 * alias that shares its {@code refill} child node's command. Replaces the old {@code besttools}/
 * {@code refill} commands that were registered directly against the server's command map via
 * {@code DelegatingCommand} — {@code paper-plugin.yml} still cannot declare a {@code commands:}
 * block, but Paper's Brigadier {@code LifecycleEvents.COMMANDS} registrar (see
 * {@link BestestToolPlugin#onEnable}) supersedes that workaround and adds real per-argument suggestions and
 * client-side validation.
 *
 * <p>Built and registered exactly once, in {@link BestestToolPlugin#onEnable} — {@code /bestesttool
 * admin reload} ({@link BestestToolPlugin#reload}) only re-reads config, it does not rebuild this
 * tree or the {@code main.command*} fields it dereferences.
 */
public final class BestToolsCommands {

    private BestToolsCommands() {}

    /**
     * Materials offered as completions for {@code blacklist add}: non-legacy materials that are
     * blocks, since the blacklist is only ever checked against a mined block's type (see
     * {@code BestToolsListener.onPlayerInteractWithBlock}), never against held items.
     */
    static final Predicate<Material> SUGGESTABLE_MATERIAL = mat -> !mat.isLegacy() && mat.isBlock();

    static final List<String> SUGGESTABLE_MATERIAL_NAMES = Arrays.stream(Material.values())
            .filter(SUGGESTABLE_MATERIAL)
            .map(m -> m.name().toLowerCase(Locale.ROOT))
            .toList();

    // -------------------------------------------------------------------------
    // /bestesttool
    // -------------------------------------------------------------------------

    public static LiteralCommandNode<CommandSourceStack> buildBestTools(BestestToolPlugin main) {
        return Commands.literal("bestesttool")
                .executes(ctx -> {
                    Player player = requirePlayer(main, ctx);
                    if (player == null || throttled(main, player)
                            || !checkPermission(main, player, Permissions.PERM_USE)) {
                        return Command.SINGLE_SUCCESS;
                    }
                    main.commandBestTools.toggleBestTools(player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(buildToggleHotbarOnly(main))
                .then(buildFavoriteSlot(main))
                .then(buildRefill(main))
                .then(buildBlacklist(main))
                .then(buildAdmin(main))
                .build();
    }

    // -------------------------------------------------------------------------
    // /bestesttool admin — groups the admin-only subcommands (reload, debug, selftest,
    // benchmark) under a single node, mirroring the bestesttool.admin permission's children in
    // paper-plugin.yml. The "admin" node itself carries no .requires: a sender granted only one
    // child permission (e.g. bestesttool.admin.reload, without bestesttool.admin) must still be able to
    // reach that child, so visibility is left to each child's own .requires, same as before.
    // -------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> buildAdmin(BestestToolPlugin main) {
        return Commands.literal("admin")
                .then(buildReload(main))
                .then(buildDebug(main))
                .then(buildSelfTest(main))
                .then(buildBenchmark(main));
    }

    /**
     * Builds the {@code /refill}/{@code /rf} root alias. Must be built and registered after
     * {@code bestToolsRoot} so it can reuse that node's already-built {@code refill} child's
     * {@link Command}. Deliberately does <em>not</em> {@code .redirect(...)} at the canonical
     * node: a node that is both executable and a redirect is a valid Brigadier tree to build, but
     * the vanilla client rejects it when synced, disconnecting with "Server sent an impossible
     * command tree". A plain {@code .executes(...)} of the same {@link Command} instance is
     * sufficient — {@code /refill} and {@code /rf} behave identically to
     * {@code /bestesttool refill}, each with its own copy of the {@code [<state>]} child
     * ({@link #refillStateArg}) rather than sharing the canonical node's {@code CommandNode}
     * instance, since a single node can't be attached under two different roots.
     */
    public static LiteralCommandNode<CommandSourceStack> buildRefillAlias(
            BestestToolPlugin main, LiteralCommandNode<CommandSourceStack> bestToolsRoot) {
        var canonicalRefill = bestToolsRoot.getChild("refill");
        return Commands.literal("refill")
                .executes(canonicalRefill.getCommand())
                .then(refillStateArg(main))
                .build();
    }

    // -------------------------------------------------------------------------
    // /bestesttool hotbaronly
    // -------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> buildToggleHotbarOnly(BestestToolPlugin main) {
        return Commands.literal("hotbaronly")
                .executes(ctx -> {
                    Player player = requirePlayer(main, ctx);
                    if (player == null || !checkPermission(main, player, Permissions.PERM_USE)) {
                        return Command.SINGLE_SUCCESS;
                    }
                    main.commandBestTools.toggleHotbarOnly(player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(boolStateArg(main, Permissions.PERM_USE,
                        (player, state) -> main.commandBestTools.setHotbarOnly(player, state)));
    }

    // -------------------------------------------------------------------------
    // /bestesttool favoriteslot
    // -------------------------------------------------------------------------

    /**
     * Reports (bare) or sets (with a {@code <slot>} argument) the hotbar slot BestTools should
     * place a tool in when it has to make room. {@code -1} carries the same "use whatever slot
     * I'm currently holding" meaning as {@code defaults.favorite_slot: -1} in {@code config.yml}
     * (see {@link net.kccricket.bestesttool.config.MainConfig#getDefaultFavoriteSlot}) — reusing
     * that sentinel keeps the per-player and server-wide settings semantically identical, so no
     * separate "unset" state is needed. The {@code -1..8} range is enforced client-side by
     * {@link IntegerArgumentType}, unlike the string-based {@link #boolStateArg}.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> buildFavoriteSlot(BestestToolPlugin main) {
        return Commands.literal("favoriteslot")
                .executes(ctx -> {
                    Player player = requirePlayer(main, ctx);
                    if (player == null || !checkPermission(main, player, Permissions.PERM_USE)) {
                        return Command.SINGLE_SUCCESS;
                    }
                    main.commandBestTools.reportFavoriteSlot(player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("slot", IntegerArgumentType.integer(-1, 8))
                        .executes(ctx -> {
                            Player player = requirePlayer(main, ctx);
                            if (player == null || !checkPermission(main, player, Permissions.PERM_USE)) {
                                return Command.SINGLE_SUCCESS;
                            }
                            int slot = IntegerArgumentType.getInteger(ctx, "slot");
                            main.commandBestTools.setFavoriteSlot(player, slot);
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    // -------------------------------------------------------------------------
    // /bestesttool refill (also the canonical target of the /refill, /rf alias)
    // -------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> buildRefill(BestestToolPlugin main) {
        return Commands.literal("refill")
                .executes(ctx -> {
                    Player player = requirePlayer(main, ctx);
                    if (player == null || !checkPermission(main, player, Permissions.PERM_REFILL)) {
                        return Command.SINGLE_SUCCESS;
                    }
                    main.commandRefill.toggleRefill(player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(refillStateArg(main));
    }

    /**
     * The shared {@code [<state>]} child for {@code /bestesttool refill} and its {@code /refill},
     * {@code /rf} root aliases (see {@link #buildRefillAlias}) — built fresh per call site since a
     * single {@code CommandNode} instance can't be attached under two different roots.
     */
    private static RequiredArgumentBuilder<CommandSourceStack, String> refillStateArg(BestestToolPlugin main) {
        return boolStateArg(main, Permissions.PERM_REFILL,
                (player, state) -> main.commandRefill.setRefill(player, state));
    }

    // -------------------------------------------------------------------------
    // /bestesttool admin reload | debug — hidden from tab completion for senders lacking the
    // node (via .requires), rather than answered with noPermission.
    // -------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> buildReload(BestestToolPlugin main) {
        return Commands.literal("reload")
                .requires(src -> Permissions.isAllowedTo(src.getSender(), Permissions.PERM_RELOAD))
                .executes(ctx -> {
                    CommandReload.reload(ctx.getSource().getSender(), main);
                    return Command.SINGLE_SUCCESS;
                });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildDebug(BestestToolPlugin main) {
        return Commands.literal("debug")
                .requires(src -> Permissions.isAllowedTo(src.getSender(), Permissions.PERM_DEBUG))
                .executes(ctx -> {
                    CommandDebug.debug(ctx.getSource().getSender(), main, "debug");
                    return Command.SINGLE_SUCCESS;
                })
                .then(debugStateArg(main, "debug"));
    }

    /** A {@code <state>} child for {@code debug}, matching its own sender type (no player requirement). */
    private static RequiredArgumentBuilder<CommandSourceStack, String> debugStateArg(BestestToolPlugin main, String arg) {
        return Commands.argument("state", StringArgumentType.word())
                .suggests((ctx, b) -> suggestYesNo(b))
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    String raw = StringArgumentType.getString(ctx, "state");
                    Boolean state = parseState(raw);
                    if (state == null) {
                        sendInvalidValue(main, sender, raw);
                        return Command.SINGLE_SUCCESS;
                    }
                    CommandDebug.debug(sender, main, arg, state);
                    return Command.SINGLE_SUCCESS;
                });
    }

    // -------------------------------------------------------------------------
    // /bestesttool admin selftest — needs both the enable_selftest config flag and the
    // permission node, gated with .requires(...) like reload/debug so it's hidden from tab
    // completion (and unparseable) rather than answered with a noPermission message.
    // -------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> buildSelfTest(BestestToolPlugin main) {
        return Commands.literal("selftest")
                .requires(src -> main.getConfigManager().main().getEnableSelfTest()
                        && Permissions.isAllowedTo(src.getSender(), Permissions.PERM_SELFTEST))
                .then(Commands.literal("start")
                        .executes(ctx -> runSelfTestStart(main, ctx, null))
                        .then(Commands.argument("stage", StringArgumentType.word())
                                .suggests((ctx, b) -> suggestToken(b, main.selfTestManager.stageNames()))
                                .executes(ctx -> runSelfTestStart(main, ctx, StringArgumentType.getString(ctx, "stage")))))
                .then(Commands.literal("next").executes(ctx -> {
                    Player player = requirePlayer(main, ctx);
                    if (player == null) return Command.SINGLE_SUCCESS;
                    main.commandSelfTest.next(player);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    Player player = requirePlayer(main, ctx);
                    if (player == null) return Command.SINGLE_SUCCESS;
                    main.commandSelfTest.status(player);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("stop").executes(ctx -> {
                    Player player = requirePlayer(main, ctx);
                    if (player == null) return Command.SINGLE_SUCCESS;
                    main.commandSelfTest.stop(player);
                    return Command.SINGLE_SUCCESS;
                }));
    }

    private static int runSelfTestStart(BestestToolPlugin main, CommandContext<CommandSourceStack> ctx, @Nullable String stageName) {
        Player player = requirePlayer(main, ctx);
        if (player == null) return Command.SINGLE_SUCCESS;
        main.commandSelfTest.start(player, stageName);
        return Command.SINGLE_SUCCESS;
    }

    // -------------------------------------------------------------------------
    // /bestesttool admin benchmark — needs both the enable_benchmark config flag and the
    // permission node, gated the same way as selftest. Unlike selftest's subcommands, these take
    // a plain CommandSender (no requirePlayer(ctx)): the workload is synthetic, so the command
    // works from console too.
    // -------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> buildBenchmark(BestestToolPlugin main) {
        return Commands.literal("benchmark")
                .requires(src -> main.getConfigManager().main().getEnableBenchmark()
                        && Permissions.isAllowedTo(src.getSender(), Permissions.PERM_BENCHMARK))
                .then(Commands.literal("start")
                        .executes(ctx -> runBenchmarkStart(main, ctx, BenchmarkWorkload.KitSize.FULL))
                        .then(Commands.literal("full")
                                .executes(ctx -> runBenchmarkStart(main, ctx, BenchmarkWorkload.KitSize.FULL)))
                        .then(Commands.literal("hotbar")
                                .executes(ctx -> runBenchmarkStart(main, ctx, BenchmarkWorkload.KitSize.HOTBAR))))
                .then(Commands.literal("status").executes(ctx -> {
                    main.commandBenchmark.status(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("stop").executes(ctx -> {
                    main.commandBenchmark.stop(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                }));
    }

    private static int runBenchmarkStart(BestestToolPlugin main, CommandContext<CommandSourceStack> ctx, BenchmarkWorkload.KitSize kitSize) {
        main.commandBenchmark.start(ctx.getSource().getSender(), kitSize);
        return Command.SINGLE_SUCCESS;
    }

    // -------------------------------------------------------------------------
    // /bestesttool blacklist
    // -------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> buildBlacklist(BestestToolPlugin main) {
        return Commands.literal("blacklist")
                .executes(ctx -> runBlacklistShow(main, ctx))
                .then(Commands.literal("show").executes(ctx -> runBlacklistShow(main, ctx)))
                .then(buildBlacklistAddRemove(main, "add", true))
                .then(buildBlacklistAddRemove(main, "remove", false))
                .then(Commands.literal("reset")
                        .executes(ctx -> {
                            Player player = requirePlayer(main, ctx);
                            if (player == null || !checkPermission(main, player, Permissions.PERM_USE)) {
                                return Command.SINGLE_SUCCESS;
                            }
                            main.commandBlacklist.reset(player);
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static int runBlacklistShow(BestestToolPlugin main, CommandContext<CommandSourceStack> ctx) {
        Player player = requirePlayer(main, ctx);
        if (player == null || !checkPermission(main, player, Permissions.PERM_USE)) {
            return Command.SINGLE_SUCCESS;
        }
        main.commandBlacklist.show(player);
        return Command.SINGLE_SUCCESS;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildBlacklistAddRemove(
            BestestToolPlugin main, String literal, boolean add) {
        return Commands.literal(literal)
                .executes(ctx -> {
                    Player player = requirePlayer(main, ctx);
                    if (player == null || throttled(main, player)
                            || !checkPermission(main, player, Permissions.PERM_USE)) {
                        return Command.SINGLE_SUCCESS;
                    }
                    main.commandBlacklist.addOrRemove(player, add, List.of());
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("inventory")
                        .executes(ctx -> runBlacklistFromInventory(main, ctx, add, false)))
                .then(Commands.literal("hotbar")
                        .executes(ctx -> runBlacklistFromInventory(main, ctx, add, true)))
                .then(Commands.argument("materials", StringArgumentType.greedyString())
                        .suggests((ctx, builder) -> add
                                ? suggestToken(builder, SUGGESTABLE_MATERIAL_NAMES)
                                : suggestBlacklistedMaterialToken(main, ctx, builder))
                        .executes(ctx -> {
                            Player player = requirePlayer(main, ctx);
                            if (player == null || throttled(main, player)
                                    || !checkPermission(main, player, Permissions.PERM_USE)) {
                                return Command.SINGLE_SUCCESS;
                            }
                            String raw = StringArgumentType.getString(ctx, "materials");
                            List<String> materials = Arrays.stream(raw.split("\\s+"))
                                    .filter(s -> !s.isEmpty())
                                    .toList();
                            main.commandBlacklist.addOrRemove(player, add, materials);
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static int runBlacklistFromInventory(
            BestestToolPlugin main, CommandContext<CommandSourceStack> ctx, boolean add, boolean hotbarOnly) {
        Player player = requirePlayer(main, ctx);
        if (player == null || throttled(main, player)
                || !checkPermission(main, player, Permissions.PERM_USE)) {
            return Command.SINGLE_SUCCESS;
        }
        main.commandBlacklist.addOrRemoveFromInventory(player, add, hotbarOnly);
        return Command.SINGLE_SUCCESS;
    }

    /** The sender's currently-blacklisted material names, lower-cased; empty for a non-player. */
    static List<String> blacklistedMaterialNames(BestestToolPlugin main, CommandSender sender) {
        return sender instanceof Player player
                ? main.getPlayerSetting(player).getBlacklist().toStringList().stream()
                        .map(s -> s.toLowerCase(Locale.ROOT))
                        .toList()
                : List.of();
    }

    /** Suggests the currently-blacklisted materials for {@code blacklist remove}. */
    static CompletableFuture<Suggestions> suggestBlacklistedMaterialToken(
            BestestToolPlugin main, CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggestToken(builder, blacklistedMaterialNames(main, ctx.getSource().getSender()));
    }

    /**
     * Suggests {@code candidates} matching the last whitespace-separated token of a greedy-string
     * argument's remaining input, replacing only that token (not everything typed so far).
     */
    static CompletableFuture<Suggestions> suggestToken(SuggestionsBuilder builder, List<String> candidates) {
        String remaining = builder.getRemaining();
        int lastSpace = remaining.lastIndexOf(' ');
        String prefix = (lastSpace >= 0 ? remaining.substring(lastSpace + 1) : remaining).toLowerCase(Locale.ROOT);
        SuggestionsBuilder tokenBuilder = builder.createOffset(builder.getStart() + lastSpace + 1);
        for (String candidate : candidates) {
            if (candidate.startsWith(prefix)) tokenBuilder.suggest(candidate);
        }
        return tokenBuilder.buildFuture();
    }

    // -------------------------------------------------------------------------
    // Shared helpers — boolean [<state>] toggles (mirrors ClickSorted's boolStateArg)
    // -------------------------------------------------------------------------

    private static final Set<String> ON_WORDS = Set.of("enable", "on", "true", "yes");
    private static final Set<String> OFF_WORDS = Set.of("disable", "off", "false", "no");
    private static final String BOOLEAN_VALUES = "yes, no";

    /** {@code true}/{@code false} for recognized on/off words, or {@code null} if unrecognized. */
    private static Boolean parseState(String s) {
        String lower = s.toLowerCase(Locale.ROOT);
        if (ON_WORDS.contains(lower)) return true;
        if (OFF_WORDS.contains(lower)) return false;
        return null;
    }

    private static CompletableFuture<Suggestions> suggestYesNo(SuggestionsBuilder builder) {
        builder.suggest("yes");
        builder.suggest("no");
        return builder.buildFuture();
    }

    /** Send the shared "invalid value" error naming {@code raw} and the valid yes/no words. */
    private static void sendInvalidValue(BestestToolPlugin main, CommandSender sender, String raw) {
        main.messages().to(sender).error().send("invalidValue",
                Placeholder.unparsed("value", raw),
                Placeholder.unparsed("valid", BOOLEAN_VALUES));
    }

    /**
     * A {@code <state>} child accepting yes/no (and the other {@link #ON_WORDS}/{@link #OFF_WORDS})
     * for a player-facing boolean toggle, gated the same way as its parent literal's bare toggle:
     * an explicit {@code node} permission check with a {@code noPermission} message on denial.
     */
    private static RequiredArgumentBuilder<CommandSourceStack, String> boolStateArg(
            BestestToolPlugin main, String permissionNode, BiConsumer<Player, Boolean> apply) {
        return Commands.argument("state", StringArgumentType.word())
                .suggests((ctx, b) -> suggestYesNo(b))
                .executes(ctx -> {
                    Player player = requirePlayer(main, ctx);
                    if (player == null || !checkPermission(main, player, permissionNode)) {
                        return Command.SINGLE_SUCCESS;
                    }
                    String raw = StringArgumentType.getString(ctx, "state");
                    Boolean state = parseState(raw);
                    if (state == null) {
                        sendInvalidValue(main, player, raw);
                        return Command.SINGLE_SUCCESS;
                    }
                    apply.accept(player, state);
                    return Command.SINGLE_SUCCESS;
                });
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    /**
     * Shared throttle gate for command executors, mirroring ClickSorted's
     * {@code ClickSortedCommands.throttled}: on denial, {@link net.kccricket.bestesttool.security.ActionThrottle}
     * itself sends the rate-limited {@code actionTooFast} notice.
     */
    private static boolean throttled(BestestToolPlugin main, Player player) {
        return main.getActionThrottle().throttled(player);
    }

    /** Sends {@code noPermission} and returns {@code false} unless {@code sender} has {@code node}. */
    private static boolean checkPermission(BestestToolPlugin main, CommandSender sender, String node) {
        if (Permissions.isAllowedTo(sender, node)) return true;
        main.messages().to(sender).error().send("noPermission", Placeholder.unparsed("plugin", main.getName()));
        return false;
    }

    /** Returns the sender as a {@link Player}, or sends {@code notAPlayer} and returns {@code null}. */
    @Nullable
    private static Player requirePlayer(BestestToolPlugin main, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) return player;
        main.messages().to(sender).error().send("notAPlayer");
        return null;
    }
}
