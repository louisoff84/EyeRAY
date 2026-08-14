package fr.craftpick.eyeray.engine;

import fr.craftpick.eyeray.EyeRayPlugin;
import fr.craftpick.eyeray.compat.ServerCompat;
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
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private final Map<ChunkKey, List<ObfuscationTarget>> cache = new HashMap<ChunkKey, List<ObfuscationTarget>>();
    private final Map<UUID, Set<BlockPos>> fakeBlocksByPlayer = new HashMap<UUID, Set<BlockPos>>();
    private final Map<UUID, ChunkKey> lastPlayerChunk = new HashMap<UUID, ChunkKey>();
    private final Queue<ScanRequest> scanQueue = new ArrayDeque<ScanRequest>();
    private final Set<ScanRequestKey> queued = new HashSet<ScanRequestKey>();

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
        scanTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { drainScanQueue(); }
        }, 1L, 1L);
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { refreshPlayers(); }
        }, settings.refreshIntervalTicks(), settings.refreshIntervalTicks());
        reapplyTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() { reapplyAll(); }
        }, settings.reapplyIntervalTicks(), settings.reapplyIntervalTicks());
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
            for (Player player : Bukkit.getOnlinePlayers()) queueNearbyChunks(player);
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
        scanTask = null;
        refreshTask = null;
        reapplyTask = null;
    }

    public void setRuntimeEnabled(boolean enabled) {
        if (runtimeEnabled == enabled) return;
        runtimeEnabled = enabled;
        if (!enabled) {
            restoreAll();
            scanQueue.clear();
            queued.clear();
        } else {
            for (Player player : Bukkit.getOnlinePlayers()) queueNearbyChunks(player);
        }
    }

    public boolean isRuntimeEnabled() { return runtimeEnabled; }
    public EyeRaySettings settings() { return settings; }
    public EyeRayStats stats() { return stats; }
    public int cachedChunks() { return cache.size(); }
    public int queuedScans() { return scanQueue.size(); }

    public int hiddenBlockCount() {
        int total = 0;
        for (Set<BlockPos> positions : fakeBlocksByPlayer.values()) total += positions.size();
        return total;
    }

    public int protectedPlayerCount() {
        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (shouldProtect(player)) count++;
        }
        return count;
    }

    public void onPlayerJoin(final Player player) {
        if (!shouldProtect(player)) return;
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() {
                if (player.isOnline()) queueNearbyChunks(player);
            }
        }, 20L);
    }

    public void onPlayerQuit(Player player) {
        fakeBlocksByPlayer.remove(player.getUniqueId());
        lastPlayerChunk.remove(player.getUniqueId());
        removeQueuedRequests(player.getUniqueId());
    }

    public void onPlayerWorldOrTeleport(final Player player) {
        restorePlayer(player);
        lastPlayerChunk.remove(player.getUniqueId());
        removeQueuedRequests(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() {
                if (player.isOnline() && shouldProtect(player)) queueNearbyChunks(player);
            }
        }, 3L);
    }

    public void invalidate(final Location location) {
        if (location == null || location.getWorld() == null) return;
        final World world = location.getWorld();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                cache.remove(new ChunkKey(world.getUID(), chunkX + dx, chunkZ + dz));
            }
        }
        restoreChangedPosition(location);
        Bukkit.getScheduler().runTaskLater(plugin, new Runnable() {
            @Override public void run() {
                for (Player player : world.getPlayers()) {
                    if (shouldProtect(player) && player.getLocation().distanceSquared(location) <= 96.0 * 96.0) {
                        queueNearbyChunks(player);
                    }
                }
            }
        }, 2L);
    }

    public void invalidateChunk(Chunk chunk) {
        if (chunk != null) cache.remove(ChunkKey.of(chunk));
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
        if (runtimeEnabled) {
            for (Player player : Bukkit.getOnlinePlayers()) queueNearbyChunks(player);
        }
    }

    private void refreshPlayers() {
        if (!runtimeEnabled) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!shouldProtect(player)) {
                restorePlayer(player);
                continue;
            }
            ChunkKey now = ChunkKey.of(player.getLocation().getChunk());
            ChunkKey previous = lastPlayerChunk.put(player.getUniqueId(), now);
            if (!now.equals(previous)) queueNearbyChunks(player);
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
            && player != null
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

            List<ObfuscationTarget> targets = cache.get(request.chunkKey());
            if (targets == null) {
                targets = scanChunk(world.getChunkAt(request.chunkKey().x(), request.chunkKey().z()));
                cache.put(request.chunkKey(), targets);
            }
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
        int minY = Math.max(ServerCompat.getMinHeight(world), settings.scanMinY());
        int maxY = Math.min(world.getMaxHeight() - 1, settings.scanMaxY());
        if (maxY < minY) return Collections.emptyList();

        List<ObfuscationTarget> targets = new ArrayList<ObfuscationTarget>();
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
                        Material cover = coverFor(material);
                        if (cover != null) {
                            targets.add(new ObfuscationTarget(
                                new BlockPos(world.getUID(), x, y, z), material, cover, false));
                        }
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
                                new BlockPos(world.getUID(), x, y, z), material, fake, true));
                            decoys++;
                        }
                    }
                }
            }
        }
        return Collections.unmodifiableList(new ArrayList<ObfuscationTarget>(targets));
    }

    private boolean isExposed(Block block) {
        for (BlockFace face : FACES) {
            Material neighbor = block.getRelative(face).getType();
            if (ServerCompat.isExposing(neighbor)) return true;
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
        String baseName = base.name();
        String[][] options;

        if ("DEEPSLATE".equals(baseName)) {
            options = new String[][] {
                {"DEEPSLATE_DIAMOND_ORE"},
                {"DEEPSLATE_GOLD_ORE"},
                {"DEEPSLATE_REDSTONE_ORE"},
                {"DEEPSLATE_IRON_ORE"},
                {"DEEPSLATE_LAPIS_ORE"}
            };
        } else if ("NETHERRACK".equals(baseName)) {
            options = new String[][] {
                {"ANCIENT_DEBRIS"},
                {"NETHER_GOLD_ORE"},
                {"NETHER_QUARTZ_ORE", "QUARTZ_ORE"}
            };
        } else if ("STONE".equals(baseName)) {
            options = new String[][] {
                {"DIAMOND_ORE"},
                {"GOLD_ORE"},
                {"REDSTONE_ORE"},
                {"IRON_ORE"},
                {"EMERALD_ORE"},
                {"LAPIS_ORE"}
            };
        } else {
            return null;
        }

        List<Material> available = new ArrayList<Material>();
        for (String[] aliases : options) {
            Material material = ServerCompat.material(aliases);
            if (material != null) available.add(material);
        }
        if (available.isEmpty()) return null;

        long hash = mix64(((long) x << 32) ^ ((long) z << 1) ^ y ^ settings.decoySeedSalt());
        int index = (int) Math.floorMod(hash, (long) available.size());
        return available.get(index);
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    private Material coverFor(Material material) {
        String name = material.name();
        if (name.startsWith("DEEPSLATE_")) {
            Material deepslate = ServerCompat.material("DEEPSLATE");
            if (deepslate != null) return deepslate;
        }
        if ("ANCIENT_DEBRIS".equals(name)
            || "NETHER_GOLD_ORE".equals(name)
            || "NETHER_QUARTZ_ORE".equals(name)
            || "QUARTZ_ORE".equals(name)) {
            Material netherrack = ServerCompat.material("NETHERRACK");
            if (netherrack != null) return netherrack;
        }
        return ServerCompat.material("STONE");
    }

    @SuppressWarnings("deprecation")
    private void applyTargets(Player player, List<ObfuscationTarget> targets) {
        if (!shouldProtect(player)) return;
        double revealSquared = settings.revealDistance() * settings.revealDistance();
        Set<BlockPos> fakePositions = fakeBlocksByPlayer.get(player.getUniqueId());
        if (fakePositions == null) {
            fakePositions = new HashSet<BlockPos>();
            fakeBlocksByPlayer.put(player.getUniqueId(), fakePositions);
        }
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

            player.sendBlockChange(pos.toLocation(world), target.fakeMaterial(), (byte) 0);
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
            location.getBlockZ());
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
        for (Player player : Bukkit.getOnlinePlayers()) restorePlayer(player);
        fakeBlocksByPlayer.clear();
    }

    @SuppressWarnings("deprecation")
    private void sendRealBlock(Player player, Block block) {
        player.sendBlockChange(block.getLocation(), block.getType(), (byte) 0);
        stats.blockChange();
    }

    private void removeQueuedRequests(UUID playerId) {
        Iterator<ScanRequest> requestIterator = scanQueue.iterator();
        while (requestIterator.hasNext()) {
            if (requestIterator.next().playerId().equals(playerId)) requestIterator.remove();
        }
        Iterator<ScanRequestKey> keyIterator = queued.iterator();
        while (keyIterator.hasNext()) {
            if (keyIterator.next().playerId().equals(playerId)) keyIterator.remove();
        }
    }

    private static final class ScanRequest {
        private final UUID playerId;
        private final ChunkKey chunkKey;

        private ScanRequest(UUID playerId, ChunkKey chunkKey) {
            this.playerId = playerId;
            this.chunkKey = chunkKey;
        }

        private UUID playerId() { return playerId; }
        private ChunkKey chunkKey() { return chunkKey; }
    }

    private static final class ScanRequestKey {
        private final UUID playerId;
        private final ChunkKey chunkKey;

        private ScanRequestKey(UUID playerId, ChunkKey chunkKey) {
            this.playerId = playerId;
            this.chunkKey = chunkKey;
        }

        private UUID playerId() { return playerId; }
        private ChunkKey chunkKey() { return chunkKey; }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof ScanRequestKey)) return false;
            ScanRequestKey other = (ScanRequestKey) obj;
            return playerId.equals(other.playerId) && chunkKey.equals(other.chunkKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(playerId, chunkKey);
        }
    }
}
