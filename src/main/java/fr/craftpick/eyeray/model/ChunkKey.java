package fr.craftpick.eyeray.model;

import org.bukkit.Chunk;

import java.util.UUID;

public record ChunkKey(UUID worldId, int x, int z) {
    public static ChunkKey of(Chunk chunk) {
        return new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
    }
}
