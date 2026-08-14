package fr.craftpick.eyeray.engine;

import fr.craftpick.eyeray.EyeRayPlugin;
import fr.craftpick.eyeray.config.EyeRaySettings;
import fr.craftpick.eyeray.model.BlockPos;
import fr.craftpick.eyeray.model.ChunkKey;
import fr.craftpick.eyeray.model.ObfuscationTarget;
import fr.craftpick.eyeray.stats.EyeRayStats;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

public final class OreObfuscationEngine {
    private static final BlockFace[] FACES = {
        BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
        BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final EyeRayPlugin plugin;
    private final EyeRayStats stats = new EyeRayStats();
    private final Map<ChunkKey, List<ObfuscationTarget>> cache = new HashMap<>();
    private final Map<UUID, Set<BlockPos>> fakeBlocksByPlayer = new HashMap<>();
    private final Map<UUID, ChunkKey> lastPlayerChunk = new HashMap<>();
    private final Queue<ScanRequest> scanQueue = new ArrayDeque<>();
    private final Set<ScanRequestKey> queued = new HashSet<>();

    private EyeRaySettings settings;
    private boolean runtimeEnabled;
    private BukkitTask scanTask;
    private BukkitTask refreshTask;
    private BukkitTask reapplyTask;

    public OreObfuscationEngine(EyeRayPlugin plugin, EyeRaySettings settings) {
        this.plugin = plugin;
        this.settings = settings;
        this.runtimeEnabled = settings.enabled();
    }

    public void start() {
        stopTasks();
        scanTask = Bukkit.getScheduler().runTaskTimer(plugin, this::drainScanQueue, 1L, 1L);
        refreshTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::refreshPlayers,
            settings.refreshIntervalTicks(),
            settings.refreshIntervalTicks()
        );
        reapplyTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::reapplyAll,
            settings.reapplyIntervalTicks(),
            settings.reapplyIntervalTicks()
        );
    }

    public void reload(EyeRaySettings newSettings) {
        restoreAll();
        cache.clear();
        scanQueue.clear();
        queued.clear();
        lastPlayerChunk.clear();
        this.settings = newSettings;
        this.runtimeEnabled = newSettings.enabled();
        start();
        if (runtimeEnabled) {
            Bukkit.getOnlinePlayers().forEach(this::queueNearbyChunks);
        }
    }

    public void shutdown() {
        restoreAll();
        stopTasks();
        cache.clear();
        scanQueue.clear();
        queued.clear();
        lastPlayerChunk.clear();
    }

    private void stopTasks() {
        if (scanTask != null) scanTask.cancel();
        if (refreshTask != null) refreshTask.cancel();
        if (reapplyTask != null) reapplyTask.cancel();
    }

    public void setRuntimeEnabled(boolean enabled) {
        if (runtimeEnabled == enabled) return;
        runtimeEnabled = enabled;
        if (!enabled) {
            restoreAll();
            scanQueue.clear();
            queued.clear();
        } else {
            Bukkit.getOnlinePlayers().forEach(this::queueNearbyChunks);
        }
    }

    public boolean isRuntimeEnabled() {
        return runtimeEnabled;
    }

    public EyeRaySettings settings() {
        return settings;
    }

    public EyeRayStats stats() {
        return stats;
    }

    public int cachedChunks() {
        return cache.size();
    }

    public int queuedScans() {
        return scanQueue.size();
    }

    public int hiddenBlockCount() {
        return fakeBlocksByPlayer.values().stream().mapToInt(Set::size).sum();
    }

    public int protectedPlayerCount() {
        return (int) Bukkit.getOnlinePlayers().stream().filter(this::shouldProtect).count();
    }

    public void onPlayerJoin(Player player) {
        if (!shouldProtect(player)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) queueNearbyChunks(player);
        }, 20L);
    }

    public void onPlayerQuit(Player player) {
        fakeBlocksByPlayer.remove(player.getUniqueId());
        lastPlayerChunk.remove(player.getUniqueId());
        removeQueuedRequests(player.getUniqueId());
    }

    public void onPlayerWorldOrTeleport(Player player) {
        restorePlayer(player);
        lastPlayerChunk.remove(player.getUniqueId());
        removeQueuedRequests(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && shouldProtect(player)) queueNearbyChunks(player);
        }, 3L);
    }

    public void invalidate(Location location) {
        if (location.getWorld() == null) return;
        World world = location.getWorld();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                cache.remove(new ChunkKey(world.getUID(), chunkX + dx, chunkZ + dz));
            }
        }
        restoreChangedPosition(location);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : world.getPlayers()) {
                if (shouldProtect(player) && player.getLocation().distanceSquared(location) <= 96.0 * 96.0) {
                    queueNearbyChunks(player);
                }
            }
        }, 2L);
    }

    public void invalidateChunk(Chunk chunk) {
        cache.remove(ChunkKey.of(chunk));
    }

    public void forceRescan(Player player) {
        restorePlayer(player);
        lastPlayerChunk.remove(player.getUniqueId());
        queueNearbyChunks(player);
    }

    public void clearCacheAndRescan() {
        restoreAll();
        cache.clear();
        scanQueue.clear();
        queued.clear();
        lastPlayerChunk.clear();
        if (runtimeEnabled) Bukkit.getOnlinePlayers().forEach(this::queueNearbyChunks);
    }

    private void refreshPlayers() {
        if (!runtimeEnabled) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!shouldProtect(player)) {
                restorePlayer(player);
                continue;
            }
            Chunk chunk = player.getChunk();
            ChunkKey now = ChunkKey.of(chunk);
            ChunkKey previous = lastPlayerChunk.put(player.getUniqueId(), now);
            if (!now.equals(previous)) {
                queueNearbyChunks(player);
            }
            revealNearby(player);
        }
    }

    private void reapplyAll() {
        if (!runtimeEnabled) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (shouldProtect(player)) queueNearbyChunks(player);
        }
    }

    private boolean shouldProtect(Player player) {
        return runtimeEnabled
            && player.isOnline()
            && !player.hasPermission("eyeray.bypass")
            && settings.isWorldEnabled(player.getWorld());
    }

    public void queueNearbyChunks(Player player) {
        if (!shouldProtect(player)) return;
        int centerX = player.getLocation().getBlockX() >> 4;
        int centerZ = player.getLocation().getBlockZ() >> 4;
        int radius = settings.chunkRadius();
        World world = player.getWorld();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = centerX + dx;
                int chunkZ = centerZ + dz;
                if (!world.isChunkLoaded(chunkX, chunkZ)) continue;
                ChunkKey chunkKey = new ChunkKey(world.getUID(), chunkX, chunkZ);
                List<ObfuscationTarget> targets = cache.get(chunkKey);
                if (targets != null) {
                    applyTargets(player, targets);
                    continue;
                }
                ScanRequest request = new ScanRequest(player.getUniqueId(), chunkKey);
                ScanRequestKey requestKey = new ScanRequestKey(player.getUniqueId(), chunkKey);
                if (queued.add(requestKey)) scanQueue.offer(request);
            }
        }
    }

    private void drainScanQueue() {
        if (!runtimeEnabled) return;
        int budget = settings.scanBudgetPerTick();
        for (int i = 0; i < budget; i++) {
            ScanRequest request = scanQueue.poll();
            if (request == null) return;
            queued.remove(new ScanRequestKey(request.playerId(), request.chunkKey()));

            Player player = Bukkit.getPlayer(request.playerId());
            if (player == null || !shouldProtect(player)) continue;
            if (!player.getWorld().getUID().equals(request.chunkKey().worldId())) continue;

            World world = player.getWorld();
            if (!world.isChunkLoaded(request.chunkKey().x(), request.chunkKey().z())) continue;
            if (!isWithinProtectionRadius(player, request.chunkKey())) continue;

            List<ObfuscationTarget> targets = cache.computeIfAbsent(
                request.chunkKey(),
                key -> scanChunk(world.getChunkAt(key.x(), key.z()))
            );
            applyTargets(player, targets);
        }
    }

    private boolean isWithinProtectionRadius(Player player, ChunkKey chunkKey) {
        int px = player.getLocation().getBlockX() >> 4;
        int pz = player.getLocation().getBlockZ() >> 4;
        int radius = settings.chunkRadius() + 1;
        return Math.abs(px - chunkKey.x()) <= radius && Math.abs(pz - chunkKey.z()) <= radius;
    }

    private List<ObfuscationTarget> scanChunk(Chunk chunk) {
        stats.chunkScanned();
        World world = chunk.getWorld();
        int minY = Math.max(world.getMinHeight(), settings.scanMinY());
        int maxY = Math.min(world.getMaxHeight() - 1, settings.scanMaxY());
        if (maxY < minY) return List.of();

        List<ObfuscationTarget> targets = new ArrayList<>();
        int decoys = 0;
        int startX = chunk.getX() << 4;
        int startZ = chunk.getZ() << 4;

        for (int y = minY; y <= maxY; y++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int x = startX + localX;
                    int z = startZ + localZ;
                    Block block = world.getBlockAt(x, y, z);
                    Material material = block.getType();

                    if (settings.protectedBlocks().contains(material)) {
                        if (!settings.hideExposedOres() && isExposed(block)) continue;
                        targets.add(new ObfuscationTarget(
                            new BlockPos(world.getUID(), x, y, z),
                            material,
                            coverFor(material),
                            false
                        ));
                        continue;
                    }

                    if (settings.decoysEnabled()
                        && decoys < settings.maxDecoysPerChunk()
                        && settings.decoyBaseBlocks().contains(material)
                        && !isExposed(block)
                        && shouldCreateDecoy(world, x, y, z)) {
                        Material fake = decoyFor(material, x, y, z);
                        if (fake != null) {
                            targets.add(new ObfuscationTarget(
                                new BlockPos(world.getUID(), x, y, z),
                                material,
                                fake,
                                true
                            ));
                            decoys++;
                        }
                    }
                }
            }
        }
        return List.copyOf(targets);
    }

    private boolean isExposed(Block block) {
        for (BlockFace face : FACES) {
            Block relative = block.getRelative(face);
            Material neighbor = relative.getType();
            if (neighbor.isAir() || !neighbor.isOccluding()) return true;
        }
        return false;
    }

    private boolean shouldCreateDecoy(World world, int x, int y, int z) {
        long value = settings.decoySeedSalt();
        value ^= world.getSeed();
        value ^= (long) x * 341873128712L;
        value ^= (long) y * 132897987541L;
        value ^= (long) z * 42317861L;
        value = mix64(value);
        int bucket = (int) Math.floorMod(value, 10000L);
        return bucket < settings.decoyChancePer10000();
    }

    private Material decoyFor(Material base, int x, int y, int z) {
        Material[] options;
        if (base == Material.DEEPSLATE) {
            options = new Material[] {
                Material.DEEPSLATE_DIAMOND_ORE,
                Material.DEEPSLATE_GOLD_ORE,
                Material.DEEPSLATE_REDSTONE_ORE,
                Material.DEEPSLATE_IRON_ORE
            };
        } else if (base == Material.NETHERRACK) {
            options = new Material[] {
                Material.ANCIENT_DEBRIS,
                Material.NETHER_GOLD_ORE,
                Material.NETHER_QUARTZ_ORE
            };
        } else if (base == Material.STONE) {
            options = new Material[] {
                Material.DIAMOND_ORE,
                Material.GOLD_ORE,
                Material.REDSTONE_ORE,
                Material.IRON_ORE,
                Material.EMERALD_ORE
            };
        } else {
            return null;
        }
        long hash = mix64(((long) x << 32) ^ ((long) z << 1) ^ y ^ settings.decoySeedSalt());
        return options[(int) Math.floorMod(hash, options.length)];
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private Material coverFor(Material material) {
        String name = material.name();
        if (name.startsWith("DEEPSLATE_")) return Material.DEEPSLATE;
        if (material == Material.ANCIENT_DEBRIS
            || material == Material.NETHER_GOLD_ORE
            || material == Material.NETHER_QUARTZ_ORE) {
            return Material.NETHERRACK;
        }
        return Material.STONE;
    }

    private void applyTargets(Player player, List<ObfuscationTarget> targets) {
        if (!shouldProtect(player)) return;
        double revealSquared = settings.revealDistance() * settings.revealDistance();
        Set<BlockPos> fakePositions = fakeBlocksByPlayer.computeIfAbsent(player.getUniqueId(), id -> new HashSet<>());
        Location playerLocation = player.getLocation();
        World world = player.getWorld();

        for (ObfuscationTarget target : targets) {
            BlockPos pos = target.position();
            if (!pos.worldId().equals(world.getUID())) continue;
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());

            if (block.getType() != target.expectedMaterial()) {
                if (fakePositions.remove(pos)) sendRealBlock(player, block);
                continue;
            }

            if (pos.distanceSquared(playerLocation) <= revealSquared) {
                if (fakePositions.remove(pos)) {
                    sendRealBlock(player, block);
                    stats.blockRevealed();
                }
                continue;
            }

            BlockData fakeData = target.fakeMaterial().createBlockData();
            player.sendBlockChange(pos.toLocation(world), fakeData);
            stats.blockChange();
            if (target.decoy()) stats.decoySent(); else stats.oreHidden();
            fakePositions.add(pos);
        }
    }

    private void revealNearby(Player player) {
        Set<BlockPos> positions = fakeBlocksByPlayer.get(player.getUniqueId());
        if (positions == null || positions.isEmpty()) return;
        double revealSquared = settings.revealDistance() * settings.revealDistance();
        Location location = player.getLocation();
        World world = player.getWorld();

        Iterator<BlockPos> iterator = positions.iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            if (!pos.worldId().equals(world.getUID())) {
                iterator.remove();
                continue;
            }
            if (pos.distanceSquared(location) <= revealSquared) {
                Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
                sendRealBlock(player, block);
                stats.blockRevealed();
                iterator.remove();
            }
        }
        if (positions.isEmpty()) fakeBlocksByPlayer.remove(player.getUniqueId());
    }

    private void restoreChangedPosition(Location location) {
        if (location.getWorld() == null) return;
        BlockPos pos = new BlockPos(
            location.getWorld().getUID(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ()
        );
        for (Player player : location.getWorld().getPlayers()) {
            Set<BlockPos> positions = fakeBlocksByPlayer.get(player.getUniqueId());
            if (positions != null && positions.remove(pos)) {
                sendRealBlock(player, location.getBlock());
            }
        }
    }

    public void restorePlayer(Player player) {
        Set<BlockPos> positions = fakeBlocksByPlayer.remove(player.getUniqueId());
        if (positions == null || positions.isEmpty()) return;
        World world = player.getWorld();
        for (BlockPos pos : positions) {
            if (!pos.worldId().equals(world.getUID())) continue;
            sendRealBlock(player, world.getBlockAt(pos.x(), pos.y(), pos.z()));
        }
    }

    public void restoreAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            restorePlayer(player);
        }
        fakeBlocksByPlayer.clear();
    }

    private void sendRealBlock(Player player, Block block) {
        player.sendBlockChange(block.getLocation(), block.getBlockData());
        stats.blockChange();
    }

    private void removeQueuedRequests(UUID playerId) {
        scanQueue.removeIf(request -> request.playerId().equals(playerId));
        queued.removeIf(key -> key.playerId().equals(playerId));
    }

    private record ScanRequest(UUID playerId, ChunkKey chunkKey) {}
    private record ScanRequestKey(UUID playerId, ChunkKey chunkKey) {}
}
