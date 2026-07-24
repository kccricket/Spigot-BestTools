package net.kccricket.bestesttool;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * A {@link Command} that delegates to a plain {@link CommandExecutor} (and, if it implements
 * one, a {@link TabCompleter}). Needed because {@code paper-plugin.yml} cannot declare a
 * {@code commands:} block, so commands are registered directly against the server's command map
 * (see {@link Main#load}) instead of via {@code getCommand(name).setExecutor(...)}.
 */
class DelegatingCommand extends Command {

    private final CommandExecutor executor;

    DelegatingCommand(String name, CommandExecutor executor, String description, String usage, List<String> aliases) {
        super(name, description, usage, aliases);
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        return executor.onCommand(sender, this, commandLabel, args);
    }

    @Override
    public @Nullable List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args) {
        if (executor instanceof TabCompleter tabCompleter) {
            List<String> completions = tabCompleter.onTabComplete(sender, this, alias, args);
            if (completions != null) return completions;
        }
        return super.tabComplete(sender, alias, args);
    }
}
