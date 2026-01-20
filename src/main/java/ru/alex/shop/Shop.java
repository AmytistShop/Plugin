package ru.alex.shop;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class Shop {
    private final Location signLoc;
    private final Location chestLoc;
    private final UUID owner;
    private final int price;
    private final ItemStack item; // includes fixed amount

    public Shop(Location signLoc, Location chestLoc, UUID owner, int price, ItemStack item) {
        this.signLoc = signLoc;
        this.chestLoc = chestLoc;
        this.owner = owner;
        this.price = price;
        this.item = item;
    }

    public Location getSignLoc() { return signLoc; }
    public Location getChestLoc() { return chestLoc; }
    public UUID getOwner() { return owner; }
    public int getPrice() { return price; }
    public ItemStack getItem() { return item.clone(); }
    public int getAmount() { return item.getAmount(); }
}
