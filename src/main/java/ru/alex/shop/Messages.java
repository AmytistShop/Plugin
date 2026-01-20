package ru.alex.shop;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;

public class Messages {

    private final SimpleChestShopPlugin plugin;
    private final FileConfiguration cfg;

    public Messages(SimpleChestShopPlugin plugin, FileConfiguration cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    public String prefix() {
        return cfg.getString("messages.prefix", "&7[&bМагазин&7]&r ");
    }

    public String msg(String key, String... placeholders) {
        String raw = cfg.getString("messages." + key, "");
        String text = prefix() + (raw == null ? "" : raw);
        if (placeholders != null && placeholders.length >= 2) {
            for (int i = 0; i + 1 < placeholders.length; i += 2) {
                text = text.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
            }
        }
        return color(text);
    }

    public String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
