package me.server.torturewatcher;

import fr.xephi.authme.events.LoginEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class TortureWatcher extends JavaPlugin implements Listener {

    private final Set<String> defaultTargets = new HashSet<>(Arrays.asList("T0g00d", "__Mrmeme__"));
    private final Map<String, Boolean> targetStates = new HashMap<>();
    private final Set<String> wipedOnce = new HashSet<>();

    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig(); // Standard way to handle config

        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        loadData();

        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("TortureWatcher enabled.");
    }

    private void loadData() {
        // Load target states
        if (dataConfig.getConfigurationSection("targets") != null) {
            for (String key : dataConfig.getConfigurationSection("targets").getKeys(false)) {
                targetStates.put(key, dataConfig.getBoolean("targets." + key));
            }
        }
        // Ensure defaults exist in the map if not explicitly set to false
        for (String def : defaultTargets) {
            targetStates.putIfAbsent(def, true);
        }

        // Load wiped status
        wipedOnce.addAll(dataConfig.getStringList("wiped-players"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("torture.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length != 2) return false;

        String targetName = args[0];
        boolean state = Boolean.parseBoolean(args[1]);

        targetStates.put(targetName, state);
        dataConfig.set("targets." + targetName, state);
        saveData();

        sender.sendMessage("§aTorture for " + targetName + " is now " + (state ? "ON" : "OFF"));
        return true;
    }

    @EventHandler
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();

        if (targetStates.getOrDefault(name, false)) {
            // Log location
            Location loc = player.getLocation();
            getLogger().warning(String.format("[WATCH] %s at %s %d %d %d",
                    name, loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));

            // Handle Wipe
            if (defaultTargets.contains(name) && !wipedOnce.contains(name)) {
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
                wipedOnce.add(name);
                List<String> wipedList = new ArrayList<>(wipedOnce);
                dataConfig.set("wiped-players", wipedList);
                saveData();
            }

            applyEffects(player);
        }
    }

    private void applyEffects(Player player) {
        // 100,000 seconds is plenty; amp 255 is max
        List<PotionEffect> effects = List.of(
                new PotionEffect(PotionEffectType.SLOWNESS, 200000, 255, false, false),
                new PotionEffect(PotionEffectType.WEAKNESS, 200000, 255, false, false),
                new PotionEffect(PotionEffectType.BLINDNESS, 200000, 255, false, false),
                new PotionEffect(PotionEffectType.HUNGER, 200000, 255, false, false)
        );
        player.addPotionEffects(effects);
    }

    private void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}