package fr.craftpick.eyeray.model;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;
import java.util.UUID;

public final class BlockPos {
    private final UUID worldId;
    private final int x;
    private final int y;
    private final int z;

    public BlockPos(UUID worldId, int x, int y, int z) {
        this.worldId = worldId;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public UUID worldId() { return worldId; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }

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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BlockPos)) return false;
        BlockPos other = (BlockPos) obj;
        return x == other.x && y == other.y && z == other.z && worldId.equals(other.worldId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldId, x, y, z);
    }
}
