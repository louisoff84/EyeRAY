package fr.craftpick.eyeray.listener;

import fr.craftpick.eyeray.engine.OreObfuscationEngine;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public final class WorldListener implements Listener {
    private final OreObfuscationEngine engine;

    public WorldListener(OreObfuscationEngine engine) {
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        engine.invalidate(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        engine.invalidate(event.getBlockPlaced().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) engine.invalidate(block.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        engine.invalidate(event.getBlock().getLocation());
        for (Block block : event.getBlocks()) engine.invalidate(block.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        engine.invalidate(event.getBlock().getLocation());
        for (Block block : event.getBlocks()) engine.invalidate(block.getLocation());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        engine.invalidateChunk(event.getChunk());
    }
}
