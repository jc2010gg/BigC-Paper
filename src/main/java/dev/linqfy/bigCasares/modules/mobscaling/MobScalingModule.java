package dev.linqfy.bigCasares.modules.mobscaling;

import dev.linqfy.bigCasares.module.PluginModule;
import dev.linqfy.bigCasares.module.runtime.BukkitRuntimeRegistrations;
import dev.linqfy.bigCasares.module.runtime.RuntimeRegistrationScope;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class MobScalingModule implements PluginModule {

    private static final String MODULE_ID = "mob-scaling";

    private final JavaPlugin plugin;
    private MobScalingSettings settings;
    private RuntimeRegistrationScope compatibilityScope;
    private MobScalingListener listener;

    public MobScalingModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return MODULE_ID;
    }

    @Override
    public void onEnable() {
        RuntimeRegistrationScope scope = new RuntimeRegistrationScope();
        this.compatibilityScope = scope;
        onEnable(scope);
    }

    @Override
    public void onEnable(RuntimeRegistrationScope scope) {
        this.settings = MobScalingSettings.fromConfig(plugin.getConfig());
        
        if (!settings.enabled()) {
            plugin.getLogger().info("[MobScalingModule] Disabled in config.");
            return;
        }

        BukkitRuntimeRegistrations registrations = new BukkitRuntimeRegistrations(plugin, scope);

        SpecialEnemies specialEnemies = new SpecialEnemies(plugin, SpecialEnemySettings.fromConfig(plugin.getConfig()));
        registrations.registerListener("mob-scaling-special-enemies", specialEnemies);
        registrations.scheduleRepeating("mob-scaling-special-enemies-tick", specialEnemies::tick,
                SpecialEnemies.TASK_PERIOD_TICKS, SpecialEnemies.TASK_PERIOD_TICKS);
        registrations.ownCleanup("mob-scaling-special-enemies-state", specialEnemies::clear);

        this.listener = new MobScalingListener(plugin, settings, new MobScalingMutations(plugin, specialEnemies));
        registrations.registerListener("mob-scaling-listener", listener);
        registrations.registerListener("mob-scaling-mutations", listener.getMutations());

        registerCommand(registrations);

        plugin.getLogger().info("[MobScalingModule] Enabled. Start date: " + settings.startDate());
    }

    @Override
    public void onDisable() {
        if (compatibilityScope != null) {
            compatibilityScope.close();
            compatibilityScope = null;
        }
        plugin.getLogger().info("[MobScalingModule] Disabled.");
    }

    private void registerCommand(BukkitRuntimeRegistrations registrations) {
        org.bukkit.command.PluginCommand cmd = plugin.getCommand("mobscaling");
        if (cmd == null) return;

        registrations.bindCommand("mobscaling-command", cmd, (CommandSender sender, Command command, String label, String[] args) -> {
            if (args.length < 1 || !args[0].equalsIgnoreCase("factor")) {
                sender.sendMessage("§cUsage: /mobscaling factor");
                return true;
            }

            if (!sender.hasPermission("bigcasares.mobscaling.admin")) {
                sender.sendMessage("§cNo tienes permiso.");
                return true;
            }

            long days = ChronoUnit.DAYS.between(settings.startDate(), LocalDate.now());
            if (days < 0) days = 0;

            double multiplier = settings.multiplier(days);
            double chance = Math.min(settings.maxMutationChance(), settings.baseMutationChance() + (settings.mutationChancePerDay() * days));

            sender.sendMessage("§6--- Mob Scaling Info ---");
            sender.sendMessage("§eDays since start: §f" + days);
            sender.sendMessage("§eStat Multiplier: §f" + String.format("%.2fx", multiplier));
            sender.sendMessage("§eMutation Chance: §f" + String.format("%.1f%%", chance * 100));

            return true;
        }, null);
    }
}
