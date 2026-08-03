package com.cryptonium.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Cryptonium - Step 1 starter plugin.
 *
 * What this does right now:
 *   - Adds "Cryptonium" as a real, custom item (a glowing purple shard).
 *   - The item is tagged internally so later steps can tell real Cryptonium
 *     apart from any look-alike item (this matters once it maps to real money).
 *   - /cryptonium give [amount]  -> gives you Cryptonium so we can see it in-game.
 *   - Greets players when they join.
 *
 * Everything else (ore, drop-on-death, vault, cashout) builds on top of this.
 */
public class CryptoniumPlugin extends JavaPlugin implements Listener {

    // The hidden "stamp" that marks an item as genuine Cryptonium.
    private NamespacedKey cryptoniumKey;

    @Override
    public void onEnable() {
        cryptoniumKey = new NamespacedKey(this, "cryptonium");
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Cryptonium is enabled. Type /cryptonium give to get some.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Cryptonium is disabled.");
    }

    /** Builds one stack of genuine, tagged Cryptonium. */
    public ItemStack makeCryptonium(int amount) {
        ItemStack item = new ItemStack(Material.AMETHYST_SHARD, Math.max(1, Math.min(amount, 64)));
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text("Cryptonium")
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));

        meta.lore(List.of(
                Component.text("A rare on-chain resource.")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Bank it to keep it safe.")
                        .color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));

        // Make it glow so it stands out.
        meta.setEnchantmentGlintOverride(true);

        // Stamp it as real Cryptonium.
        meta.getPersistentDataContainer().set(cryptoniumKey, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    /** True if the given item is genuine, plugin-made Cryptonium. */
    public boolean isCryptonium(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte tag = item.getItemMeta().getPersistentDataContainer()
                .get(cryptoniumKey, PersistentDataType.BYTE);
        return tag != null && tag == (byte) 1;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage(Component.text("Welcome! Mine Cryptonium, bank it, cash it out.")
                .color(NamedTextColor.AQUA));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("cryptonium")) return false;

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("give")) {
            int amount = 1;
            if (args.length >= 2) {
                try {
                    amount = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(Component.text("That's not a number.").color(NamedTextColor.RED));
                    return true;
                }
            }
            player.getInventory().addItem(makeCryptonium(amount));
            player.sendMessage(Component.text("You received " + Math.max(1, Math.min(amount, 64)) + " Cryptonium.")
                    .color(NamedTextColor.GREEN));
            return true;
        }

        player.sendMessage(Component.text("Cryptonium is running. Try: /cryptonium give 5")
                .color(NamedTextColor.AQUA));
        return true;
    }
}
