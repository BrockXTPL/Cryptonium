package com.cryptonium.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cryptonium - Step 2.
 *
 * Step 1 gave us the Cryptonium item. Step 2 makes it MINEABLE:
 *   - Adds a "Cryptonium Ore" block (a purple crystal block) you can place.
 *   - When you break one of OUR ore blocks, it drops Cryptonium instead of itself.
 *   - Only ore we placed counts, so normal blocks are unaffected.
 *   - The list of ore locations is saved to a file, so it survives restarts.
 *
 * Commands:
 *   /cryptonium give [amount]  -> get Cryptonium items
 *   /cryptonium ore  [amount]  -> get Cryptonium Ore blocks to place and mine
 */
public class CryptoniumPlugin extends JavaPlugin implements Listener {

    // The block type we use to REPRESENT Cryptonium ore in the world.
    private static final Material ORE_MATERIAL = Material.AMETHYST_BLOCK;

    // How much Cryptonium one ore block drops when mined.
    private static final int DROP_PER_ORE = 1;

    private NamespacedKey cryptoniumKey;   // stamps the Cryptonium item
    private NamespacedKey oreItemKey;      // stamps the placeable ore item

    // Remembers every ore block we placed, as "world,x,y,z".
    private final Set<String> oreLocations = new HashSet<>();
    private File oreFile;
    private FileConfiguration oreConfig;

    @Override
    public void onEnable() {
        cryptoniumKey = new NamespacedKey(this, "cryptonium");
        oreItemKey = new NamespacedKey(this, "cryptonium_ore");
        getServer().getPluginManager().registerEvents(this, this);
        loadOres();
        getLogger().info("Cryptonium is enabled. /cryptonium give  and  /cryptonium ore  are ready.");
    }

    @Override
    public void onDisable() {
        saveOres();
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

    // ---------- Cryptonium ore (placeable block item) ----------

    public ItemStack makeCryptoniumOre(int amount) {
        ItemStack item = new ItemStack(ORE_MATERIAL, clamp(amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plain("Cryptonium Ore", NamedTextColor.LIGHT_PURPLE));
        meta.lore(List.of(
                plain("Place it, then mine it", NamedTextColor.GRAY),
                plain("to get Cryptonium.", NamedTextColor.GRAY)
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

    // ---------- Mining logic ----------

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (isCryptoniumOreItem(event.getItemInHand())) {
            oreLocations.add(locKey(event.getBlockPlaced()));
            saveOres();
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String key = locKey(block);
        if (oreLocations.remove(key)) {
            saveOres();
            event.setDropItems(false); // don't drop the block itself
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5),
                    makeCryptonium(DROP_PER_ORE));
            event.getPlayer().sendMessage(plain("You mined Cryptonium!", NamedTextColor.AQUA));
        }
    }

    // ---------- Saving / loading ore locations ----------

    private void loadOres() {
        oreFile = new File(getDataFolder(), "ores.yml");
        if (!oreFile.exists()) {
            getDataFolder().mkdirs();
            try {
                oreFile.createNewFile();
            } catch (IOException e) {
                getLogger().warning("Could not create ores.yml: " + e.getMessage());
            }
        }
        oreConfig = YamlConfiguration.loadConfiguration(oreFile);
        oreLocations.addAll(oreConfig.getStringList("ores"));
    }

    private void saveOres() {
        if (oreConfig == null || oreFile == null) return;
        oreConfig.set("ores", new ArrayList<>(oreLocations));
        try {
            oreConfig.save(oreFile);
        } catch (IOException e) {
            getLogger().warning("Could not save ores.yml: " + e.getMessage());
        }
    }

    private String locKey(Block block) {
        Location l = block.getLocation();
        return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    // ---------- Small helpers ----------

    private int clamp(int amount) {
        return Math.max(1, Math.min(amount, 64));
    }

    private Component plain(String text, NamedTextColor color) {
        return Component.text(text).color(color).decoration(TextDecoration.ITALIC, false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage(plain("Welcome! Mine Cryptonium, bank it, cash it out.", NamedTextColor.AQUA));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("cryptonium")) return false;

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("give")) {
            int amount = parseAmount(player, args);
            if (amount < 0) return true;
            player.getInventory().addItem(makeCryptonium(amount));
            player.sendMessage(plain("You received " + clamp(amount) + " Cryptonium.", NamedTextColor.GREEN));
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("ore")) {
            int amount = parseAmount(player, args);
            if (amount < 0) return true;
            player.getInventory().addItem(makeCryptoniumOre(amount));
            player.sendMessage(plain("You received " + clamp(amount) + " Cryptonium Ore. Place it and mine it!",
                    NamedTextColor.GREEN));
            return true;
        }

        player.sendMessage(plain("Cryptonium is running. Try: /cryptonium give 5  or  /cryptonium ore 5",
                NamedTextColor.AQUA));
        return true;
    }

    /** Reads the amount argument, or returns 1 if none. Returns -1 if it was invalid (and warns the player). */
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
