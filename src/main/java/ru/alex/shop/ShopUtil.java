package ru.alex.shop;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

import java.util.Locale;

public final class ShopUtil {
    private ShopUtil() {}

    public static boolean isChestBlock(Material m) {
        return m == Material.CHEST || m == Material.TRAPPED_CHEST;
    }

    /**
     * Find a chest associated with a sign block.
     * Supports:
     * - Wall sign attached to chest
     * - Standing sign placed on top of chest
     * - Otherwise searches adjacent blocks (radius 1)
     */
    public static Block findChestForSign(Block signBlock) {
        // Wall sign: chest is behind the sign (opposite facing)
        var data = signBlock.getBlockData();
        if (data instanceof WallSign ws) {
            BlockFace facing = ws.getFacing();
            Block behind = signBlock.getRelative(facing.getOppositeFace());
            if (isChestBlock(behind.getType())) return behind;
        }

        // Standing sign: check below
        Block below = signBlock.getRelative(BlockFace.DOWN);
        if (isChestBlock(below.getType())) return below;

        // Search 1-block radius
        for (BlockFace face : new BlockFace[]{
                BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
                BlockFace.UP, BlockFace.DOWN
        }) {
            Block b = signBlock.getRelative(face);
            if (isChestBlock(b.getType())) return b;
        }

        return null;
    }

    public static ItemStack firstSellableItem(Block chestBlock) {
        Inventory inv = chestInventory(chestBlock);
        if (inv == null) return null;
        for (ItemStack it : inv.getContents()) {
            if (it == null || it.getType().isAir()) continue;
            if (it.getAmount() <= 0) continue;
            return it.clone();
        }
        return null;
    }

    public static boolean hasAtLeast(Block chestBlock, ItemStack template, int amount) {
        Inventory inv = chestInventory(chestBlock);
        if (inv == null) return false;
        int count = 0;
        for (ItemStack it : inv.getContents()) {
            if (it == null || it.getType().isAir()) continue;
            if (isSimilarForTrade(it, template)) {
                count += it.getAmount();
                if (count >= amount) return true;
            }
        }
        return false;
    }

    public static boolean removeItem(Block chestBlock, ItemStack template, int amount) {
        Inventory inv = chestInventory(chestBlock);
        if (inv == null) return false;
        int toRemove = amount;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            if (!isSimilarForTrade(it, template)) continue;

            int take = Math.min(toRemove, it.getAmount());
            it.setAmount(it.getAmount() - take);
            if (it.getAmount() <= 0) inv.setItem(i, null);
            else inv.setItem(i, it);
            toRemove -= take;
            if (toRemove <= 0) return true;
        }
        return false;
    }

    public static void addToChest(Block chestBlock, ItemStack stack) {
        Inventory inv = chestInventory(chestBlock);
        if (inv == null) return;
        var leftovers = inv.addItem(stack);
        // If chest is full, drop leftovers at chest
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(s -> chestBlock.getWorld().dropItemNaturally(chestBlock.getLocation().add(0.5, 1.0, 0.5), s));
        }
    }

    public static int countMaterial(PlayerInventory inv, Material mat) {
        int count = 0;
        for (ItemStack it : inv.getContents()) {
            if (it == null || it.getType().isAir()) continue;
            if (it.getType() == mat) count += it.getAmount();
        }
        return count;
    }

    public static void removeMaterial(PlayerInventory inv, Material mat, int amount) {
        int toRemove = amount;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            if (it.getType() != mat) continue;
            int take = Math.min(toRemove, it.getAmount());
            it.setAmount(it.getAmount() - take);
            if (it.getAmount() <= 0) inv.setItem(i, null);
            else inv.setItem(i, it);
            toRemove -= take;
            if (toRemove <= 0) break;
        }
    }

    public static Inventory chestInventory(Block chestBlock) {
        if (!(chestBlock.getState() instanceof InventoryHolder holder)) return null;
        return holder.getInventory();
    }

    private static boolean isSimilarForTrade(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getType() != b.getType()) return false;
        // Compare meta (name, lore, enchants, custom model data, etc.)
        if (a.hasItemMeta() || b.hasItemMeta()) {
            if (!a.hasItemMeta() || !b.hasItemMeta()) return false;
            return a.getItemMeta().equals(b.getItemMeta());
        }
        return true;
    }

    public static String prettyItemName(Material mat, int maxLen) {
        String base = mat.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        // Simple Russian-ish: capitalize first letter
        String pretty = base.isEmpty() ? base : (base.substring(0, 1).toUpperCase(Locale.ROOT) + base.substring(1));
        if (maxLen > 0 && pretty.length() > maxLen) {
            return pretty.substring(0, Math.max(0, maxLen - 1)) + "…";
        }
        return pretty;
    }

    public static boolean sameBlock(Location a, Location b) {
        return a != null && b != null
                && a.getWorld() != null && b.getWorld() != null
                && a.getWorld().getUID().equals(b.getWorld().getUID())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    public static String locToString(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    public static Location locFromString(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] p = s.split(";");
        if (p.length != 4) return null;
        var w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            int x = Integer.parseInt(p[1]);
            int y = Integer.parseInt(p[2]);
            int z = Integer.parseInt(p[3]);
            return new Location(w, x, y, z);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
