package com.cryptonium.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Directional;
import org.bukkit.block.sign.Side;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.StructureSearchResult;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Cryptonium - full game plugin.
 *
 * New in this version:
 *   - Permanent daytime + clear weather in the game world.
 *   - SHOP NPCs (lobby + village) opening a GUI shop paid in Cryptonium.
 *   - Shop items: Lucky Pickaxe (3x yield, diamond durability), Cryptonium
 *     Sword, Cryptonium Armor set, Cryptonium Potion (infinite full-heal,
 *     5-minute cooldown).
 *   - Soulbound: shop items, vault chest, and compass never drop on death -
 *     they're returned on respawn. Cryptonium itself still drops.
 *   - Village shop building (spruce market with item-frame displays) and a
 *     quartz/gold cashout station around the banker. Lobby gets a shop stall.
 *   - Players are re-given their vault chest on login if they have none.
 *
 * Existing:
 *   - Lobby void world (rules on signs, ENTER NPC), every login starts there.
 *   - Central village safe zone r80: invincible, no PvP, no building,
 *     explosion-proof, glowstone border ring, enter/exit warning titles.
 *   - CRYPTONIUM BANKER cashes carried Cryptonium into queued payouts
 *     (150,000 tokens each) to the player's saved, validated Solana wallet.
 *   - Personal spawns near the village; beds override.
 *   - Vault chests: private, protected, 10-minute pickup lock.
 *   - Diamonds -> Cryptonium (ore drops, generated loot, no diamond trades).
 *   - Carry glow, pickup/death broadcasts, village compass, beacon.
 *
 * Commands:
 *   /cryptonium give [n] | ore [n] | chest | compass
 *   /wallet set <addr> | /wallet | /wallet clear
 *   /cnadmin <password> [banker|enter|shop]
 */
public class CryptoniumPlugin extends JavaPlugin implements Listener {

    private static final Material ORE_MATERIAL = Material.AMETHYST_BLOCK;
    private static final int DROP_PER_ORE = 1;
    private static final int LUCKY_MULTIPLIER = 3;
    private static final String ADMIN_PASSWORD = "5886";
    private static final String GLOW_TEAM = "cn_carriers";
    private static final long VAULT_PICKUP_LOCK_MS = 10L * 60L * 1000L; // 10 minutes
    private static final long POTION_COOLDOWN_MS = 5L * 60L * 1000L;    // 5 minutes
    private static final String LOBBY_WORLD_NAME = "cn_lobby";
    private static final double SAFE_ZONE_RADIUS = 80.0;
    private static final int SPAWN_RING_MIN = 85;
    private static final int SPAWN_RING_MAX = 115;
    private static final int VILLAGE_SEARCH_RADIUS_CHUNKS = 64;
    private static final long TOKENS_PER_CRYPTONIUM = 150_000L;
    private static final String B58 = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    // Shop prices (in Cryptonium).
    private static final int PRICE_SWORD = 30;
    private static final int PRICE_PICKAXE = 40;
    private static final int PRICE_HELMET = 20;
    private static final int PRICE_CHESTPLATE = 35;
    private static final int PRICE_LEGGINGS = 30;
    private static final int PRICE_BOOTS = 20;
    private static final int PRICE_POTION = 60;

    private NamespacedKey cryptoniumKey;
    private NamespacedKey oreItemKey;
    private NamespacedKey vaultOwnerKey;
    private NamespacedKey npcKey;
    private NamespacedKey compassKey;
    private NamespacedKey soulboundKey;
    private NamespacedKey luckyKey;
    private NamespacedKey potionKey;

    private final Set<String> oreLocations = new HashSet<>();
    private File oreFile;
    private FileConfiguration oreConfig;

    private final Map<String, UUID> chestOwners = new HashMap<>();
    private final Map<String, Long> chestPlaceTime = new HashMap<>();
    private File vaultFile;
    private FileConfiguration vaultConfig;

    private File stateFile;
    private FileConfiguration stateConfig;
    private File spawnsFile;
    private FileConfiguration spawnsConfig;
    private File walletsFile;
    private FileConfiguration walletsConfig;
    private File payoutsFile;
    private FileConfiguration payoutsConfig;

    private World gameWorld;
    private World lobbyWorld;
    private Location lobbySpawn;
    private Location safeCenter;

    private Team glowTeam;
    private final Random random = new Random();
    private final Set<UUID> insideZone = new HashSet<>();
    private final Map<UUID, List<ItemStack>> deathKeeps = new HashMap<>();
    private final Map<UUID, Long> potionCooldown = new HashMap<>();

    /** Empty chunks = a void world for the lobby. */
    public static final class VoidGenerator extends ChunkGenerator {
    }

    /** Marks our shop inventory so clicks can be identified safely. */
    public static final class ShopHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    @Override
    public void onEnable() {
        cryptoniumKey = new NamespacedKey(this, "cryptonium");
        oreItemKey = new NamespacedKey(this, "cryptonium_ore");
        vaultOwnerKey = new NamespacedKey(this, "vault_owner");
        npcKey = new NamespacedKey(this, "cn_npc");
        compassKey = new NamespacedKey(this, "cn_compass");
        soulboundKey = new NamespacedKey(this, "cn_soulbound");
        luckyKey = new NamespacedKey(this, "cn_lucky");
        potionKey = new NamespacedKey(this, "cn_potion");
        getServer().getPluginManager().registerEvents(this, this);
        loadAllFiles();
        setupLobby();
        setupGameWorld();
        setupGlowTeam();
        startMainTask();
        getLogger().info("Cryptonium is enabled. Lobby, safe zone, shops, banker, and beacon are ready.");
    }

    @Override
    public void onDisable() {
        saveOres();
        saveVaults();
        getLogger().info("Cryptonium is disabled.");
    }

    // ---------- Core items ----------

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

    public ItemStack makeVaultChest(UUID owner) {
        ItemStack item = new ItemStack(Material.CHEST, 1);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain("Personal Vault", NamedTextColor.LIGHT_PURPLE));
        meta.lore(List.of(
                plain("Only you can open this.", NamedTextColor.GRAY),
                plain("Store Cryptonium safely inside.", NamedTextColor.GRAY),
                plain("Soulbound - stays with you on death.", NamedTextColor.LIGHT_PURPLE)
        ));
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(vaultOwnerKey, PersistentDataType.STRING, owner.toString());
        meta.getPersistentDataContainer().set(soulboundKey, PersistentDataType.BYTE, (byte) 1);
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

    private boolean hasVaultChestItem(Player player) {
        for (ItemStack it : player.getInventory().getContents()) {
            if (vaultItemOwner(it) != null) return true;
        }
        return false;
    }

    public ItemStack makeVillageCompass() {
        ItemStack item = new ItemStack(Material.COMPASS, 1);
        CompassMeta meta = (CompassMeta) item.getItemMeta();
        meta.displayName(plain("Village Compass", NamedTextColor.GOLD));
        meta.lore(List.of(
                plain("Points to the central village", NamedTextColor.GRAY),
                plain("(the safe cash-out zone).", NamedTextColor.GRAY),
                plain("Soulbound - stays with you on death.", NamedTextColor.LIGHT_PURPLE)
        ));
        if (safeCenter != null) {
            meta.setLodestone(safeCenter);
            meta.setLodestoneTracked(false);
        }
        meta.getPersistentDataContainer().set(compassKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(soulboundKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isVillageCompass(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte tag = item.getItemMeta().getPersistentDataContainer()
                .get(compassKey, PersistentDataType.BYTE);
        return tag != null && tag == (byte) 1;
    }

    private boolean hasVillageCompass(Player player) {
        for (ItemStack it : player.getInventory().getContents()) {
            if (isVillageCompass(it)) return true;
        }
        return false;
    }

    // ---------- Shop items ----------

    private void applyShopMeta(ItemMeta meta, String name, NamedTextColor color,
                               List<String> desc, int price) {
        meta.displayName(plain(name, color));
        List<Component> lore = new ArrayList<>();
        for (String line : desc) lore.add(plain(line, NamedTextColor.GRAY));
        lore.add(plain("Price: " + price + " Cryptonium", NamedTextColor.AQUA));
        lore.add(plain("Soulbound - stays with you on death.", NamedTextColor.LIGHT_PURPLE));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(soulboundKey, PersistentDataType.BYTE, (byte) 1);
    }

    public ItemStack makeLuckyPickaxe() {
        ItemStack item = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        applyShopMeta(meta, "Lucky Pickaxe", NamedTextColor.AQUA,
                List.of("Mines Cryptonium at " + LUCKY_MULTIPLIER + "x yield.",
                        "Diamond durability."), PRICE_PICKAXE);
        meta.getPersistentDataContainer().set(luckyKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isLuckyPickaxe(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte tag = item.getItemMeta().getPersistentDataContainer()
                .get(luckyKey, PersistentDataType.BYTE);
        return tag != null && tag == (byte) 1;
    }

    public ItemStack makeCryptoniumSword() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        applyShopMeta(meta, "Cryptonium Sword", NamedTextColor.LIGHT_PURPLE,
                List.of("Heavy damage (Sharpness V)."), PRICE_SWORD);
        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack makeArmorPiece(Material material, String name, int price) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        applyShopMeta(meta, name, NamedTextColor.LIGHT_PURPLE,
                List.of("Heavy protection (Protection IV)."), price);
        meta.addEnchant(Enchantment.PROTECTION, 4, true);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack makeCryptoniumHelmet() {
        return makeArmorPiece(Material.NETHERITE_HELMET, "Cryptonium Helmet", PRICE_HELMET);
    }

    public ItemStack makeCryptoniumChestplate() {
        return makeArmorPiece(Material.NETHERITE_CHESTPLATE, "Cryptonium Chestplate", PRICE_CHESTPLATE);
    }

    public ItemStack makeCryptoniumLeggings() {
        return makeArmorPiece(Material.NETHERITE_LEGGINGS, "Cryptonium Leggings", PRICE_LEGGINGS);
    }

    public ItemStack makeCryptoniumBoots() {
        return makeArmorPiece(Material.NETHERITE_BOOTS, "Cryptonium Boots", PRICE_BOOTS);
    }

    public ItemStack makeCryptoniumPotion() {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        applyShopMeta(meta, "Cryptonium Potion", NamedTextColor.DARK_PURPLE,
                List.of("Right-click: fully restores HP.",
                        "Never runs out.",
                        "5-minute cooldown."), PRICE_POTION);
        meta.getPersistentDataContainer().set(potionKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isCryptoniumPotion(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte tag = item.getItemMeta().getPersistentDataContainer()
                .get(potionKey, PersistentDataType.BYTE);
        return tag != null && tag == (byte) 1;
    }

    private boolean isSoulbound(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte tag = item.getItemMeta().getPersistentDataContainer()
                .get(soulboundKey, PersistentDataType.BYTE);
        if (tag != null && tag == (byte) 1) return true;
        // Older vault chests / compasses from before the soulbound tag existed.
        return vaultItemOwner(item) != null || isVillageCompass(item);
    }

    // ---------- Shop GUI ----------

    private void openShop(Player player) {
        ShopHolder holder = new ShopHolder();
        Inventory inv = getServer().createInventory(holder, 27,
                Component.text("CRYPTONIUM SHOP").color(NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD));
        holder.inventory = inv;

        ItemStack pane = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta paneMeta = pane.getItemMeta();
        paneMeta.displayName(Component.text(" "));
        pane.setItemMeta(paneMeta);
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        inv.setItem(10, makeCryptoniumSword());
        inv.setItem(11, makeLuckyPickaxe());
        inv.setItem(12, makeCryptoniumHelmet());
        inv.setItem(13, makeCryptoniumChestplate());
        inv.setItem(14, makeCryptoniumLeggings());
        inv.setItem(15, makeCryptoniumBoots());
        inv.setItem(16, makeCryptoniumPotion());

        player.openInventory(inv);
        player.sendMessage(plain("You have " + countCarried(player) + " Cryptonium to spend.", NamedTextColor.AQUA));
    }

    @EventHandler
    public void onShopClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ShopHolder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null
                || !(event.getClickedInventory().getHolder() instanceof ShopHolder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        switch (event.getSlot()) {
            case 10 -> buy(player, makeCryptoniumSword(), PRICE_SWORD, "Cryptonium Sword");
            case 11 -> buy(player, makeLuckyPickaxe(), PRICE_PICKAXE, "Lucky Pickaxe");
            case 12 -> buy(player, makeCryptoniumHelmet(), PRICE_HELMET, "Cryptonium Helmet");
            case 13 -> buy(player, makeCryptoniumChestplate(), PRICE_CHESTPLATE, "Cryptonium Chestplate");
            case 14 -> buy(player, makeCryptoniumLeggings(), PRICE_LEGGINGS, "Cryptonium Leggings");
            case 15 -> buy(player, makeCryptoniumBoots(), PRICE_BOOTS, "Cryptonium Boots");
            case 16 -> buy(player, makeCryptoniumPotion(), PRICE_POTION, "Cryptonium Potion");
            default -> {
            }
        }
    }

    @EventHandler
    public void onShopDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopHolder) {
            event.setCancelled(true);
        }
    }

    private void buy(Player player, ItemStack item, int price, String name) {
        if (!chargeCryptonium(player, price)) {
            player.sendMessage(plain("You need " + price + " Cryptonium for " + name
                    + " (you have " + countCarried(player) + ").", NamedTextColor.RED));
            return;
        }
        giveOrDrop(player, item);
        player.sendMessage(plain("Bought " + name + " for " + price + " Cryptonium!", NamedTextColor.GREEN));
    }

    private boolean chargeCryptonium(Player player, int amount) {
        if (countCarried(player) < amount) return false;
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack it = contents[i];
            if (!isCryptonium(it)) continue;
            int take = Math.min(it.getAmount(), remaining);
            remaining -= take;
            if (take == it.getAmount()) player.getInventory().setItem(i, null);
            else it.setAmount(it.getAmount() - take);
        }
        return true;
    }

    // ---------- Cryptonium Potion use ----------

    @EventHandler
    public void onPotionUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (!isCryptoniumPotion(item)) return;
        event.setCancelled(true); // never throw / consume it

        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        long last = potionCooldown.getOrDefault(player.getUniqueId(), 0L);
        long since = now - last;
        if (since < POTION_COOLDOWN_MS) {
            long left = (POTION_COOLDOWN_MS - since) / 1000L;
            String label = left >= 60 ? (left / 60) + "m " + (left % 60) + "s" : left + "s";
            player.sendMessage(plain("Cryptonium Potion recharging (" + label + " left).", NamedTextColor.RED));
            return;
        }
        potionCooldown.put(player.getUniqueId(), now);
        double max = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        player.setHealth(max);
        player.setFireTicks(0);
        player.setCooldown(Material.EXPERIENCE_BOTTLE, (int) (POTION_COOLDOWN_MS / 50L));
        player.sendMessage(plain("Fully healed! Potion recharges in 5 minutes.", NamedTextColor.GREEN));
    }

    // ---------- Lobby world ----------

    private void setupLobby() {
        World w = getServer().getWorld(LOBBY_WORLD_NAME);
        if (w == null) {
            WorldCreator creator = new WorldCreator(LOBBY_WORLD_NAME);
            creator.generator(new VoidGenerator());
            w = creator.createWorld();
        }
        lobbyWorld = w;
        if (w == null) {
            getLogger().warning("Could not create the lobby world!");
            return;
        }
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setTime(6000L);
        lobbySpawn = new Location(w, 0.5, 101.0, 6.5, 180f, 0f);
        w.setSpawnLocation(0, 101, 6);

        if (!stateConfig.getBoolean("lobbyBuilt", false)) {
            buildLobby(w);
            spawnNpc(new Location(w, 0.5, 101.0, -8.5, 0f, 0f), "enter",
                    "ENTER THE WORLD", NamedTextColor.GREEN);
            stateConfig.set("lobbyBuilt", true);
            saveState();
            getLogger().info("Lobby platform built.");
        }

        if (!stateConfig.getBoolean("lobbyShopBuilt", false)) {
            buildLobbyShop(w);
            stateConfig.set("lobbyShopBuilt", true);
            saveState();
            getLogger().info("Lobby shop stall built.");
        }
    }

    private void buildLobby(World w) {
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                w.getBlockAt(x, 100, z).setType(Material.SMOOTH_QUARTZ);
                if (Math.abs(x) == 12 || Math.abs(z) == 12) {
                    for (int y = 101; y <= 103; y++) {
                        w.getBlockAt(x, y, z).setType(Material.PURPUR_BLOCK);
                    }
                }
            }
        }
        int[] grid = {-8, 0, 8};
        for (int x : grid) {
            for (int z : grid) {
                w.getBlockAt(x, 100, z).setType(Material.SEA_LANTERN);
            }
        }
        String[][] north = {
                {"CRYPTONIUM", "Mine diamond", "ore to get", "Cryptonium"},
                {"DANGER", "Carrying it =", "purple glow +", "drops on death"},
                {"VAULT", "Your chest is", "private. 10min", "lock on place"},
                {"LOOT", "Kill carriers", "and steal their", "Cryptonium"}
        };
        String[][] south = {
                {"CASH OUT", "Central Village", "banker NPC", "pays tokens"},
                {"WALLET", "/wallet set", "<your address>", "paste: Ctrl+V"},
                {"SAFE ZONE", "No PvP or", "building near", "the village"},
                {"START", "Right-click the", "ENTER NPC", "to play"}
        };
        int[] xs = {-9, -4, 1, 6};
        for (int i = 0; i < 4; i++) {
            placeWallSign(w, xs[i], 102, -11, BlockFace.SOUTH, north[i]);
            placeWallSign(w, xs[i], 102, 11, BlockFace.NORTH, south[i]);
        }
    }

    /** Barrel counter + item-frame displays on the west wall + SHOP NPC. */
    private void buildLobbyShop(World w) {
        for (int z = -2; z <= 2; z++) {
            w.getBlockAt(-10, 101, z).setType(Material.BARREL);
        }
        ItemStack[] displays = shopDisplayItems();
        int i = 0;
        for (int z = -3; z <= 3 && i < displays.length; z++, i++) {
            placeFrame(w, -11, 102, z, BlockFace.EAST, displays[i]);
        }
        spawnNpc(new Location(w, -8.5, 101.0, 0.5, -90f, 0f), "shop",
                "SHOP", NamedTextColor.GOLD);
    }

    private ItemStack[] shopDisplayItems() {
        return new ItemStack[]{
                makeCryptoniumSword(), makeLuckyPickaxe(), makeCryptoniumHelmet(),
                makeCryptoniumChestplate(), makeCryptoniumLeggings(), makeCryptoniumBoots(),
                makeCryptoniumPotion()
        };
    }

    private void placeFrame(World w, int x, int y, int z, BlockFace facing, ItemStack item) {
        try {
            Location loc = new Location(w, x, y, z);
            ItemFrame frame = w.spawn(loc, ItemFrame.class);
            frame.setFacingDirection(facing, true);
            frame.setItem(item, false);
            frame.setFixed(true);
            frame.setInvulnerable(true);
        } catch (Exception e) {
            getLogger().warning("Could not place display frame at " + x + "," + y + "," + z + ": " + e.getMessage());
        }
    }

    private void placeWallSign(World w, int x, int y, int z, BlockFace facing, String[] lines) {
        Block b = w.getBlockAt(x, y, z);
        b.setType(Material.OAK_WALL_SIGN);
        if (b.getBlockData() instanceof Directional dir) {
            dir.setFacing(facing);
            b.setBlockData(dir);
        }
        if (b.getState() instanceof Sign sign) {
            for (int i = 0; i < 4 && i < lines.length; i++) {
                sign.getSide(Side.FRONT).line(i, plain(lines[i], NamedTextColor.BLACK));
            }
            sign.setWaxed(true);
            sign.update();
        }
    }

    // ---------- Game world + safe zone ----------

    private void setupGameWorld() {
        gameWorld = getServer().getWorlds().get(0);

        // Permanent daytime + clear weather.
        gameWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        gameWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        gameWorld.setTime(6000L);
        gameWorld.setStorm(false);
        gameWorld.setThundering(false);

        if (stateConfig.contains("safe.x")) {
            safeCenter = new Location(gameWorld,
                    stateConfig.getDouble("safe.x"),
                    stateConfig.getDouble("safe.y"),
                    stateConfig.getDouble("safe.z"));
        } else {
            getLogger().info("Locating the central village (one-time, can take a minute)...");
            Location origin = new Location(gameWorld, 0, 64, 0);
            Location best = null;
            double bestDist = Double.MAX_VALUE;
            Structure[] villages = {Structure.VILLAGE_PLAINS, Structure.VILLAGE_SAVANNA,
                    Structure.VILLAGE_DESERT, Structure.VILLAGE_TAIGA, Structure.VILLAGE_SNOWY};
            for (Structure type : villages) {
                StructureSearchResult result =
                        gameWorld.locateNearestStructure(origin, type, VILLAGE_SEARCH_RADIUS_CHUNKS, true);
                if (result != null) {
                    Location loc = result.getLocation();
                    double d = loc.getX() * loc.getX() + loc.getZ() * loc.getZ();
                    if (d < bestDist) {
                        bestDist = d;
                        best = loc;
                    }
                }
            }
            int cx, cz;
            if (best != null) {
                cx = best.getBlockX();
                cz = best.getBlockZ();
                getLogger().info("Central village found at " + cx + ", " + cz);
            } else {
                cx = gameWorld.getSpawnLocation().getBlockX();
                cz = gameWorld.getSpawnLocation().getBlockZ();
                getLogger().warning("No village found nearby - using world spawn as the cash zone.");
            }
            int cy = gameWorld.getHighestBlockYAt(cx, cz) + 1;
            safeCenter = new Location(gameWorld, cx + 0.5, cy, cz + 0.5);
            gameWorld.setSpawnLocation(cx, cy, cz);
            stateConfig.set("safe.x", safeCenter.getX());
            stateConfig.set("safe.y", safeCenter.getY());
            stateConfig.set("safe.z", safeCenter.getZ());
            saveState();
        }

        if (!stateConfig.getBoolean("bankerSpawned", false)) {
            spawnNpc(safeCenter.clone(), "banker", "CRYPTONIUM BANKER", NamedTextColor.GOLD);
            stateConfig.set("bankerSpawned", true);
            saveState();
        }

        if (!stateConfig.getBoolean("beaconBuilt", false)) {
            buildVillageBeacon();
            stateConfig.set("beaconBuilt", true);
            saveState();
            getLogger().info("Village beacon built.");
        }

        if (!stateConfig.getBoolean("borderBuilt", false)) {
            getLogger().info("Building the safe-zone border ring (one-time)...");
            buildSafeZoneBorder();
            stateConfig.set("borderBuilt", true);
            saveState();
            getLogger().info("Safe-zone border built.");
        }

        if (!stateConfig.getBoolean("villageShopsBuilt", false)) {
            buildCashoutStation();
            buildVillageShop();
            stateConfig.set("villageShopsBuilt", true);
            saveState();
            getLogger().info("Village shop and cashout station built.");
        }
    }

    /** A beacon on a 3x3 iron base near the banker, with a cleared column so the beam shows. */
    private void buildVillageBeacon() {
        if (safeCenter == null) return;
        int bx = safeCenter.getBlockX() + 6;
        int bz = safeCenter.getBlockZ();
        int ground = gameWorld.getHighestBlockYAt(bx, bz);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                gameWorld.getBlockAt(bx + dx, ground + 1, bz + dz).setType(Material.IRON_BLOCK);
            }
        }
        gameWorld.getBlockAt(bx, ground + 2, bz).setType(Material.BEACON);
        int top = Math.min(ground + 60, gameWorld.getMaxHeight() - 1);
        for (int y = ground + 3; y <= top; y++) {
            Block b = gameWorld.getBlockAt(bx, y, bz);
            if (!b.getType().isAir()) b.setType(Material.AIR);
        }
    }

    /** Quartz-and-gold bank structure around the banker NPC. */
    private void buildCashoutStation() {
        if (safeCenter == null) return;
        int bx = safeCenter.getBlockX();
        int bz = safeCenter.getBlockZ();
        int fy = safeCenter.getBlockY() - 1;

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int y = fy + 1; y <= fy + 4; y++) {
                    gameWorld.getBlockAt(bx + dx, y, bz + dz).setType(Material.AIR);
                }
                gameWorld.getBlockAt(bx + dx, fy, bz + dz).setType(Material.SMOOTH_QUARTZ);
            }
        }
        // Gold inlays and glowing center.
        gameWorld.getBlockAt(bx - 2, fy, bz - 2).setType(Material.GOLD_BLOCK);
        gameWorld.getBlockAt(bx + 2, fy, bz - 2).setType(Material.GOLD_BLOCK);
        gameWorld.getBlockAt(bx - 2, fy, bz + 2).setType(Material.GOLD_BLOCK);
        gameWorld.getBlockAt(bx + 2, fy, bz + 2).setType(Material.GOLD_BLOCK);
        gameWorld.getBlockAt(bx, fy, bz).setType(Material.SEA_LANTERN);
        // Pillars with gold caps.
        int[][] corners = {{-3, -3}, {-3, 3}, {3, -3}, {3, 3}};
        for (int[] c : corners) {
            for (int y = fy + 1; y <= fy + 3; y++) {
                gameWorld.getBlockAt(bx + c[0], y, bz + c[1]).setType(Material.QUARTZ_PILLAR);
            }
            gameWorld.getBlockAt(bx + c[0], fy + 4, bz + c[1]).setType(Material.GOLD_BLOCK);
        }
        // Roof.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                gameWorld.getBlockAt(bx + dx, fy + 5, bz + dz).setType(Material.SMOOTH_QUARTZ_SLAB);
            }
        }
        // Teller counter in front of the banker.
        for (int dx = -1; dx <= 1; dx++) {
            gameWorld.getBlockAt(bx + dx, fy + 1, bz + 2).setType(Material.CHISELED_QUARTZ_BLOCK);
        }
    }

    /** Spruce market stall with item-frame displays and the SHOP NPC. */
    private void buildVillageShop() {
        if (safeCenter == null) return;
        int sx = safeCenter.getBlockX() - 12;
        int sz = safeCenter.getBlockZ();
        int fy = gameWorld.getHighestBlockYAt(sx, sz);

        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int y = fy + 1; y <= fy + 4; y++) {
                    gameWorld.getBlockAt(sx + dx, y, sz + dz).setType(Material.AIR);
                }
                gameWorld.getBlockAt(sx + dx, fy, sz + dz).setType(Material.SPRUCE_PLANKS);
            }
        }
        // Solid back wall (north side) to hold the displays.
        for (int dx = -3; dx <= 3; dx++) {
            for (int y = fy + 1; y <= fy + 3; y++) {
                gameWorld.getBlockAt(sx + dx, y, sz - 3).setType(Material.SPRUCE_PLANKS);
            }
        }
        // Corner pillars.
        int[][] corners = {{-3, -3}, {-3, 3}, {3, -3}, {3, 3}};
        for (int[] c : corners) {
            for (int y = fy + 1; y <= fy + 4; y++) {
                gameWorld.getBlockAt(sx + c[0], y, sz + c[1]).setType(Material.SPRUCE_LOG);
            }
        }
        // Roof.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                gameWorld.getBlockAt(sx + dx, fy + 5, sz + dz).setType(Material.SPRUCE_SLAB);
            }
        }
        // Glow.
        gameWorld.getBlockAt(sx - 2, fy, sz + 2).setType(Material.SEA_LANTERN);
        gameWorld.getBlockAt(sx + 2, fy, sz + 2).setType(Material.SEA_LANTERN);
        // Item displays across the back wall.
        ItemStack[] displays = shopDisplayItems();
        int i = 0;
        for (int dx = -3; dx <= 3 && i < displays.length; dx++, i++) {
            placeFrame(gameWorld, sx + dx, fy + 2, sz - 2, BlockFace.SOUTH, displays[i]);
        }
        // The shopkeeper.
        spawnNpc(new Location(gameWorld, sx + 0.5, fy + 1, sz - 0.5, 0f, 0f), "shop",
                "SHOP", NamedTextColor.GOLD);
    }

    /** Glowstone ring set into the ground along the exact safe-zone radius. */
    private void buildSafeZoneBorder() {
        if (safeCenter == null) return;
        int cx = safeCenter.getBlockX();
        int cz = safeCenter.getBlockZ();
        Set<Long> placed = new HashSet<>();
        int steps = 1440;
        for (int i = 0; i < steps; i++) {
            double angle = (Math.PI * 2 * i) / steps;
            int x = cx + (int) Math.round(Math.cos(angle) * SAFE_ZONE_RADIUS);
            int z = cz + (int) Math.round(Math.sin(angle) * SAFE_ZONE_RADIUS);
            long key = (((long) x) << 32) ^ (z & 0xffffffffL);
            if (!placed.add(key)) continue;
            Block top = gameWorld.getHighestBlockAt(x, z);
            int guard = 0;
            while (guard++ < 32 && top.getY() > gameWorld.getMinHeight()
                    && (top.getType().isAir()
                    || Tag.LEAVES.isTagged(top.getType())
                    || Tag.LOGS.isTagged(top.getType()))) {
                top = gameWorld.getBlockAt(x, top.getY() - 1, z);
            }
            top.setType(Material.GLOWSTONE);
        }
    }

    private boolean inLobby(Location l) {
        return lobbyWorld != null && l.getWorld() == lobbyWorld;
    }

    private boolean inSafeZone(Location l) {
        if (safeCenter == null || l.getWorld() != gameWorld) return false;
        double dx = l.getX() - safeCenter.getX();
        double dz = l.getZ() - safeCenter.getZ();
        return dx * dx + dz * dz <= SAFE_ZONE_RADIUS * SAFE_ZONE_RADIUS;
    }

    /** Building is blocked in the lobby and the safe zone (creative admins excepted). */
    private boolean isBuildProtected(Player p, Location l) {
        if (p.getGameMode() == GameMode.CREATIVE) return false;
        return inLobby(l) || inSafeZone(l);
    }

    // ---------- Safe-zone enter/exit warnings ----------

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        Location from = event.getFrom();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()
                && from.getWorld() == to.getWorld()) {
            return;
        }
        Player player = event.getPlayer();
        boolean now = inSafeZone(to);
        boolean was = insideZone.contains(player.getUniqueId());
        if (now == was) return;
        if (now) {
            insideZone.add(player.getUniqueId());
            player.showTitle(Title.title(
                    Component.text("SAFE ZONE").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD),
                    plain("No PvP. No building. Cash out with the banker.", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(500))));
        } else {
            insideZone.remove(player.getUniqueId());
            player.showTitle(Title.title(
                    Component.text("LEAVING SAFE ZONE").color(NamedTextColor.RED).decorate(TextDecoration.BOLD),
                    plain("PvP enabled - watch your Cryptonium!", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(500))));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        insideZone.remove(event.getPlayer().getUniqueId());
    }

    // ---------- NPCs ----------

    private void spawnNpc(Location loc, String role, String name, NamedTextColor color) {
        Villager v = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        v.setAI(false);
        v.setInvulnerable(true);
        v.setSilent(true);
        v.setPersistent(true);
        v.setRemoveWhenFarAway(false);
        v.customName(Component.text(name).color(color).decorate(TextDecoration.BOLD));
        v.setCustomNameVisible(true);
        v.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, role);
    }

    @EventHandler
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        String role = event.getRightClicked().getPersistentDataContainer()
                .get(npcKey, PersistentDataType.STRING);
        if (role == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        switch (role) {
            case "enter" -> sendToGame(player);
            case "banker" -> bankerTalk(player);
            case "shop" -> openShop(player);
            default -> {
            }
        }
    }

    // ---------- Entering the game + personal spawns ----------

    private void sendToGame(Player player) {
        Location spawn = getOrCreateSpawn(player);
        player.teleport(spawn);
        if (safeCenter != null) player.setCompassTarget(safeCenter);
        player.sendMessage(plain("Good luck. Follow your Village Compass (or the beacon beam) to the safe cash-out zone.",
                NamedTextColor.AQUA));
    }

    private Location getOrCreateSpawn(Player player) {
        String key = player.getUniqueId().toString();
        String saved = spawnsConfig.getString(key);
        if (saved != null) {
            String[] parts = saved.split(",");
            try {
                return new Location(gameWorld,
                        Double.parseDouble(parts[0]) + 0.5,
                        Double.parseDouble(parts[1]),
                        Double.parseDouble(parts[2]) + 0.5);
            } catch (Exception ignored) {
            }
        }
        int cx = safeCenter != null ? safeCenter.getBlockX() : 0;
        int cz = safeCenter != null ? safeCenter.getBlockZ() : 0;
        int x = cx, z = cz, y = 80;
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = SPAWN_RING_MIN + random.nextDouble() * (SPAWN_RING_MAX - SPAWN_RING_MIN);
            x = cx + (int) Math.round(Math.cos(angle) * dist);
            z = cz + (int) Math.round(Math.sin(angle) * dist);
            Block top = gameWorld.getHighestBlockAt(x, z);
            y = top.getY() + 1;
            if (!top.isLiquid()) break;
        }
        spawnsConfig.set(key, x + "," + y + "," + z);
        saveSpawns();
        return new Location(gameWorld, x + 0.5, y, z + 0.5);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!event.isBedSpawn() && !event.isAnchorSpawn()) {
            if (spawnsConfig.contains(player.getUniqueId().toString())) {
                event.setRespawnLocation(getOrCreateSpawn(player));
            } else if (lobbySpawn != null) {
                event.setRespawnLocation(lobbySpawn);
            }
        }
        // Return soulbound items kept from their death.
        List<ItemStack> keep = deathKeeps.remove(player.getUniqueId());
        if (keep != null && !keep.isEmpty()) {
            getServer().getScheduler().runTask(this, () -> {
                for (ItemStack it : keep) giveOrDrop(player, it);
                player.sendMessage(plain("Your soulbound items stayed with you.", NamedTextColor.LIGHT_PURPLE));
            });
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.sendMessage(plain("Welcome! Mine Cryptonium, bank it in your vault, cash it out.", NamedTextColor.AQUA));
        if (!hasVaultChestItem(player) && !chestOwners.containsValue(player.getUniqueId())) {
            giveOrDrop(player, makeVaultChest(player.getUniqueId()));
            player.sendMessage(plain("You've been given your Personal Vault chest. Place it to store Cryptonium.",
                    NamedTextColor.LIGHT_PURPLE));
        }
        if (!hasVillageCompass(player)) {
            giveOrDrop(player, makeVillageCompass());
            player.sendMessage(plain("You've been given a Village Compass - it always points to the cash-out village.",
                    NamedTextColor.GOLD));
        }
        if (safeCenter != null) player.setCompassTarget(safeCenter);
        // Everyone starts each session in the lobby.
        getServer().getScheduler().runTask(this, () -> {
            if (lobbySpawn != null) player.teleport(lobbySpawn);
        });
    }

    // ---------- Wallets + cashout ----------

    private boolean isValidSolanaAddress(String s) {
        if (s.length() < 32 || s.length() > 44) return false;
        BigInteger n = BigInteger.ZERO;
        int leadingOnes = 0;
        boolean counting = true;
        for (char ch : s.toCharArray()) {
            int idx = B58.indexOf(ch);
            if (idx < 0) return false;
            if (counting && ch == '1') leadingOnes++;
            else counting = false;
            n = n.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(idx));
        }
        byte[] raw = n.toByteArray();
        int start = (raw.length > 0 && raw[0] == 0) ? 1 : 0;
        int decodedLen = leadingOnes + (raw.length - start);
        return decodedLen == 32;
    }

    private String shortWallet(String w) {
        if (w.length() <= 10) return w;
        return w.substring(0, 4) + "..." + w.substring(w.length() - 4);
    }

    private void bankerTalk(Player player) {
        String wallet = walletsConfig.getString(player.getUniqueId().toString());
        if (wallet == null) {
            player.sendMessage(plain("Set your payout wallet first:", NamedTextColor.RED));
            player.sendMessage(plain("  /wallet set <your Solana address>   (paste with Ctrl+V)", NamedTextColor.YELLOW));
            return;
        }
        int carried = countCarried(player);
        if (carried == 0) {
            player.sendMessage(plain("Bring Cryptonium in your inventory to cash out.", NamedTextColor.YELLOW));
            return;
        }
        long tokens = carried * TOKENS_PER_CRYPTONIUM;
        player.sendMessage(plain("Cash out " + carried + " Cryptonium -> " + String.format("%,d", tokens) + " tokens",
                NamedTextColor.AQUA));
        player.sendMessage(plain("Payout wallet: " + shortWallet(wallet) + "  (change: /wallet set <address>)",
                NamedTextColor.GRAY));
        player.sendMessage(Component.text("  [CLICK HERE TO CONFIRM]")
                .color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/cryptonium confirmcash")));
    }

    private void confirmCashout(Player player) {
        if (!inSafeZone(player.getLocation())) {
            player.sendMessage(plain("You must be at the central village to cash out.", NamedTextColor.RED));
            return;
        }
        String wallet = walletsConfig.getString(player.getUniqueId().toString());
        if (wallet == null) {
            player.sendMessage(plain("Set your payout wallet first: /wallet set <address>", NamedTextColor.RED));
            return;
        }
        int amount = removeAllCryptonium(player);
        if (amount <= 0) {
            player.sendMessage(plain("You have no Cryptonium to cash out.", NamedTextColor.YELLOW));
            return;
        }
        long tokens = amount * TOKENS_PER_CRYPTONIUM;
        List<String> pending = new ArrayList<>(payoutsConfig.getStringList("pending"));
        pending.add(System.currentTimeMillis() + "|" + player.getUniqueId() + "|" + player.getName()
                + "|" + wallet + "|" + amount);
        payoutsConfig.set("pending", pending);
        savePayouts();
        getLogger().info("[CASHOUT] " + player.getName() + " cashed " + amount
                + " Cryptonium (" + tokens + " tokens) -> " + wallet);
        player.sendMessage(plain("Cashed out " + amount + " Cryptonium!", NamedTextColor.GREEN));
        player.sendMessage(plain(String.format("%,d", tokens) + " tokens queued for payout to "
                + shortWallet(wallet), NamedTextColor.GREEN));
    }

    private int removeAllCryptonium(Player player) {
        int total = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isCryptonium(contents[i])) {
                total += contents[i].getAmount();
                player.getInventory().setItem(i, null);
            }
        }
        return total;
    }

    // ---------- Diamonds -> Cryptonium ----------

    private boolean isDiamondOre(Material m) {
        return m == Material.DIAMOND_ORE || m == Material.DEEPSLATE_DIAMOND_ORE;
    }

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

    @EventHandler
    public void onVillagerTrade(VillagerAcquireTradeEvent event) {
        if (event.getRecipe().getResult().getType() == Material.DIAMOND) {
            event.setCancelled(true);
        }
    }

    // ---------- Placing / breaking ----------

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (isBuildProtected(player, event.getBlockPlaced().getLocation())) {
            event.setCancelled(true);
            player.sendMessage(plain("You can't build here.", NamedTextColor.RED));
            return;
        }

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
            player.sendMessage(plain("Vault placed. You can't pick it up for " + lockLabel() + ".",
                    NamedTextColor.LIGHT_PURPLE));
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (isBuildProtected(player, block.getLocation())) {
            event.setCancelled(true);
            player.sendMessage(plain("You can't break blocks here.", NamedTextColor.RED));
            return;
        }

        String key = locKey(block);
        if (chestOwners.containsKey(key)) {
            handleVaultBreak(event, block, key, player);
            return;
        }

        boolean trackedOre = oreLocations.contains(key);
        boolean naturalDiamond = isDiamondOre(block.getType());
        if (!trackedOre && !naturalDiamond) return;

        ItemStack toolItem = player.getInventory().getItemInMainHand();
        if (!isAllowedPickaxe(toolItem.getType())) {
            event.setCancelled(true);
            player.sendMessage(plain("You need at least an iron pickaxe to mine Cryptonium.", NamedTextColor.RED));
            return;
        }

        if (trackedOre) {
            oreLocations.remove(key);
            saveOres();
        }
        event.setDropItems(false);
        int dropAmount = isLuckyPickaxe(toolItem) ? DROP_PER_ORE * LUCKY_MULTIPLIER : DROP_PER_ORE;
        giveOrDrop(player, makeCryptonium(dropAmount));
        player.sendMessage(plain(dropAmount > DROP_PER_ORE
                        ? "Lucky strike! You mined " + dropAmount + " Cryptonium!"
                        : "You mined Cryptonium!",
                NamedTextColor.AQUA));
    }

    private boolean isAllowedPickaxe(Material m) {
        return m == Material.IRON_PICKAXE
                || m == Material.DIAMOND_PICKAXE
                || m == Material.NETHERITE_PICKAXE;
    }

    // ---------- Vault protection ----------

    private String lockLabel() {
        long minutes = VAULT_PICKUP_LOCK_MS / 60000L;
        return minutes >= 1 ? minutes + " minutes" : (VAULT_PICKUP_LOCK_MS / 1000L) + " seconds";
    }

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
            long left = (VAULT_PICKUP_LOCK_MS - elapsed) / 1000L;
            String label = left >= 60 ? (left / 60) + "m " + (left % 60) + "s" : left + "s";
            player.sendMessage(plain("You can't pick up your vault yet (" + label + " left).",
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
        UUID owner = chestOwners.get(locKey(block));
        if (owner == null) return;
        if (!event.getPlayer().getUniqueId().equals(owner)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plain("This is not your vault.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> chestOwners.containsKey(locKey(b)) || inSafeZone(b.getLocation()));
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> chestOwners.containsKey(locKey(b)) || inSafeZone(b.getLocation()));
    }

    // ---------- Safe zone / lobby protection ----------

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && (inLobby(player.getLocation()) || inSafeZone(player.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPvp(EntityDamageByEntityEvent event) {
        Player attacker = null;
        if (event.getDamager() instanceof Player p) attacker = p;
        else if (event.getDamager() instanceof Projectile proj
                && proj.getShooter() instanceof Player p) attacker = p;
        if (attacker != null
                && (inSafeZone(attacker.getLocation()) || inLobby(attacker.getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && inLobby(player.getLocation())) {
            event.setCancelled(true);
        }
    }

    // ---------- Pickup alert / death drop / glow ----------

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack stack = event.getItem().getItemStack();
        if (!isCryptonium(stack)) return;
        getServer().broadcast(plain(
                player.getName() + " picked up " + stack.getAmount() + " Cryptonium!",
                NamedTextColor.GREEN));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Soulbound items never drop - hold them for respawn.
        List<ItemStack> keep = new ArrayList<>();
        event.getDrops().removeIf(it -> {
            if (isSoulbound(it)) {
                keep.add(it);
                return true;
            }
            return false;
        });
        if (!keep.isEmpty()) {
            deathKeeps.merge(player.getUniqueId(), keep, (a, b) -> {
                a.addAll(b);
                return a;
            });
        }

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

    private void setupGlowTeam() {
        Scoreboard board = getServer().getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(GLOW_TEAM);
        if (team == null) {
            team = board.registerNewTeam(GLOW_TEAM);
        }
        team.setColor(ChatColor.LIGHT_PURPLE);
        glowTeam = team;
    }

    private void startMainTask() {
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
                if (inLobby(player.getLocation()) && player.getLocation().getY() < 60 && lobbySpawn != null) {
                    player.teleport(lobbySpawn);
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

    private int countCarried(Player player) {
        int total = 0;
        for (ItemStack it : player.getInventory().getContents()) {
            if (isCryptonium(it)) total += it.getAmount();
        }
        return total;
    }

    // ---------- File storage ----------

    private void loadAllFiles() {
        oreFile = ensureFile("ores.yml");
        oreConfig = YamlConfiguration.loadConfiguration(oreFile);
        oreLocations.addAll(oreConfig.getStringList("ores"));

        vaultFile = ensureFile("vaults.yml");
        vaultConfig = YamlConfiguration.loadConfiguration(vaultFile);
        for (String entry : vaultConfig.getStringList("chests")) {
            String[] parts = entry.split("\\|");
            if (parts.length < 2) continue;
            try {
                chestOwners.put(parts[0], UUID.fromString(parts[1]));
                chestPlaceTime.put(parts[0], parts.length >= 3 ? Long.parseLong(parts[2]) : 0L);
            } catch (IllegalArgumentException ignored) {
            }
        }

        stateFile = ensureFile("state.yml");
        stateConfig = YamlConfiguration.loadConfiguration(stateFile);
        spawnsFile = ensureFile("spawns.yml");
        spawnsConfig = YamlConfiguration.loadConfiguration(spawnsFile);
        walletsFile = ensureFile("wallets.yml");
        walletsConfig = YamlConfiguration.loadConfiguration(walletsFile);
        payoutsFile = ensureFile("payouts.yml");
        payoutsConfig = YamlConfiguration.loadConfiguration(payoutsFile);
    }

    private void saveOres() {
        oreConfig.set("ores", new ArrayList<>(oreLocations));
        saveConfigFile(oreConfig, oreFile);
    }

    private void saveVaults() {
        List<String> chestList = new ArrayList<>();
        for (Map.Entry<String, UUID> e : chestOwners.entrySet()) {
            long time = chestPlaceTime.getOrDefault(e.getKey(), 0L);
            chestList.add(e.getKey() + "|" + e.getValue() + "|" + time);
        }
        vaultConfig.set("chests", chestList);
        saveConfigFile(vaultConfig, vaultFile);
    }

    private void saveState() {
        saveConfigFile(stateConfig, stateFile);
    }

    private void saveSpawns() {
        saveConfigFile(spawnsConfig, spawnsFile);
    }

    private void saveWallets() {
        saveConfigFile(walletsConfig, walletsFile);
    }

    private void savePayouts() {
        saveConfigFile(payoutsConfig, payoutsFile);
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

    private void saveConfigFile(FileConfiguration config, File file) {
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

        if (cmd.equals("wallet")) {
            handleWallet(player, args);
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
                case "compass" -> {
                    giveOrDrop(player, makeVillageCompass());
                    player.sendMessage(plain("Here's a Village Compass - it always points to the cash-out village.",
                            NamedTextColor.GOLD));
                }
                case "confirmcash" -> confirmCashout(player);
                default -> player.sendMessage(plain(
                        "Commands: /cryptonium give | ore | chest | compass   Wallet: /wallet set <address>",
                        NamedTextColor.AQUA));
            }
            return true;
        }

        return false;
    }

    private void handleWallet(Player player, String[] args) {
        String key = player.getUniqueId().toString();
        if (args.length == 0) {
            String current = walletsConfig.getString(key);
            if (current == null) {
                player.sendMessage(plain("No payout wallet saved yet.", NamedTextColor.YELLOW));
                player.sendMessage(plain("Use: /wallet set <your Solana address>  (paste with Ctrl+V)",
                        NamedTextColor.AQUA));
            } else {
                player.sendMessage(plain("Your payout wallet: " + shortWallet(current), NamedTextColor.AQUA));
                player.sendMessage(plain("Change it: /wallet set <address>   Remove it: /wallet clear",
                        NamedTextColor.GRAY));
            }
            return;
        }
        if (args[0].equalsIgnoreCase("set")) {
            if (args.length < 2) {
                player.sendMessage(plain("Usage: /wallet set <your Solana address>", NamedTextColor.RED));
                return;
            }
            String addr = args[1].trim();
            if (!isValidSolanaAddress(addr)) {
                player.sendMessage(plain("That doesn't look like a valid Solana address. Copy it from your wallet app and paste with Ctrl+V.",
                        NamedTextColor.RED));
                return;
            }
            walletsConfig.set(key, addr);
            saveWallets();
            player.sendMessage(plain("Payout wallet saved: " + shortWallet(addr), NamedTextColor.GREEN));
            player.sendMessage(plain("You're set - cash out with the banker at the central village.",
                    NamedTextColor.GRAY));
            return;
        }
        if (args[0].equalsIgnoreCase("clear")) {
            walletsConfig.set(key, null);
            saveWallets();
            player.sendMessage(plain("Payout wallet removed.", NamedTextColor.YELLOW));
            return;
        }
        player.sendMessage(plain("Usage: /wallet set <address> | /wallet | /wallet clear", NamedTextColor.RED));
    }

    private void handleAdmin(Player player, String[] args) {
        if (args.length < 1) {
            player.sendMessage(plain("Usage: /cnadmin <password> [banker|enter|shop]", NamedTextColor.RED));
            return;
        }
        if (!args[0].equals(ADMIN_PASSWORD)) {
            player.sendMessage(plain("Wrong password.", NamedTextColor.RED));
            return;
        }
        if (args.length >= 2) {
            String which = args[1].toLowerCase();
            if (which.equals("banker")) {
                spawnNpc(player.getLocation(), "banker", "CRYPTONIUM BANKER", NamedTextColor.GOLD);
                player.sendMessage(plain("Banker NPC spawned here.", NamedTextColor.GREEN));
                return;
            }
            if (which.equals("enter")) {
                spawnNpc(player.getLocation(), "enter", "ENTER THE WORLD", NamedTextColor.GREEN);
                player.sendMessage(plain("Enter NPC spawned here.", NamedTextColor.GREEN));
                return;
            }
            if (which.equals("shop")) {
                spawnNpc(player.getLocation(), "shop", "SHOP", NamedTextColor.GOLD);
                player.sendMessage(plain("Shop NPC spawned here.", NamedTextColor.GREEN));
                return;
            }
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
