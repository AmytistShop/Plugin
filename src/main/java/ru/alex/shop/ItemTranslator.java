package ru.alex.shop;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class ItemTranslator {
    private final Map<String, String> map = new HashMap<>();

    public void loadFromFile(File f) {
        map.clear();
        if (f == null || !f.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        for (String k : yml.getKeys(false)) {
            if (k == null) continue;
            String v = yml.getString(k);
            if (v != null && !v.isEmpty()) map.put(k.toUpperCase(), v);
        }
    }

    public String translate(Material mat, String fallback) {
        if (mat == null) return fallback;
        String v = map.get(mat.name());
        return (v == null || v.isEmpty()) ? fallback : v;
    }
}
