package ru.alex.shop;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

public class Messages {
    private final FileConfiguration cfg;

    public Messages(FileConfiguration cfg) {
        this.cfg = cfg;
    }

    public String prefixed(String key) {
        String prefix = color(cfg.getString("messages.prefix", "&a[Магазин]&r "));
        return prefix + get(key);
    }

    public String get(String key) {
        return color(cfg.getString("messages." + key, key));
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
