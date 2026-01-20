package ru.alex.shop;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public record Shop(
        String id,
        UUID ownerUuid,
        String ownerName,
        Location signLocation,
        Location chestLocation,
        ItemStack item,
        int price
) {}
