package fr.craftpick.eyeray.model;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public record BlockPos(UUID worldId, int x, int y, int z) {
    public Location toLocation(World world) {
        return new Location(world, x, y, z);
    }

    public long distanceSquared(Location location) {
        if (location.getWorld() == null || !worldId.equals(location.getWorld().getUID())) {
            return Long.MAX_VALUE;
        }
        long dx = (long) x - location.getBlockX();
        long dy = (long) y - location.getBlockY();
        long dz = (long) z - location.getBlockZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
