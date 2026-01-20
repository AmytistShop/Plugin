package ru.alex.shop;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ShopManager {
    private final File file;
    private YamlConfiguration yml;
    private final Map<String, Shop> shops = new HashMap<>();

    public ShopManager(File dataFolder) {
        this.file = new File(dataFolder, "shops.yml");
    }

    public void load() {
        if (!file.exists()) {
            try { file.getParentFile().mkdirs(); file.createNewFile(); } catch (IOException ignored) {}
        }
        yml = YamlConfiguration.loadConfiguration(file);
        shops.clear();

        ConfigurationSection root = yml.getConfigurationSection("shops");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;

            Location signLoc = locFrom(s.getConfigurationSection("sign"));
            Location chestLoc = locFrom(s.getConfigurationSection("chest"));
            UUID owner = UUID.fromString(s.getString("owner"));
            int price = s.getInt("price");
            ItemStack item = s.getItemStack("item");

            if (signLoc == null || chestLoc == null || owner == null || item == null) continue;
            shops.put(key, new Shop(signLoc, chestLoc, owner, price, item));
        }
    }

    public void save() {
        yml.set("shops", null);
        for (Map.Entry<String, Shop> e : shops.entrySet()) {
            String key = e.getKey();
            Shop shop = e.getValue();
            String path = "shops." + key;

            yml.set(path + ".owner", shop.getOwner().toString());
            yml.set(path + ".price", shop.getPrice());
            yml.set(path + ".item", shop.getItem());

            setLoc(path + ".sign", shop.getSignLoc());
            setLoc(path + ".chest", shop.getChestLoc());
        }
        try { yml.save(file); } catch (IOException ignored) {}
    }

    private void setLoc(String path, Location loc) {
        yml.set(path + ".world", loc.getWorld().getName());
        yml.set(path + ".x", loc.getBlockX());
        yml.set(path + ".y", loc.getBlockY());
        yml.set(path + ".z", loc.getBlockZ());
    }

    private Location locFrom(ConfigurationSection s) {
        if (s == null) return null;
        World w = Bukkit.getWorld(s.getString("world"));
        if (w == null) return null;
        return new Location(w, s.getInt("x"), s.getInt("y"), s.getInt("z"));
    }

    public String key(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public boolean hasShop(Location signLoc) {
        return shops.containsKey(key(signLoc));
    }

    public Shop get(Location signLoc) {
        return shops.get(key(signLoc));
    }

    public void add(Shop shop) {
        shops.put(key(shop.getSignLoc()), shop);
    }

    public void remove(Location signLoc) {
        shops.remove(key(signLoc));
    }
}
