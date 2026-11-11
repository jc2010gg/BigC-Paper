package dev.linqfy.bigCasares.modules.schizophrenia;

import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class SchizophreniaCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("start", "stop", "status", "give", "provocar");

    private final Server server;
    private final SchizophreniaRuntime runtime;
    private final LobotomizerItem lobotomizer;

    SchizophreniaCommand(Server server, SchizophreniaRuntime runtime, LobotomizerItem lobotomizer) {
        this.server = server;
        this.runtime = runtime;
        this.lobotomizer = lobotomizer;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("bigcasares.schizophrenia.admin")) {
            sender.sendMessage("§cNo tenés permiso.");
            return true;
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> runtime.statusLines().forEach(sender::sendMessage);
            case "start" -> target(sender, args, 1).ifPresent(player -> {
                int intensity = args.length >= 3 ? parseInt(args[2], 1) : 1;
                sender.sendMessage(runtime.start(player, intensity)
                    ? "§5" + player.getName() + " empezó a escuchar cosas (intensidad " + EpisodeMath.clampIntensity(intensity) + ")."
                    : "§c" + player.getName() + " ya está en pleno episodio.");
            });
            case "stop" -> target(sender, args, 1).ifPresent(player -> {
                if (runtime.isAffected(player)) {
                    runtime.end(player, false);
                    sender.sendMessage("§a" + player.getName() + " ya no escucha voces.");
                } else {
                    sender.sendMessage("§7" + player.getName() + " no tiene un episodio activo.");
                }
            });
            case "give" -> target(sender, args, 1).ifPresent(player -> {
                player.getInventory().addItem(lobotomizer.create())
                    .values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
                sender.sendMessage("§aLe di un Lobotomizador a " + player.getName() + ".");
            });
            case "provocar" -> provoke(sender, args, label);
            default -> sender.sendMessage("§eUso: /" + label
                + " <start <jugador> [1-5]|stop <jugador>|status|give [jugador]|provocar <jugador> [alucinación]>");
        }
        return true;
    }

    /** Without a hallucination: skip the quiet part and start the dread. With one: play it right now. */
    private void provoke(CommandSender sender, String[] args, String label) {
        Optional<Player> target = target(sender, args, 1);
        if (target.isEmpty()) {
            return;
        }
        Player player = target.get();
        runtime.start(player, 1);
        if (args.length < 3) {
            runtime.provoke(player);
            sender.sendMessage("§5" + player.getName() + " siente que algo se acerca.");
            return;
        }
        Optional<Hallucination> hallucination = Hallucination.parse(args[2]);
        if (hallucination.isEmpty()) {
            sender.sendMessage("§cAlucinación desconocida: " + args[2]);
            return;
        }
        sender.sendMessage(runtime.trigger(player, hallucination.get())
            ? "§5" + player.getName() + " vio algo: " + hallucination.get().id()
            : "§7No hubo lugar para " + hallucination.get().id() + " cerca de " + player.getName() + ".");
    }

    private Optional<Player> target(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            Player player = server.getPlayerExact(args[index]);
            if (player == null) {
                sender.sendMessage("§cNo encontré al jugador " + args[index] + ".");
            }
            return Optional.ofNullable(player);
        }
        if (sender instanceof Player player) {
            return Optional.of(player);
        }
        sender.sendMessage("§cDesde la consola tenés que poner el jugador.");
        return Optional.empty();
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("status")) {
            return filter(server.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("provocar")) {
            return filter(Arrays.stream(Hallucination.values()).map(Hallucination::id).toList(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("start")) {
            return filter(List.of("1", "2", "3", "4", "5"), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
