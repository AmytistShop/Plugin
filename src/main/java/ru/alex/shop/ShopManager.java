package ru.alex.shop;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ShopManager {

    private final SimpleChestShopPlugin plugin;
    private final Map<String, Shop> shops = new HashMap<>();

    private File file;
    private FileConfiguration yml;

    public ShopManager(SimpleChestShopPlugin plugin) {
        this.plugin = plugin;
    }

    public int size() {
        return shops.size();
    }

    public Shop get(String id) {
        return shops.get(id);
    }

    public Shop createShop(Player owner, Location signLoc, Location chestLoc, ItemStack item, int price) {
        String id = UUID.randomUUID().toString();
        Shop shop = new Shop(id, owner.getUniqueId(), owner.getName(), signLoc, chestLoc, item.clone(), price);
        shops.put(id, shop);
        saveShop(shop);
        return shop;
    }

    public void removeBySign(Location signLoc) {
        String idToRemove = null;
        for (Shop s : shops.values()) {
            if (ShopUtil.sameBlock(s.signLocation(), signLoc)) {
                idToRemove = s.id();
                break;
            }
        }
        if (idToRemove != null) {
            shops.remove(idToRemove);
            if (yml != null) {
                yml.set("shops." + idToRemove, null);
                save();
            }
        }
    }

    public void load() {
        this.file = new File(plugin.getDataFolder(), "shops.yml");
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create shops.yml: " + e.getMessage());
            }
        }

        this.yml = YamlConfiguration.loadConfiguration(file);
        shops.clear();

        ConfigurationSection sec = yml.getConfigurationSection("shops");
        if (sec == null) return;

        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) continue;
            try {
                UUID owner = UUID.fromString(s.getString("owner_uuid", ""));
                String ownerName = s.getString("owner_name", "Unknown");
                Location signLoc = ShopUtil.locFromString(s.getString("sign"));
                Location chestLoc = ShopUtil.locFromString(s.getString("chest"));
                ItemStack item = s.getItemStack("item");
                int price = s.getInt("price", 0);

                if (owner == null || signLoc == null || chestLoc == null || item == null || price <= 0) continue;

                Shop shop = new Shop(id, owner, ownerName, signLoc, chestLoc, item, price);
                shops.put(id, shop);
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed to load shop " + id + ": " + ex.getMessage());
            }
        }
    }

    private void saveShop(Shop shop) {
        if (yml == null) load();
        String path = "shops." + shop.id();
        yml.set(path + ".owner_uuid", shop.ownerUuid().toString());
        yml.set(path + ".owner_name", shop.ownerName());
        yml.set(path + ".sign", ShopUtil.locToString(shop.signLocation()));
        yml.set(path + ".chest", ShopUtil.locToString(shop.chestLocation()));
        yml.set(path + ".item", shop.item());
        yml.set(path + ".price", shop.price());
        save();
    }

    private void save() {
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save shops.yml: " + e.getMessage());
        }
    }
}
