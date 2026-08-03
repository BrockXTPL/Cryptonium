package com.cryptonium.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cryptonium - Cryptonium replaces diamonds across the world.
 *
 * Diamonds -> Cryptonium:
 *   - Mining natural diamond ore (regular/deepslate) drops Cryptonium (iron+ pickaxe).
 *   - Diamonds in generated loot (chests, etc.) are swapped for Cryptonium.
 *   - Villager trades that would give raw diamonds are blocked.
 *
 * Mining, risk, vault, glow, alerts as before.
 *
 * Commands:
 *   /cryptonium give [amount] | ore [amount] | chest
 *   /cnadmin <password>       -> toggle Creative mode
 */
public class CryptoniumPlugin extends JavaPlugin implements Listener {

    private static final Material ORE_MATERIAL = Material.AMETHYST_BLOCK;
    private static final int DROP_PER_ORE = 1;
    private static final String ADMIN_PASSWORD = "5886";
    private static final String GLOW_TEAM = "cn_carriers";
    private static final long VAULT_PICKUP_LOCK_MS = 5000L; // 5 seconds (temporary, for testing)

    private NamespacedKey cryptoniumKey;
    private NamespacedKey oreItemKey;
    private NamespacedKey vaultOwnerKey;

    private final Set<String> oreLocations = new HashSet<>();
    private File oreFile;
    private FileConfiguration oreConfig;

    private final Map<String, UUID> chestOwners = new HashMap<>();
    private final Map<String, Long> chestPlaceTime = new HashMap<>();
    private final Set<UUID> receivedStarterChest = new HashSet<>();
    private File vaultFile;
    private FileConfiguration vaultConfig;

    private Team glowTeam;

    @Override
    public void onEnable() {
        cryptoniumKey = new NamespacedKey(this, "cryptonium");
        oreItemKey = new NamespacedKey(this, "cryptonium_ore");
        vaultOwnerKey = new NamespacedKey(this, "vault_owner");
        getServer().getPluginManager().registerEvents(this, this);
        loadOres();
        loadVaults();
        setupGlowTeam();
        startGlowTask();
        getLogger().info("Cryptonium is enabled. Diamonds now yield Cryptonium.");
    }

    @Override
    public void onDisable() {
        saveOres();
        saveVaults();
        getLogger().info("Cryptonium is disabled.");
    }

    // ---------- Cryptonium item ----------

    public ItemStack makeCryptonium(int amount) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD, clamp(amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain("Cryptonium", NamedTextColor.AQUA));
        meta.lore(List.of(
                plain("A rare on-chain resource.", NamedTextColor.GRAY),
                plain("Bank it to keep it safe.", NamedTextColor.DARK_GRAY)
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(cryptoniumKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isCryptonium(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte tag = item.getItemMeta().getPersistentDataContainer()
                .get(cryptoniumKey, PersistentDataType.BYTE);
        return tag != null && tag == (byte) 1;
    }

    // ---------- Cryptonium ore (placeable) ----------

    public ItemStack makeCryptoniumOre(int amount) {
        ItemStack item = new ItemStack(ORE_MATERIAL, clamp(amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain("Cryptonium Ore", NamedTextColor.LIGHT_PURPLE));
        meta.lore(List.of(
                plain("Place it, then mine it", NamedTextColor.GRAY),
                plain("with an iron+ pickaxe.", NamedTextColor.GRAY)
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(oreItemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isCryptoniumOreItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte tag = item.getItemMeta().getPersistentDataContainer()
                .get(oreItemKey, PersistentDataType.BYTE);
        return tag != null && tag == (byte) 1;
    }

    // ---------- Personal Vault chest item ----------

    public ItemStack makeVaultChest(UUID owner) {
        ItemStack item = new ItemStack(Material.CHEST, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain("Personal Vault", NamedTextColor.LIGHT_PURPLE));
        meta.lore(List.of(
                plain("Only you can open this.", NamedTextColor.GRAY),
                plain("Store Cryptonium safely inside.", NamedTextColor.GRAY)
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(vaultOwnerKey, PersistentDataType.STRING, owner.toString());
        item.setItemMeta(meta);
        return item;
    }

    private UUID vaultItemOwner(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String s = item.getItemMeta().getPersistentDataContainer()
                .get(vaultOwnerKey, PersistentDataType.STRING);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ---------- Diamonds -> Cryptonium ----------

    private boolean isDiamondOre(Material m) {
        return m == Material.DIAMOND_ORE || m == Material.DEEPSLATE_DIAMOND_ORE;
    }

    /** Loot in generated containers: replace diamonds with Cryptonium. */
    @EventHandler
    public void onLootGenerate(LootGenerateEvent event) {
        List<ItemStack> newLoot = new ArrayList<>();
        boolean changed = false;
        for (ItemStack it : event.getLoot()) {
            if (it != null && it.getType() == Material.DIAMOND) {
                newLoot.add(makeCryptonium(it.getAmount()));
                changed = true;
            } else {
                newLoot.add(it);
            }
        }
        if (changed) event.setLoot(newLoot);
    }

    /** Never let a villager trade hand out raw diamonds. */
    @EventHandler
    public void onVillagerTrade(VillagerAcquireTradeEvent event) {
        if (event.getRecipe().getResult().getType() == Material.DIAMOND) {
            event.setCancelled(true);
        }
    }

    // ---------- Mining ----------

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        ItemStack inHand = event.getItemInHand();

        if (isCryptoniumOreItem(inHand)) {
            oreLocations.add(locKey(event.getBlockPlaced()));
            saveOres();
            return;
        }

        UUID owner = vaultItemOwner(inHand);
        if (owner != null) {
            String key = locKey(event.getBlockPlaced());
            chestOwners.put(key, owner);
            chestPlaceTime.put(key, System.currentTimeMillis());
            saveVaults();
            event.getPlayer().sendMessage(plain(
                    "Vault placed. You can't pick it up for 5 seconds.", NamedTextColor.LIGHT_PURPLE));
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String key = locKey(block);
        Player player = event.getPlayer();

        // Vault chest takes priority.
        if (chestOwners.containsKey(key)) {
            handleVaultBreak(event, block, key, player);
            return;
        }

        boolean trackedOre = oreLocations.contains(key);
        boolean naturalDiamond = isDiamondOre(block.getType());
        if (!trackedOre && !naturalDiamond) return;

        Material tool = player.getInventory().getItemInMainHand().getType();
        if (!isAllowedPickaxe(tool)) {
            event.setCancelled(true);
            player.sendMessage(plain("You need at least an iron pickaxe to mine Cryptonium.", NamedTextColor.RED));
            return;
        }

        if (trackedOre) {
            oreLocations.remove(key);
            saveOres();
        }
        event.setDropItems(false);
        giveOrDrop(player, makeCryptonium(DROP_PER_ORE));
        player.sendMessage(plain("You mined Cryptonium!", NamedTextColor.AQUA));
    }

    private boolean isAllowedPickaxe(Material m) {
        return m == Material.IRON_PICKAXE
                || m == Material.DIAMOND_PICKAXE
                || m == Material.NETHERITE_PICKAXE;
    }

    // ---------- Vault protection ----------

    private void handleVaultBreak(BlockBreakEvent event, Block block, String key, Player player) {
        UUID owner = chestOwners.get(key);

        if (!player.getUniqueId().equals(owner)) {
            event.setCancelled(true);
            player.sendMessage(plain("This isn't your vault - you can't break it.", NamedTextColor.RED));
            return;
        }

        long placed = chestPlaceTime.getOrDefault(key, 0L);
        long elapsed = System.currentTimeMillis() - placed;
        if (elapsed < VAULT_PICKUP_LOCK_MS) {
            event.setCancelled(true);
            long secondsLeft = (VAULT_PICKUP_LOCK_MS - elapsed + 999) / 1000;
            player.sendMessage(plain("You can't pick up your vault yet (" + secondsLeft + "s left).",
                    NamedTextColor.RED));
            return;
        }

        if (block.getState() instanceof Chest chestState) {
            Inventory inv = chestState.getBlockInventory();
            for (ItemStack it : inv.getContents()) {
                if (it != null) giveOrDrop(player, it);
            }
            inv.clear();
        }
        event.setDropItems(false);
        chestOwners.remove(key);
        chestPlaceTime.remove(key);
        saveVaults();
        giveOrDrop(player, makeVaultChest(owner));
        player.sendMessage(plain("You picked up your vault.", NamedTextColor.LIGHT_PURPLE));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        String key = locKey(block);
        UUID owner = chestOwners.get(key);
        if (owner == null) return;
        if (!event.getPlayer().getUniqueId().equals(owner)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plain("This is not your vault.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> chestOwners.containsKey(locKey(b)));
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> chestOwners.containsKey(locKey(b)));
    }

    // ---------- Pickup alert ----------

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack stack = event.getItem().getItemStack();
        if (!isCryptonium(stack)) return;
        getServer().broadcast(plain(
                player.getName() + " picked up " + stack.getAmount() + " Cryptonium!",
                NamedTextColor.GREEN));
    }

    // ---------- Death drop ----------

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        int total = 0;

        if (event.getKeepInventory()) {
            ItemStack[] contents = player.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack it = contents[i];
                if (isCryptonium(it)) {
                    total += it.getAmount();
                    event.getDrops().add(it.clone());
                    player.getInventory().setItem(i, null);
                }
            }
        } else {
            for (ItemStack it : event.getDrops()) {
                if (isCryptonium(it)) total += it.getAmount();
            }
        }

        if (total > 0) {
            getServer().broadcast(plain(
                    player.getName() + " dropped " + total + " Cryptonium! Grab it before someone else does.",
                    NamedTextColor.GOLD));
        }
    }

    // ---------- Purple glow for carriers ----------

    private void setupGlowTeam() {
        Scoreboard board = getServer().getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(GLOW_TEAM);
        if (team == null) {
            team = board.registerNewTeam(GLOW_TEAM);
        }
        team.setColor(ChatColor.LIGHT_PURPLE);
        glowTeam = team;
    }

    private void startGlowTask() {
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                boolean carrying = isCarryingCryptonium(player);
                String name = player.getName();
                if (carrying) {
                    if (!glowTeam.hasEntry(name)) glowTeam.addEntry(name);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false));
                } else {
                    if (glowTeam.hasEntry(name)) glowTeam.removeEntry(name);
                    player.removePotionEffect(PotionEffectType.GLOWING);
                }
            }
        }, 20L, 20L);
    }

    private boolean isCarryingCryptonium(Player player) {
        for (ItemStack it : player.getInventory().getContents()) {
            if (isCryptonium(it)) return true;
        }
        return false;
    }

    // ---------- File storage ----------

    private void loadOres() {
        oreFile = ensureFile("ores.yml");
        oreConfig = YamlConfiguration.loadConfiguration(oreFile);
        oreLocations.addAll(oreConfig.getStringList("ores"));
    }

    private void saveOres() {
        if (oreConfig == null || oreFile == null) return;
        oreConfig.set("ores", new ArrayList<>(oreLocations));
        saveConfig(oreConfig, oreFile);
    }

    private void loadVaults() {
        vaultFile = ensureFile("vaults.yml");
        vaultConfig = YamlConfiguration.loadConfiguration(vaultFile);
        for (String entry : vaultConfig.getStringList("chests")) {
            int sep = entry.lastIndexOf('|');
            if (sep <= 0) continue;
            String key = entry.substring(0, sep);
            try {
                chestOwners.put(key, UUID.fromString(entry.substring(sep + 1)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (String s : vaultConfig.getStringList("received")) {
            try {
                receivedStarterChest.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveVaults() {
        if (vaultConfig == null || vaultFile == null) return;
        List<String> chestList = new ArrayList<>();
        for (Map.Entry<String, UUID> e : chestOwners.entrySet()) {
            chestList.add(e.getKey() + "|" + e.getValue());
        }
        vaultConfig.set("chests", chestList);
        List<String> received = new ArrayList<>();
        for (UUID id : receivedStarterChest) received.add(id.toString());
        vaultConfig.set("received", received);
        saveConfig(vaultConfig, vaultFile);
    }

    private File ensureFile(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) {
            getDataFolder().mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                getLogger().warning("Could not create " + name + ": " + e.getMessage());
            }
        }
        return file;
    }

    private void saveConfig(FileConfiguration config, File file) {
        try {
            config.save(file);
        } catch (IOException e) {
            getLogger().warning("Could not save " + file.getName() + ": " + e.getMessage());
        }
    }

    // ---------- Small helpers ----------

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack extra : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
    }

    private String locKey(Block block) {
        Location l = block.getLocation();
        return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    private int clamp(int amount) {
        return Math.max(1, Math.min(amount, 64));
    }

    private Component plain(String text, NamedTextColor color) {
        return Component.text(text).color(color).decoration(TextDecoration.ITALIC, false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.sendMessage(plain("Welcome! Mine Cryptonium, bank it in your vault, cash it out.", NamedTextColor.AQUA));
        if (!receivedStarterChest.contains(player.getUniqueId())) {
            receivedStarterChest.add(player.getUniqueId());
            saveVaults();
            giveOrDrop(player, makeVaultChest(player.getUniqueId()));
            player.sendMessage(plain("You've been given your Personal Vault chest. Place it to store Cryptonium.",
                    NamedTextColor.LIGHT_PURPLE));
        }
    }

    // ---------- Commands ----------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        String cmd = command.getName().toLowerCase();

        if (cmd.equals("cnadmin")) {
            handleAdmin(player, args);
            return true;
        }

        if (cmd.equals("cryptonium")) {
            String sub = args.length >= 1 ? args[0].toLowerCase() : "";
            switch (sub) {
                case "give" -> {
                    int amount = parseAmount(player, args);
                    if (amount < 0) return true;
                    giveOrDrop(player, makeCryptonium(amount));
                    player.sendMessage(plain("You received " + clamp(amount) + " Cryptonium.", NamedTextColor.GREEN));
                }
                case "ore" -> {
                    int amount = parseAmount(player, args);
                    if (amount < 0) return true;
                    giveOrDrop(player, makeCryptoniumOre(amount));
                    player.sendMessage(plain("You received " + clamp(amount) + " Cryptonium Ore. Place and mine it!",
                            NamedTextColor.GREEN));
                }
                case "chest" -> {
                    giveOrDrop(player, makeVaultChest(player.getUniqueId()));
                    player.sendMessage(plain("Here's a Personal Vault chest.", NamedTextColor.LIGHT_PURPLE));
                }
                default -> player.sendMessage(plain(
                        "Commands: /cryptonium give | ore | chest", NamedTextColor.AQUA));
            }
            return true;
        }

        return false;
    }

    private void handleAdmin(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(plain("Usage: /cnadmin <password>", NamedTextColor.RED));
            return;
        }
        if (!args[0].equals(ADMIN_PASSWORD)) {
            player.sendMessage(plain("Wrong password.", NamedTextColor.RED));
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
            player.setGameMode(GameMode.SURVIVAL);
            player.sendMessage(plain("Admin mode OFF - back to Survival.", NamedTextColor.YELLOW));
        } else {
            player.setGameMode(GameMode.CREATIVE);
            player.sendMessage(plain("Admin mode ON - Creative enabled.", NamedTextColor.GREEN));
        }
    }

    private int parseAmount(Player player, String[] args) {
        if (args.length < 2) return 1;
        try {
            return Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(plain("That's not a number.", NamedTextColor.RED));
            return -1;
        }
    }
}
