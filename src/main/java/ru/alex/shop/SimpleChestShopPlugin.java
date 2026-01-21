package ru.alex.shop;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Locale;

public class SimpleChestShopPlugin extends JavaPlugin implements Listener {

    private ShopManager shopManager;
    private Messages m;
    private ItemTranslator translator;

    private Material currency;
    private String currencyName;


    private org.bukkit.Location getInvLocation(org.bukkit.inventory.Inventory inv) {
        if (inv == null) return null;
        org.bukkit.inventory.InventoryHolder h = inv.getHolder();
        if (h instanceof org.bukkit.block.DoubleChest dc) {
            return dc.getLocation();
        }
        if (h instanceof org.bukkit.block.BlockState bs) {
            return bs.getLocation();
        }
        return null;
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadLocal();

        // ensure items_ru.yml exists
        File itemsFile = new File(getDataFolder(), "items_ru.yml");
        if (!itemsFile.exists()) {
            try {
                getDataFolder().mkdirs();
                saveResource("items_ru.yml", false);
            } catch (Exception ignored) {}
        }

        translator = new ItemTranslator();
        translator.loadFromFile(itemsFile);

        shopManager = new ShopManager(getDataFolder());
        shopManager.load();

        getServer().getPluginManager().registerEvents(this, this);
    }

    private void reloadLocal() {
        reloadConfig();
        m = new Messages(getConfig());

        String mat = getConfig().getString("currency.material","DIAMOND").toUpperCase(Locale.ROOT);
        currency = Material.matchMaterial(mat);
        if (currency == null) currency = Material.DIAMOND;
        currencyName = getConfig().getString("currency.name","Алмазов");
    }

    @Override
    public void onDisable() {
        if (shopManager != null) shopManager.save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (!cmd.getName().equalsIgnoreCase("shop")) return false;

        if (args.length >= 1 && args[0].equalsIgnoreCase("refresh")) {
            return handleRefresh(p);
        }

        if (args.length < 1 || !args[0].equalsIgnoreCase("set")) {
            p.sendMessage(m.prefix() + m.get("messages.need_price_amount", "&cИспользуй: &e/shop set <цена> <кол-во>"));
            return true;
        }
        if (args.length < 3) {
            p.sendMessage(m.prefix() + m.get("messages.need_price_amount", "&cИспользуй: &e/shop set <цена> <кол-во>"));
            return true;
        }

        int price, amount;
        try {
            price = Integer.parseInt(args[1]);
            amount = Integer.parseInt(args[2]);
        } catch (Exception ex) {
            p.sendMessage(m.prefix() + m.get("messages.need_price_amount", "&cИспользуй: &e/shop set <цена> <кол-во>"));
            return true;
        }
        if (price <= 0 || amount <= 0) {
            p.sendMessage(m.prefix() + m.get("messages.need_price_amount", "&cИспользуй: &e/shop set <цена> <кол-во>"));
            return true;
        }

        Block target = p.getTargetBlockExact(6);
        if (target == null || !(target.getState() instanceof Container cont)) {
            p.sendMessage(m.prefix() + m.get("messages.need_look_chest", "&cСмотри на сундук."));
            return true;
        }

        ItemStack sample = ShopUtil.firstSellable(cont.getInventory(), currency);
        if (sample == null) {
            p.sendMessage(m.prefix() + m.get("messages.need_item_in_chest", "&cВ сундуке нет товара для продажи!"));
            return true;
        }

        Shop existed = shopManager.getByChest(cont.getLocation());
        boolean isUpdate = existed != null;

        Shop shop = new Shop(cont.getLocation(), p.getUniqueId(), price, amount, sample);

        // place sign on the front side for beauty
        Block signBlock = ShopUtil.placeFrontSign(target);
        if (signBlock != null && signBlock.getState() instanceof Sign sign) {
            String itemName = translator.translate(sample.getType(), sample.getType().name());
            ShopUtil.writeSign(sign, m, getConfig(), p.getName(), itemName, amount, price, currencyName);
            shop.setSignLoc(sign.getLocation());
        }

        shopManager.put(shop);
        shopManager.save();

        String msg = m.prefix() + (isUpdate ? m.get("messages.updated","") : m.get("messages.created",""));
        msg = msg.replace("%price%", String.valueOf(price))
                 .replace("%amount%", String.valueOf(amount))
                 .replace("%currency%", currencyName);
        p.sendMessage(msg);
        return true;
    }

    private boolean handleRefresh(Player p) {
        Block target = p.getTargetBlockExact(6);
        if (target == null || !(target.getState() instanceof Container cont)) {
            p.sendMessage(m.prefix() + m.get("messages.need_look_chest", "&cСмотри на сундук."));
            return true;
        }
        Shop shop = shopManager.getByChest(cont.getLocation());
        if (shop == null) {
            p.sendMessage(m.prefix() + m.get("messages.need_look_chest", "&cСмотри на сундук."));
            return true;
        }
        if (!shop.getOwner().equals(p.getUniqueId()) && !p.hasPermission("simplechestshop.admin")) {
            p.sendMessage(m.prefix() + m.get("messages.break_denied", "&cТолько владелец может это делать!"));
            return true;
        }

        if (shop.getSignLoc() != null && shop.getSignLoc().getBlock().getState() instanceof Sign sign) {
            String itemName = translator.translate(shop.getItemSample().getType(), shop.getItemSample().getType().name());
            ShopUtil.writeSign(sign, m, getConfig(), p.getName(), itemName, shop.getAmount(), shop.getPrice(), currencyName);
            p.sendMessage(m.prefix() + m.get("messages.refreshed", "&aТабличка магазина обновлена."));
            return true;
        }

        // if sign missing, try place again
        Block signBlock = ShopUtil.placeFrontSign(target);
        if (signBlock != null && signBlock.getState() instanceof Sign sign) {
            String itemName = translator.translate(shop.getItemSample().getType(), shop.getItemSample().getType().name());
            ShopUtil.writeSign(sign, m, getConfig(), p.getName(), itemName, shop.getAmount(), shop.getPrice(), currencyName);
            shop.setSignLoc(sign.getLocation());
            shopManager.put(shop);
            shopManager.save();
            p.sendMessage(m.prefix() + m.get("messages.refreshed", "&aТабличка магазина обновлена."));
            return true;
        }

        p.sendMessage(m.prefix() + m.get("messages.refreshed", "&aТабличка магазина обновлена."));
        return true;
    }

    // Buyers purchase through chest UI: clicking product in the chest triggers purchase.
    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        Inventory top = e.getView().getTopInventory();
        org.bukkit.Location loc = getInvLocation(top);
        if (loc == null) return;

        Shop shop = shopManager.getByChest(loc);
        if (shop == null) return;

        // owner/admin can manage chest freely
        if (shop.getOwner().equals(p.getUniqueId()) || p.hasPermission("simplechestshop.admin")) return;

        // Only handle clicks in the top inventory (the shop chest)
        int topSize = top.getSize();
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= topSize) return;

        // Block any interaction with the shop chest by default
        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        ItemStack sample = shop.getItemSample();

        // Buy only when clicking the selling item
        if (!clicked.isSimilar(sample)) return;

        int need = shop.getAmount();
        int available = ShopUtil.countSimilar(top, sample);
        if (available < need) {
            p.closeInventory();
            p.sendMessage(m.prefix() + m.get("messages.out_of_stock","&cВ магазине нет товара!"));
            return;
        }

        int price = shop.getPrice();
        int has = ShopUtil.countMaterial(p.getInventory(), currency);
        if (has < price) {
            p.closeInventory();
            p.sendMessage(m.prefix() + m.get("messages.not_enough_currency_close","&cНедостаточно %currency% для покупки!")
                    .replace("%currency%", currencyName));
            return;
        }

        // take money
        ShopUtil.removeMaterial(p.getInventory(), currency, price);

        // remove items from chest
        ShopUtil.removeSimilar(top, sample, need);

        // give items
        ItemStack give = sample.clone();
        give.setAmount(need);
        ShopUtil.addOrDrop(p.getInventory(), p.getLocation(), give);

        // store money in chest (or drop)
        boolean stored = ShopUtil.addOrDrop(top, loc, new ItemStack(currency, price));
        if (!stored) {
            p.sendMessage(m.prefix() + m.get("messages.chest_full_drop","&eСундук продавца заполнен, валюта выброшена рядом."));
        }

        String itemName = translator.translate(sample.getType(), sample.getType().name());
        String bought = m.get("messages.bought","")
                .replace("%item%", itemName)
                .replace("%amount%", String.valueOf(need))
                .replace("%price%", String.valueOf(price))
                .replace("%currency%", currencyName);
        p.sendMessage(m.prefix() + bought);
    }

    @EventHandler
    public void onInvDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        Inventory top = e.getView().getTopInventory();
        org.bukkit.Location loc = getInvLocation(top);
        if (loc == null) return;

        Shop shop = shopManager.getByChest(loc);
if (shop == null) return;

        if (shop.getOwner().equals(p.getUniqueId()) || p.hasPermission("simplechestshop.admin")) return;

        int topSize = top.getSize();
        for (int slot : e.getRawSlots()) {
            if (slot < topSize) {
                e.setCancelled(true);
                return;
            }
        }
    }

    // Decorative sign protection: only owner can break (admin optional)
    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!(b.getState() instanceof Sign)) return;

        Shop shop = shopManager.getBySign(b.getLocation());
        if (shop == null) return;

        Player p = e.getPlayer();
        if (shop.getOwner().equals(p.getUniqueId()) || p.hasPermission("simplechestshop.admin")) {
            shopManager.removeByChest(shop.getChestLoc());
            shopManager.save();
            p.sendMessage(m.prefix() + m.get("messages.removed","&aМагазин удалён."));
            return;
        }

        e.setCancelled(true);
        p.sendMessage(m.prefix() + m.get("messages.break_denied","&cТолько владелец может ломать табличку магазина!"));
    }
}
