package fr.craftpick.eyeray.model;

import org.bukkit.Chunk;

import java.util.Objects;
import java.util.UUID;

public final class ChunkKey {
    private final UUID worldId;
    private final int x;
    private final int z;

    public ChunkKey(UUID worldId, int x, int z) {
        this.worldId = worldId;
        this.x = x;
        this.z = z;
    }

    public UUID worldId() { return worldId; }
    public int x() { return x; }
    public int z() { return z; }

    public static ChunkKey of(Chunk chunk) {
        return new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ChunkKey)) return false;
        ChunkKey other = (ChunkKey) obj;
        return x == other.x && z == other.z && worldId.equals(other.worldId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldId, x, z);
    }
}
