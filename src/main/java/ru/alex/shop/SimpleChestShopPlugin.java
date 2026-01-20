package ru.alex.shop;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public class SimpleChestShopPlugin extends JavaPlugin implements Listener {

    public static final String CREATE_TAG = "[Магазин]";

    private Messages messages;
    private ShopManager shopManager;

    private Material currencyMaterial;
    private String currencyNameRu;
    private int maxItemNameLen;

    private NamespacedKey shopKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadAll();

        this.shopKey = new NamespacedKey(this, "shop_id");

        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("SimpleChestShopRU enabled. Loaded shops: " + shopManager.size());
    }

    private void reloadAll() {
        reloadConfig();
        FileConfiguration cfg = getConfig();
        this.messages = new Messages(this, cfg);

        this.maxItemNameLen = cfg.getInt("settings.max_item_name_len", 15);
        this.currencyNameRu = cfg.getString("settings.currency_name_ru", "Алмазов");

        String matName = cfg.getString("settings.currency_material", "DIAMOND");
        Material m = Material.matchMaterial(matName == null ? "DIAMOND" : matName);
        this.currencyMaterial = (m == null ? Material.DIAMOND : m);

        this.shopManager = new ShopManager(this);
        this.shopManager.load();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("sshopreload")) {
            if (!sender.hasPermission("simplechestshop.admin")) {
                sender.sendMessage(messages.color(messages.prefix() + "&cНет прав."));
                return true;
            }
            reloadAll();
            sender.sendMessage(messages.color(messages.prefix() + "&aКонфиг перезагружен."));
            return true;
        }
        return false;
    }

    @EventHandler
    public void onSignChange(SignChangeEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        String l1 = safe(e.getLine(0));
        if (!CREATE_TAG.equalsIgnoreCase(stripColors(l1))) return;

        // Normalize line 0
        e.setLine(0, CREATE_TAG);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = e.getClickedBlock();
        if (block == null) return;
        if (!(block.getState() instanceof Sign sign)) return;

        Player player = e.getPlayer();

        // If sign has shop marker in persistent data -> existing shop
        String shopId = sign.getPersistentDataContainer().get(shopKey, PersistentDataType.STRING);
        if (shopId != null) {
            e.setCancelled(true);
            handlePurchase(player, sign, shopId);
            return;
        }

        // Otherwise: maybe creation sign
        String line0 = stripColors(safe(sign.getLine(0)));
        if (!CREATE_TAG.equalsIgnoreCase(line0)) return;

        if (!player.hasPermission("simplechestshop.create")) {
            player.sendMessage(messages.msg("must_be_owner"));
            return;
        }

        // Parse price from line1 (2nd line)
        String priceRaw = stripColors(safe(sign.getLine(1))).trim();
        int price;
        try {
            price = Integer.parseInt(priceRaw);
        } catch (NumberFormatException ex) {
            player.sendMessage(messages.msg("invalid_price"));
            return;
        }
        if (price <= 0) {
            player.sendMessage(messages.msg("invalid_price"));
            return;
        }

        // Find chest
        Block chestBlock = ShopUtil.findChestForSign(block);
        if (chestBlock == null) {
            player.sendMessage(messages.msg("no_chest_found"));
            return;
        }

        try {
            ItemStack item = ShopUtil.firstSellableItem(chestBlock);
            if (item == null) {
                player.sendMessage(messages.msg("chest_empty"));
                return;
            }
            Shop shop = shopManager.createShop(player, block.getLocation(), chestBlock.getLocation(), item, price);
            // Mark sign with id
            sign.getPersistentDataContainer().set(shopKey, PersistentDataType.STRING, shop.id());
            sign.update(true, false);

            updateSignText(sign, shop);
            player.sendMessage(messages.msg("shop_created"));
        } catch (Exception ex) {
            getLogger().warning("Failed to create shop: " + ex.getMessage());
            ex.printStackTrace();
            player.sendMessage(messages.color(messages.prefix() + "&cОшибка создания магазина."));
        }
        e.setCancelled(true);
    }

    private void handlePurchase(Player buyer, Sign sign, String shopId) {
        if (!buyer.hasPermission("simplechestshop.use")) {
            buyer.sendMessage(messages.color(messages.prefix() + "&cНет прав."));
            return;
        }

        Shop shop = shopManager.get(shopId);
        if (shop == null) {
            // stale sign
            sign.getPersistentDataContainer().remove(shopKey);
            sign.update(true, false);
            buyer.sendMessage(messages.color(messages.prefix() + "&cЭтот магазин больше не существует."));
            return;
        }

        // Ensure sign location matches (protect from copy)
        if (!sameBlock(sign.getLocation(), shop.signLocation())) {
            buyer.sendMessage(messages.color(messages.prefix() + "&cНеверная табличка магазина."));
            return;
        }

        Block chestBlock = shop.chestLocation().getBlock();
        if (!ShopUtil.isChestBlock(chestBlock.getType())) {
            buyer.sendMessage(messages.color(messages.prefix() + "&cСундук магазина не найден."));
            return;
        }

        // Check stock
        if (!ShopUtil.hasAtLeast(chestBlock, shop.item(), shop.item().getAmount())) {
            buyer.sendMessage(messages.msg("not_enough_stock"));
            return;
        }

        // Check diamonds
        int have = ShopUtil.countMaterial(buyer.getInventory(), currencyMaterial);
        if (have < shop.price()) {
            buyer.sendMessage(messages.msg("not_enough_diamonds", "price", String.valueOf(shop.price())));
            return;
        }

        // Take diamonds
        ShopUtil.removeMaterial(buyer.getInventory(), currencyMaterial, shop.price());

        // Take item from chest
        ItemStack out = shop.item().clone();
        boolean removed = ShopUtil.removeItem(chestBlock, shop.item(), shop.item().getAmount());
        if (!removed) {
            // rollback diamonds just in case
            buyer.getInventory().addItem(new ItemStack(currencyMaterial, shop.price()));
            buyer.sendMessage(messages.msg("not_enough_stock"));
            return;
        }

        // Put diamonds into chest
        ShopUtil.addToChest(chestBlock, new ItemStack(currencyMaterial, shop.price()));

        // Give item to player
        var leftovers = buyer.getInventory().addItem(out);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(stack -> buyer.getWorld().dropItemNaturally(buyer.getLocation(), stack));
            buyer.sendMessage(messages.msg("inventory_full_drop"));
        }

        buyer.sendMessage(messages.msg("purchase_success",
                "item", ShopUtil.prettyItemName(shop.item().getType(), maxItemNameLen),
                "amount", String.valueOf(shop.item().getAmount()),
                "price", String.valueOf(shop.price())
        ));

        // Update sign in case item name changed? keep same.
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (!(b.getState() instanceof Sign sign)) return;
        String shopId = sign.getPersistentDataContainer().get(shopKey, PersistentDataType.STRING);
        if (shopId == null) return;
        shopManager.removeBySign(sign.getLocation());
    }

    private void updateSignText(Sign sign, Shop shop) {
        // 1) [Магазин] - owner
        sign.setLine(0, CREATE_TAG + " - " + shop.ownerName());
        // 2) item xN
        String itemName = ShopUtil.prettyItemName(shop.item().getType(), maxItemNameLen);
        sign.setLine(1, itemName + " x" + shop.item().getAmount());
        // 3) price
        sign.setLine(2, "цена: " + shop.price() + " " + currencyNameRu);
        sign.setLine(3, "");
        sign.update(true, false);
    }

    private static boolean sameBlock(Location a, Location b) {
        return a.getWorld() != null && b.getWorld() != null
                && a.getWorld().getUID().equals(b.getWorld().getUID())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String stripColors(String s) {
        return s.replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
    }
}
