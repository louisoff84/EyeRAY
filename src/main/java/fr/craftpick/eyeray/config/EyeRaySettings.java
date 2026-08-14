package fr.craftpick.eyeray.config;

import fr.craftpick.eyeray.compat.ServerCompat;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class EyeRaySettings {
    public enum WorldMode { BLACKLIST, WHITELIST }

    private final boolean enabled;
    private final int chunkRadius;
    private final double revealDistance;
    private final int refreshIntervalTicks;
    private final int reapplyIntervalTicks;
    private final int scanBudgetPerTick;
    private final int scanMinY;
    private final int scanMaxY;
    private final boolean hideExposedOres;
    private final Set<Material> protectedBlocks;
    private final boolean decoysEnabled;
    private final int maxDecoysPerChunk;
    private final int decoyChancePer10000;
    private final long decoySeedSalt;
    private final Set<Material> decoyBaseBlocks;
    private final WorldMode worldMode;
    private final Set<String> worlds;
    private final String prefix;
    private final String noPermission;
    private final String reloaded;
    private final String enabledMessage;
    private final String disabledMessage;

    private EyeRaySettings(FileConfiguration config) {
        enabled = config.getBoolean("enabled", true);
        chunkRadius = clamp(config.getInt("chunk-radius", 1), 0, 4);
        revealDistance = Math.max(1.0, config.getDouble("reveal-distance", 6.0));
        refreshIntervalTicks = Math.max(1, config.getInt("refresh-interval-ticks", 5));
        reapplyIntervalTicks = Math.max(refreshIntervalTicks, config.getInt("reapply-interval-ticks", 100));
        scanBudgetPerTick = clamp(config.getInt("scan-budget-per-tick", 1), 1, 8);
        scanMinY = config.getInt("scan.min-y", -64);
        scanMaxY = config.getInt("scan.max-y", 128);
        hideExposedOres = config.getBoolean("hide-exposed-ores", false);

        protectedBlocks = parseMaterials(config.getStringList("protected-blocks"));
        decoysEnabled = config.getBoolean("decoys.enabled", true);
        maxDecoysPerChunk = clamp(config.getInt("decoys.max-per-chunk", 24), 0, 256);
        decoyChancePer10000 = clamp(config.getInt("decoys.chance-per-10000", 18), 0, 10000);
        decoySeedSalt = config.getLong("decoys.seed-salt", 918273645L);
        decoyBaseBlocks = parseMaterials(config.getStringList("decoys.base-blocks"));

        WorldMode parsedMode;
        try {
            String mode = config.getString("worlds.mode", "BLACKLIST");
            parsedMode = WorldMode.valueOf(mode.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            parsedMode = WorldMode.BLACKLIST;
        }
        worldMode = parsedMode;
        worlds = new HashSet<String>();
        for (String world : config.getStringList("worlds.list")) {
            worlds.add(world.toLowerCase(Locale.ROOT));
        }

        prefix = color(config.getString("messages.prefix", "&8[&bEyeRAY&8] &7"));
        noPermission = color(config.getString("messages.no-permission", "&cNo permission."));
        reloaded = color(config.getString("messages.reloaded", "&aConfiguration reloaded."));
        enabledMessage = color(config.getString("messages.enabled", "&aProtection enabled."));
        disabledMessage = color(config.getString("messages.disabled", "&cProtection disabled."));
    }

    public static EyeRaySettings load(FileConfiguration config) {
        return new EyeRaySettings(config);
    }

    private static Set<Material> parseMaterials(Iterable<String> names) {
        EnumSet<Material> result = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material material = ServerCompat.materialFromConfig(name);
            if (material != null && material.isBlock()) {
                result.add(material);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public boolean isWorldEnabled(World world) {
        boolean listed = worlds.contains(world.getName().toLowerCase(Locale.ROOT));
        return worldMode == WorldMode.WHITELIST ? listed : !listed;
    }

    public boolean enabled() { return enabled; }
    public int chunkRadius() { return chunkRadius; }
    public double revealDistance() { return revealDistance; }
    public int refreshIntervalTicks() { return refreshIntervalTicks; }
    public int reapplyIntervalTicks() { return reapplyIntervalTicks; }
    public int scanBudgetPerTick() { return scanBudgetPerTick; }
    public int scanMinY() { return scanMinY; }
    public int scanMaxY() { return scanMaxY; }
    public boolean hideExposedOres() { return hideExposedOres; }
    public Set<Material> protectedBlocks() { return protectedBlocks; }
    public boolean decoysEnabled() { return decoysEnabled; }
    public int maxDecoysPerChunk() { return maxDecoysPerChunk; }
    public int decoyChancePer10000() { return decoyChancePer10000; }
    public long decoySeedSalt() { return decoySeedSalt; }
    public Set<Material> decoyBaseBlocks() { return decoyBaseBlocks; }
    public String prefix() { return prefix; }
    public String noPermission() { return noPermission; }
    public String reloaded() { return reloaded; }
    public String enabledMessage() { return enabledMessage; }
    public String disabledMessage() { return disabledMessage; }
}
