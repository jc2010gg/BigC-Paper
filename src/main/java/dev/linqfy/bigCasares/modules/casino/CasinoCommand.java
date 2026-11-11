package dev.linqfy.bigCasares.modules.casino;

import dev.linqfy.bigCasares.modules.casino.bar.DrinkType;
import dev.linqfy.bigCasares.modules.casino.decor.ManagementSign;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class CasinoCommand implements TabExecutor {

    static final String ADMIN = "bigcasares.casino.admin";
    private static final List<String> PLAYER_ACTIONS = List.of("deuda", "jackpot");
    private static final List<String> ADMIN_ACTIONS = List.of("construir", "colocar", "quitar", "lista", "trago");

    private final CasinoRuntime runtime;

    CasinoCommand(CasinoRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "deuda" -> debt(sender);
            case "jackpot" -> sender.sendMessage("§6[Casino] §fJackpot acumulado: §e"
                + runtime.context().chips(runtime.jackpot().value()));
            case "construir" -> {
                if (admin(sender) && sender instanceof Player player) {
                    build(player, args);
                }
            }
            case "colocar" -> {
                if (admin(sender) && sender instanceof Player player) {
                    place(player, label, args);
                }
            }
            case "quitar" -> {
                if (admin(sender) && sender instanceof Player player) {
                    double radius = args.length > 1 ? parseDouble(args[1], 4) : 4;
                    Optional<Installation> removed = runtime.removeNearest(player.getLocation(), radius);
                    sender.sendMessage(removed.map(installation -> "§aQuitado: " + CasinoRuntime.describe(installation))
                        .orElse("§cNo hay nada del casino a menos de " + radius + " bloques."));
                }
            }
            case "lista" -> {
                if (admin(sender)) {
                    List<Installation> installations = runtime.installations();
                    sender.sendMessage("§6[Casino] §f" + installations.size() + " instalaciones:");
                    installations.forEach(installation -> sender.sendMessage(" §8- §f" + CasinoRuntime.describe(installation)));
                }
            }
            case "trago" -> {
                if (admin(sender)) {
                    giveDrink(sender, label, args);
                }
            }
            default -> help(sender, label);
        }
        return true;
    }

    private void build(Player player, String[] args) {
        if (runtime.building()) {
            player.sendMessage("§cYa se está construyendo un casino.");
            return;
        }
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirmar")) {
            player.sendMessage("§6[Casino] §fEsto construye §eEl Gran Casares§f (" + (CasinoBlueprint.HALF_WIDTH * 2 + 3)
                + "x" + (CasinoBlueprint.DEPTH + 3) + ", " + (CasinoBlueprint.HEIGHT + 2) + " de alto) delante tuyo,");
            player.sendMessage("§fmirando hacia donde mirás, y §creemplaza todos los bloques§f de esa zona.");
            player.sendMessage("§7Parate en la futura puerta y usá §e/casino construir confirmar§7.");
            return;
        }
        int blocks = runtime.build(player.getLocation());
        player.sendMessage("§6[Casino] §aConstruyendo " + blocks + " bloques... las mesas abren al terminar.");
    }

    private void place(Player player, String label, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§eUso: /" + label + " colocar <" + String.join("|",
                Arrays.stream(AttractionType.values()).map(AttractionType::id).toList()) + "> [variante]");
            return;
        }
        Optional<AttractionType> type = AttractionType.byId(args[1]);
        if (type.isEmpty()) {
            player.sendMessage("§cTipo desconocido: " + args[1]);
            return;
        }
        String variant = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "";
        Location at = player.getLocation();
        if (type.get() == AttractionType.SIGN) {
            at = player.getEyeLocation();
        }
        Installation installation = runtime.place(type.get(), at, variant);
        player.sendMessage("§aColocado: " + CasinoRuntime.describe(installation)
            + " §7(mira hacia donde mirabas; /casino quitar para sacarlo)");
    }

    private void giveDrink(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eUso: /" + label + " trago <" + String.join("|",
                Arrays.stream(DrinkType.values()).map(DrinkType::id).toList()) + "> [jugador]");
            return;
        }
        Optional<DrinkType> type = DrinkType.byId(args[1]);
        Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player self ? self : null;
        if (type.isEmpty() || target == null) {
            sender.sendMessage("§cTrago o jugador inválido.");
            return;
        }
        target.getInventory().addItem(runtime.drinks().create(type.get()));
        sender.sendMessage("§aLe diste " + type.get().displayName() + " §aa " + target.getName() + ".");
    }

    private void debt(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo jugadores.");
            return;
        }
        var loans = runtime.loans();
        if (loans.loan(player.getUniqueId()).isEmpty()) {
            player.sendMessage("§5[Don Casares] §7No me debés nada. Por ahora.");
            return;
        }
        long left = loans.loan(player.getUniqueId()).get().dueAtMillis() - System.currentTimeMillis();
        player.sendMessage("§5[Don Casares] §fDebés §c" + runtime.context().chips(loans.balance(player.getUniqueId()))
            + (left > 0 ? " §7· vence en §f" + CasinoFormat.duration(left) : " §4§l· VENCIDA"));
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage("§6§l[Gran Casares] §fBlackjack, póker, ruleta, tragamonedas, riñas, bar y empeños.");
        sender.sendMessage("§e/" + label + " deuda §7· cuánto le debés a Don Casares");
        sender.sendMessage("§e/" + label + " jackpot §7· jackpot acumulado de las tragamonedas");
        if (sender.hasPermission(ADMIN)) {
            sender.sendMessage("§e/" + label + " construir [confirmar] §7· arma el casino entero delante tuyo");
            sender.sendMessage("§e/" + label + " colocar <tipo> [variante] §7· pone una atracción donde estás");
            sender.sendMessage("§e/" + label + " quitar [radio] §7· saca la atracción más cercana");
            sender.sendMessage("§e/" + label + " lista §7· /" + label + " trago <tipo> [jugador]");
            sender.sendMessage("§7Carteles: variante 0-" + (ManagementSign.TEXTS.size() - 1) + " o texto con & y \\n");
        }
    }

    private static boolean admin(CommandSender sender) {
        if (!sender.hasPermission(ADMIN)) {
            sender.sendMessage("§cNo tenés permiso.");
            return false;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cTenés que estar en el juego.");
        }
        return true;
    }

    private static double parseDouble(String raw, double fallback) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException invalid) {
            return fallback;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(PLAYER_ACTIONS);
            if (sender.hasPermission(ADMIN)) {
                options.addAll(ADMIN_ACTIONS);
            }
        } else if (args.length == 2 && sender.hasPermission(ADMIN)) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "colocar" -> Arrays.stream(AttractionType.values()).map(AttractionType::id).forEach(options::add);
                case "trago" -> Arrays.stream(DrinkType.values()).map(DrinkType::id).forEach(options::add);
                case "construir" -> options.add("confirmar");
                default -> {
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("trago") && sender.hasPermission(ADMIN)) {
            Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
