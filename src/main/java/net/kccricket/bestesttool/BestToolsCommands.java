package net.kccricket.bestesttool;

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
import net.kccricket.bestesttool.text.MessageUtil;

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

/**
 * Builds the {@code /bestesttool} Brigadier command tree, plus the {@code /refill}/{@code /rf}
 * alias that shares its {@code refill} child node's command. Replaces the old {@code besttools}/
 * {@code refill} commands that were registered directly against the server's command map via
 * {@code DelegatingCommand} — {@code paper-plugin.yml} still cannot declare a {@code commands:}
 * block, but Paper's Brigadier {@code LifecycleEvents.COMMANDS} registrar (see
 * {@link Main#onEnable}) supersedes that workaround and adds real per-argument suggestions and
 * client-side validation.
 *
 * <p>Every {@code executes} body dereferences {@code main.command*} fields rather than closing
 * over a local reference, because {@link Main#load} reassigns all of them on
 * {@code /bestesttool reload} while this tree is built and registered exactly once, in
 * {@code onEnable}.
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

    public static LiteralCommandNode<CommandSourceStack> buildBestTools(Main main) {
        return Commands.literal("bestesttool")
                .executes(ctx -> {
                    Player player = requirePlayer(ctx);
                    if (player == null || !checkPermission(main, player, Permissions.PERM_USE)) {
                        return Command.SINGLE_SUCCESS;
                    }
                    main.commandBestTools.toggleBestTools(player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(buildToggleHotbarOnly(main))
                .then(buildFavoriteSlot(main))
                .then(buildRefill(main))
                .then(buildReload(main))
                .then(buildDebug(main))
                .then(buildPerformance(main))
                .then(buildBlacklist(main))
                .build();
    }

    /**
     * Builds the {@code /refill}/{@code /rf} root alias. Must be built and registered after
     * {@code bestToolsRoot} so it can reuse that node's already-built {@code refill} child's
     * {@link Command}. Deliberately does <em>not</em> {@code .redirect(...)} at the canonical
     * node: a node that is both executable and a redirect is a valid Brigadier tree to build, but
     * the vanilla client rejects it when synced, disconnecting with "Server sent an impossible
     * command tree". {@code refill} has no subcommands to redirect into anyway, so a plain
     * {@code .executes(...)} of the same {@link Command} instance is sufficient — {@code /refill}
     * and {@code /rf} behave identically to {@code /bestesttool refill} with no shared tree node.
     */
    public static LiteralCommandNode<CommandSourceStack> buildRefillAlias(
            Main main, LiteralCommandNode<CommandSourceStack> bestToolsRoot) {
        var canonicalRefill = bestToolsRoot.getChild("refill");
        return Commands.literal("refill")
                .executes(canonicalRefill.getCommand())
                .build();
    }

    // -------------------------------------------------------------------------
    // /bestesttool hotbaronly
    // -------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> buildToggleHotbarOnly(Main main) {
        return Commands.literal("hotbaronly")
                .executes(ctx -> {
                    Player player = requirePlayer(ctx);
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
    private static LiteralArgumentBuilder<CommandSourceStack> buildFavoriteSlot(Main main) {
        return Commands.literal("favoriteslot")
                .executes(ctx -> {
                    Player player = requirePlayer(ctx);
                    if (player == null || !checkPermission(main, player, Permissions.PERM_USE)) {
                        return Command.SINGLE_SUCCESS;
                    }
                    main.commandBestTools.reportFavoriteSlot(player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("slot", IntegerArgumentType.integer(-1, 8))
                        .executes(ctx -> {
                            Player player = requirePlayer(ctx);
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

    private static LiteralArgumentBuilder<CommandSourceStack> buildRefill(Main main) {
        return Commands.literal("refill")
                .executes(ctx -> {
                    Player player = requirePlayer(ctx);
                    if (player == null || !checkPermission(main, player, Permissions.PERM_REFILL)) {
                        return Command.SINGLE_SUCCESS;
                    }
                    main.commandRefill.toggleRefill(player);
                    return Command.SINGLE_SUCCESS;
                })
                .then(boolStateArg(main, Permissions.PERM_REFILL,
                        (player, state) -> main.commandRefill.setRefill(player, state)));
    }

    // -------------------------------------------------------------------------
    // /bestesttool reload | debug | performance — admin-only, hidden from tab completion
    // for senders lacking the node (via .requires), rather than answered with noPermission.
    // -------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> buildReload(Main main) {
        return Commands.literal("reload")
                .requires(src -> Permissions.isAllowedTo(src.getSender(), Permissions.PERM_RELOAD))
                .executes(ctx -> {
                    CommandReload.reload(ctx.getSource().getSender(), main);
                    return Command.SINGLE_SUCCESS;
                });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildDebug(Main main) {
        return Commands.literal("debug")
                .requires(src -> Permissions.isAllowedTo(src.getSender(), Permissions.PERM_DEBUG))
                .executes(ctx -> {
                    CommandDebug.debug(ctx.getSource().getSender(), main, "debug");
                    return Command.SINGLE_SUCCESS;
                })
                .then(debugStateArg(main, "debug"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildPerformance(Main main) {
        return Commands.literal("performance")
                .requires(src -> Permissions.isAllowedTo(src.getSender(), Permissions.PERM_DEBUG))
                .executes(ctx -> {
                    CommandDebug.debug(ctx.getSource().getSender(), main, "performance");
                    return Command.SINGLE_SUCCESS;
                })
                .then(debugStateArg(main, "performance"));
    }

    /** A {@code <state>} child for {@code debug}/{@code performance}, matching their own sender type (no player requirement). */
    private static RequiredArgumentBuilder<CommandSourceStack, String> debugStateArg(Main main, String arg) {
        return Commands.argument("state", StringArgumentType.word())
                .suggests((ctx, b) -> suggestYesNo(b))
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    String raw = StringArgumentType.getString(ctx, "state");
                    Boolean state = parseState(raw);
                    if (state == null) {
                        sendInvalidValue(sender, raw);
                        return Command.SINGLE_SUCCESS;
                    }
                    CommandDebug.debug(sender, main, arg, state);
                    return Command.SINGLE_SUCCESS;
                });
    }

    // -------------------------------------------------------------------------
    // /bestesttool blacklist
    // -------------------------------------------------------------------------

    private static LiteralArgumentBuilder<CommandSourceStack> buildBlacklist(Main main) {
        return Commands.literal("blacklist")
                .executes(ctx -> runBlacklistShow(main, ctx))
                .then(Commands.literal("show").executes(ctx -> runBlacklistShow(main, ctx)))
                .then(buildBlacklistAddRemove(main, "add", true))
                .then(buildBlacklistAddRemove(main, "remove", false))
                .then(Commands.literal("reset")
                        .executes(ctx -> {
                            Player player = requirePlayer(ctx);
                            if (player == null || !checkPermission(main, player, Permissions.PERM_USE)) {
                                return Command.SINGLE_SUCCESS;
                            }
                            main.commandBlacklist.reset(player);
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static int runBlacklistShow(Main main, CommandContext<CommandSourceStack> ctx) {
        Player player = requirePlayer(ctx);
        if (player == null || !checkPermission(main, player, Permissions.PERM_USE)) {
            return Command.SINGLE_SUCCESS;
        }
        main.commandBlacklist.show(player);
        return Command.SINGLE_SUCCESS;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildBlacklistAddRemove(
            Main main, String literal, boolean add) {
        return Commands.literal(literal)
                .executes(ctx -> {
                    Player player = requirePlayer(ctx);
                    if (player == null || !checkPermission(main, player, Permissions.PERM_USE)) {
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
                            Player player = requirePlayer(ctx);
                            if (player == null || !checkPermission(main, player, Permissions.PERM_USE)) {
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
            Main main, CommandContext<CommandSourceStack> ctx, boolean add, boolean hotbarOnly) {
        Player player = requirePlayer(ctx);
        if (player == null || !checkPermission(main, player, Permissions.PERM_USE)) {
            return Command.SINGLE_SUCCESS;
        }
        main.commandBlacklist.addOrRemoveFromInventory(player, add, hotbarOnly);
        return Command.SINGLE_SUCCESS;
    }

    /** The sender's currently-blacklisted material names, lower-cased; empty for a non-player. */
    static List<String> blacklistedMaterialNames(Main main, CommandSender sender) {
        return sender instanceof Player player
                ? main.getPlayerSetting(player).getBlacklist().toStringList().stream()
                        .map(s -> s.toLowerCase(Locale.ROOT))
                        .toList()
                : List.of();
    }

    /** Suggests the currently-blacklisted materials for {@code blacklist remove}. */
    static CompletableFuture<Suggestions> suggestBlacklistedMaterialToken(
            Main main, CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
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
    private static void sendInvalidValue(CommandSender sender, String raw) {
        MessageUtil.send(sender, "invalidValue",
                Placeholder.unparsed("value", raw),
                Placeholder.unparsed("valid", BOOLEAN_VALUES));
    }

    /**
     * A {@code <state>} child accepting yes/no (and the other {@link #ON_WORDS}/{@link #OFF_WORDS})
     * for a player-facing boolean toggle, gated the same way as its parent literal's bare toggle:
     * an explicit {@code node} permission check with a {@code noPermission} message on denial.
     */
    private static RequiredArgumentBuilder<CommandSourceStack, String> boolStateArg(
            Main main, String permissionNode, BiConsumer<Player, Boolean> apply) {
        return Commands.argument("state", StringArgumentType.word())
                .suggests((ctx, b) -> suggestYesNo(b))
                .executes(ctx -> {
                    Player player = requirePlayer(ctx);
                    if (player == null || !checkPermission(main, player, permissionNode)) {
                        return Command.SINGLE_SUCCESS;
                    }
                    String raw = StringArgumentType.getString(ctx, "state");
                    Boolean state = parseState(raw);
                    if (state == null) {
                        sendInvalidValue(player, raw);
                        return Command.SINGLE_SUCCESS;
                    }
                    apply.accept(player, state);
                    return Command.SINGLE_SUCCESS;
                });
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    /** Sends {@code noPermission} and returns {@code false} unless {@code sender} has {@code node}. */
    private static boolean checkPermission(Main main, CommandSender sender, String node) {
        if (Permissions.isAllowedTo(sender, node)) return true;
        MessageUtil.send(sender, "noPermission", Placeholder.unparsed("plugin", main.getName()));
        return false;
    }

    /** Returns the sender as a {@link Player}, or sends {@code notAPlayer} and returns {@code null}. */
    @Nullable
    private static Player requirePlayer(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) return player;
        MessageUtil.send(sender, "notAPlayer");
        return null;
    }
}
