package me.server.torturewatcher;

import fr.xephi.authme.events.LoginEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public final class Torture extends JavaPlugin implements Listener, TabCompleter {

    // Hardcoded permanent targets
    private final Set<String> defaultTargets = new HashSet<>(Arrays.asList("T0g00d", "__Mrmeme__"));

    private final Map<String, Boolean> targetStates = new HashMap<>();

    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // 1. Setup Data File
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Could not create data.yml!");
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        // 2. Load Data
        loadData();

        // 3. Register logic
        Bukkit.getPluginManager().registerEvents(this, this);

        if (getCommand("torture") != null) {
            getCommand("torture").setExecutor(this);
            getCommand("torture").setTabCompleter(this);
        }

        getLogger().info("Torture plugin active.");
    }

    private void loadData() {
        // Load current states from data.yml
        if (dataConfig.getConfigurationSection("targets") != null) {
            for (String key : dataConfig.getConfigurationSection("targets").getKeys(false)) {
                targetStates.put(key, dataConfig.getBoolean("targets." + key));
            }
        }

        // Ensure hardcoded defaults are enabled if not explicitly turned off in the file
        for (String def : defaultTargets) {
            targetStates.putIfAbsent(def, true);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("torture.admin")) {
            sender.sendMessage("§cYou do not have permission to execute this command.");
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage("§cUsage: /torture <player> <true|false>");
            return true;
        }

        String targetName = args[0];
        boolean state = Boolean.parseBoolean(args[1]);

        // 1. Save State Immediately
        targetStates.put(targetName, state);
        dataConfig.set("targets." + targetName, state);
        saveData();

        // 2. Handle Online vs Offline Logic
        Player onlineTarget = Bukkit.getPlayer(targetName);

        if (onlineTarget == null) {
            // Target is OFFLINE
            sender.sendMessage("§cTarget not online but effect reserved upon target joining.");
        } else {
            // Target is ONLINE
            if (state) {
                applyEffects(onlineTarget);
                wipeInventory(onlineTarget); // Wipe happens immediately on command
            } else {
                for (PotionEffect effect : onlineTarget.getActivePotionEffects()) {
                    onlineTarget.removePotionEffect(effect.getType());
                }
            }

            // Sound Feedback
            if (sender instanceof Player) {
                ((Player) sender).playSound(((Player) sender).getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1f, 0.5f);
            }

            // Context-Aware Message
            String action = state ? "punished" : "released";
            String color = state ? "§a" : "§7"; // Green for ON, Gray for OFF
            sender.sendMessage("§4§l[Torture] §cPlayer " + action + ": §f" + targetName + " " + color + "(" + (state ? "ON" : "OFF") + ")");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            return Arrays.asList("true", "false");
        }
        return Collections.emptyList();
    }

    @EventHandler
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();

        if (targetStates.getOrDefault(name, false)) {
            Location loc = player.getLocation();
            getLogger().warning(String.format("[WATCH] %s logged in at %s (x:%d y:%d z:%d)",
                    name, loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));

            // Always wipe inventory on login if they are being tortured
            wipeInventory(player);
            getLogger().info("[WATCH] Inventory wiped for target: " + name);

            // Apply Torture (Delayed slightly to ensure AuthMe processing is done)
            Bukkit.getScheduler().runTaskLater(this, () -> applyEffects(player), 10L);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (targetStates.getOrDefault(event.getPlayer().getName(), false)) {
            // Re-apply effects (and wipe again just to be sure they didn't pick up death drops)
            Bukkit.getScheduler().runTaskLater(this, () -> {
                wipeInventory(event.getPlayer());
                applyEffects(event.getPlayer());
            }, 20L);
        }
    }

    private void wipeInventory(Player player) {
        if (player.getInventory() != null) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);
        }
    }

    private void applyEffects(Player player) {
        int duration = 200000; // Effectively infinite

        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 4, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, duration, 4, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, duration, 1, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, duration, 2, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, duration, 2, false, false));
    }

    private void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().severe("Failed to save data.yml!");
            e.printStackTrace();
        }
    }
}