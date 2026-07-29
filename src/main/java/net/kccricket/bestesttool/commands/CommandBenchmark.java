package net.kccricket.bestesttool.commands;

import net.kccricket.bestesttool.Main;
import org.bukkit.command.CommandSender;
import net.kccricket.bestesttool.benchmark.BenchmarkWorkload;

/**
 * Plain action methods for {@code /bestesttool benchmark}. Permission and {@code enable_benchmark}
 * config gating live in the Brigadier tree built by {@link BestToolsCommands} — this class only
 * performs the action once a call site has already established the sender is authorized. Unlike
 * {@link CommandSelfTest}, these methods take a plain {@link CommandSender} rather than a
 * {@link org.bukkit.entity.Player}: the benchmark workload is entirely synthetic (see
 * {@link BenchmarkWorkload}) and never touches a real player's inventory or the world, so it works
 * from console as well as in-game.
 */
public class CommandBenchmark {

    private final Main main;

    public CommandBenchmark(Main main) {
        this.main = main;
    }

    void start(CommandSender sender, BenchmarkWorkload.KitSize kitSize) {
        main.benchmarkManager.start(sender, kitSize);
    }

    void stop(CommandSender sender) {
        main.benchmarkManager.stop(sender);
    }

    void status(CommandSender sender) {
        main.benchmarkManager.status(sender);
    }
}
