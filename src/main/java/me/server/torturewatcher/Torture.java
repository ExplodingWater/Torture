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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;
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
        targetStates.clear(); // Clear map to prevent duplicates on reload

        // Load from file
        if (dataConfig.getConfigurationSection("targets") != null) {
            for (String key : dataConfig.getConfigurationSection("targets").getKeys(false)) {
                targetStates.put(key, dataConfig.getBoolean("targets." + key));
            }
        }

        // Ensure defaults
        for (String def : defaultTargets) {
            targetStates.putIfAbsent(def, true);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // STRICT OP CHECK
        if (!sender.isOp()) {
            sender.sendMessage("§cYou must be a server Operator (OP) to use this.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            dataConfig = YamlConfiguration.loadConfiguration(dataFile);
            loadData();
            sender.sendMessage("§a[Torture] Configuration and Data reloaded.");
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage("§cUsage: /torture <player> <true|false> OR /torture reload");
            return true;
        }

        String targetName = args[0];
        boolean state = Boolean.parseBoolean(args[1]);

        // Safety: Prevent torturing someone with bypass (unless it's a default hardcoded target)
        Player onlineTarget = Bukkit.getPlayer(targetName);
        if (onlineTarget != null && onlineTarget.hasPermission("torture.bypass") && !defaultTargets.contains(targetName) && state) {
            sender.sendMessage("§cError: You cannot torture this player (they have bypass permission).");
            return true;
        }

        // 1. Save State
        targetStates.put(targetName, state);
        dataConfig.set("targets." + targetName, state);
        saveData();

        // 2. Handle Online vs Offline Logic
        if (onlineTarget == null) {
            sender.sendMessage("§cTarget not online but effect reserved upon target joining.");
        } else {
            if (state) {
                applyEffects(onlineTarget);
                wipeInventory(onlineTarget);
            } else {
                for (PotionEffect effect : onlineTarget.getActivePotionEffects()) {
                    onlineTarget.removePotionEffect(effect.getType());
                }
            }

            if (sender instanceof Player) {
                ((Player) sender).playSound(((Player) sender).getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 1f, 0.5f);
            }

            String action = state ? "punished" : "released";
            String color = state ? "§a" : "§7";
            sender.sendMessage("§4§l[Torture] §cPlayer " + action + ": §f" + targetName + " " + color + "(" + (state ? "ON" : "OFF") + ")");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Hide auto-complete from non-OPs
        if (!sender.isOp()) return Collections.emptyList();

        if (args.length == 1) {
            List<String> completions = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList());
            completions.add("reload");
            return completions.stream()
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("reload")) {
            return Arrays.asList("true", "false");
        }
        return Collections.emptyList();
    }

    // --- EVENTS ---

    @EventHandler
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();

        if (targetStates.getOrDefault(name, false)) {
            Location loc = player.getLocation();
            getLogger().warning(String.format("[WATCH] %s logged in at %s (x:%d y:%d z:%d)",
                    name, loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));

            wipeInventory(player);
            getLogger().info("[WATCH] Inventory wiped for target: " + name);

            // Delay to ensure AuthMe is done
            Bukkit.getScheduler().runTaskLater(this, () -> applyEffects(player), 10L);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (targetStates.getOrDefault(event.getPlayer().getName(), false)) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                wipeInventory(event.getPlayer());
                applyEffects(event.getPlayer());
            }, 20L);
        }
    }

    // --- LOCKDOWN EVENTS (Prevent doing anything) ---

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (targetStates.getOrDefault(event.getPlayer().getName(), false)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c§l✖ §7You are detained.");
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (targetStates.getOrDefault(event.getPlayer().getName(), false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        if (targetStates.getOrDefault(event.getPlayer().getName(), false)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c§l✖ §7No speaking.");
        }
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (targetStates.getOrDefault(event.getPlayer().getName(), false)) {
            // Allow them to use /msg only, block everything else
            String msg = event.getMessage().toLowerCase();
            if (!msg.startsWith("/msg") && !msg.startsWith("/tell") && !msg.startsWith("/r")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§c§l✖ §7You cannot use commands.");
            }
        }
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        if (targetStates.getOrDefault(event.getPlayer().getName(), false)) {
            event.setCancelled(true); // Prevent them from dropping items to save them
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (targetStates.getOrDefault(event.getPlayer().getName(), false)) {
            event.setCancelled(true); // Prevents opening chests, pushing buttons, etc.
        }
    }

    // --- HELPERS ---

    private void wipeInventory(Player player) {
        if (player.getInventory() != null) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);
        }
    }

    private void applyEffects(Player player) {
        int duration = 200000;

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