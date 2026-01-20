package ru.alex.shop;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ShopUtil {

    public static boolean isShopHeader(String line) {
        if (line == null) return false;
        String t = strip(line).toLowerCase(Locale.ROOT);
        return t.equals("[магазин]") || t.equals("[shop]");
    }

    public static String strip(String s) {
        return s == null ? "" : s.replaceAll("§.", "").trim();
    }

    public static Container findAdjacentChest(Block signBlock) {
        for (Block rel : adjacentBlocks(signBlock)) {
            BlockState st = rel.getState();
            if (st instanceof Container c) return c;
        }
        return null;
    }

    private static List<Block> adjacentBlocks(Block b) {
        return Arrays.asList(
                b.getRelative(1,0,0),
                b.getRelative(-1,0,0),
                b.getRelative(0,1,0),
                b.getRelative(0,-1,0),
                b.getRelative(0,0,1),
                b.getRelative(0,0,-1)
        );
    }

    public static ItemStack firstSellableItem(Inventory inv) {
        for (ItemStack it : inv.getContents()) {
            if (it != null && it.getType() != Material.AIR && it.getAmount() > 0) {
                return it.clone();
            }
        }
        return null;
    }

    public static String formatItemName(ItemStack item) {
        if (item == null) return "";
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return strip(meta.getDisplayName());
        }
        return niceEnum(item.getType().name());
    }

    private static String niceEnum(String name) {
        String[] parts = name.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    public static int countSimilar(Inventory inv, ItemStack sample) {
        int count = 0;
        for (ItemStack it : inv.getContents()) {
            if (it == null || it.getType() == Material.AIR) continue;
            if (it.isSimilar(sample)) count += it.getAmount();
        }
        return count;
    }

    public static boolean removeSimilar(Inventory inv, ItemStack sample, int amount) {
        int remaining = amount;
        ItemStack[] contents = inv.getContents();
        for (int i=0;i<contents.length;i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType() == Material.AIR) continue;
            if (!it.isSimilar(sample)) continue;

            int take = Math.min(remaining, it.getAmount());
            it.setAmount(it.getAmount() - take);
            remaining -= take;

            if (it.getAmount() <= 0) contents[i] = null;
            if (remaining <= 0) break;
        }
        inv.setContents(contents);
        return remaining <= 0;
    }

    public static int countCurrency(Inventory inv, Material currency) {
        int count = 0;
        for (ItemStack it : inv.getContents()) {
            if (it != null && it.getType() == currency) count += it.getAmount();
        }
        return count;
    }

    public static boolean removeCurrency(Inventory inv, Material currency, int amount) {
        return removeSimilar(inv, new ItemStack(currency), amount);
    }

    public static boolean addToInventoryOrDrop(Inventory inv, Location dropLoc, ItemStack stack) {
        Map<Integer, ItemStack> left = inv.addItem(stack);
        if (left.isEmpty()) return true;
        for (ItemStack it : left.values()) {
            dropLoc.getWorld().dropItemNaturally(dropLoc, it);
        }
        return false;
    }
}
