package ru.alex.shop;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public class SimpleChestShopPlugin extends JavaPlugin implements Listener {

    private ShopManager shopManager;
    private Messages messages;
    private Material currencyMat;
    private String currencyName;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadLocal();

        shopManager = new ShopManager(getDataFolder());
        shopManager.load();

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Enabled!");
    }

    private void reloadLocal() {
        reloadConfig();
        FileConfiguration cfg = getConfig();
        messages = new Messages(cfg);

        String mat = cfg.getString("currency.material", "DIAMOND").toUpperCase(Locale.ROOT);
        currencyMat = Material.matchMaterial(mat);
        if (currencyMat == null) currencyMat = Material.DIAMOND;
        currencyName = cfg.getString("currency.name", "Алмазов");
    }

    @Override
    public void onDisable() {
        if (shopManager != null) shopManager.save();
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();

        if (!(b.getState() instanceof Sign sign)) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();

        // Existing shop => buy
        if (shopManager.hasShop(b.getLocation())) {
            if (!p.hasPermission("simplechestshop.use")) {
                p.sendMessage(messages.prefixed("no_permission"));
                return;
            }
            Shop shop = shopManager.get(b.getLocation());
            if (shop.getOwner().equals(p.getUniqueId())) {
                p.sendMessage(messages.prefixed("owner_cant_buy"));
                return;
            }

            Block chestBlock = shop.getChestLoc().getBlock();
            if (!(chestBlock.getState() instanceof Container cont)) {
                p.sendMessage(messages.prefixed("out_of_stock"));
                return;
            }
            Inventory chestInv = cont.getInventory();

            ItemStack sample = shop.getItem();
            int needAmount = shop.getAmount();

            int available = ShopUtil.countSimilar(chestInv, sample);
            if (available < needAmount) {
                p.sendMessage(messages.prefixed("out_of_stock"));
                return;
            }

            int hasMoney = ShopUtil.countCurrency(p.getInventory(), currencyMat);
            if (hasMoney < shop.getPrice()) {
                p.sendMessage(Messages.color(messages.get("messages.prefix")));
                p.sendMessage(Messages.color(messages.get("messages.not_enough_currency").replace("%currency%", currencyName)));
                // fallback:
                p.sendMessage(Messages.color(getConfig().getString("messages.prefix","&a[Магазин]&r ") +
                        getConfig().getString("messages.not_enough_currency","&cУ вас недостаточно %currency%!").replace("%currency%", currencyName)));
                return;
            }

            // give item
            ShopUtil.addToInventoryOrDrop(p.getInventory(), p.getLocation(), sample.clone());

            // remove from chest
            if (!ShopUtil.removeSimilar(chestInv, sample, needAmount)) {
                p.sendMessage(messages.prefixed("out_of_stock"));
                return;
            }

            // take currency
            ShopUtil.removeCurrency(p.getInventory(), currencyMat, shop.getPrice());

            // store currency in chest
            ItemStack pay = new ItemStack(currencyMat, shop.getPrice());
            boolean stored = ShopUtil.addToInventoryOrDrop(chestInv, cont.getLocation(), pay);
            if (!stored) p.sendMessage(messages.prefixed("paid_to_chest_full"));

            e.setCancelled(true);
            return;
        }

        // Create shop only if header matches
        String line1 = ShopUtil.strip(sign.getLine(0));
        if (!ShopUtil.isShopHeader(line1)) return;

        if (!p.hasPermission("simplechestshop.create")) {
            p.sendMessage(messages.prefixed("no_permission"));
            e.setCancelled(true);
            return;
        }

        String priceLine = ShopUtil.strip(sign.getLine(1));
        int price;
        try {
            price = Integer.parseInt(priceLine);
        } catch (Exception ex) {
            p.sendMessage(messages.prefixed("created_need_price"));
            e.setCancelled(true);
            return;
        }

        Container chest = ShopUtil.findAdjacentChest(b);
        if (chest == null) {
            p.sendMessage(messages.prefixed("created_need_chest"));
            e.setCancelled(true);
            return;
        }

        ItemStack item = ShopUtil.firstSellableItem(chest.getInventory());
        if (item == null) {
            p.sendMessage(messages.prefixed("created_need_item"));
            e.setCancelled(true);
            return;
        }

        Shop shop = new Shop(b.getLocation(), chest.getLocation(), p.getUniqueId(), price, item);
        shopManager.add(shop);
        shopManager.save();

        String msg = getConfig().getString("messages.created","&aМагазин создан! Цена: &e%price% %currency%&a. Товар: &e%item% x%amount%&a.");
        msg = msg.replace("%price%", String.valueOf(price))
                 .replace("%currency%", currencyName)
                 .replace("%item%", ShopUtil.formatItemName(item))
                 .replace("%amount%", String.valueOf(item.getAmount()));
        p.sendMessage(Messages.color(getConfig().getString("messages.prefix","&a[Магазин]&r ") + msg));

        // Per your request: DO NOT change sign text.
        e.setCancelled(true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!(b.getState() instanceof Sign)) return;
        if (!shopManager.hasShop(b.getLocation())) return;

        Player p = e.getPlayer();
        if (!p.hasPermission("simplechestshop.admin")) {
            p.sendMessage(messages.prefixed("break_denied"));
            e.setCancelled(true);
            return;
        }

        shopManager.remove(b.getLocation());
        shopManager.save();
        p.sendMessage(messages.prefixed("removed"));
    }
}
