package ru.alex.shop;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

public class Messages {
    private final FileConfiguration cfg;

    public Messages(FileConfiguration cfg) {
        this.cfg = cfg;
    }

    public String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    public String get(String path, String def) {
        return color(cfg.getString(path, def));
    }

    public String prefix() {
        return color(cfg.getString("messages.prefix", "&a[Магазин]&r "));
    }
}
