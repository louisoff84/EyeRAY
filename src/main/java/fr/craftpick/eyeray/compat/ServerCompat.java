package fr.craftpick.eyeray.compat;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;

import java.lang.reflect.Method;

/**
 * Small compatibility layer deliberately limited to APIs that changed between
 * Bukkit/Spigot 1.8.8 and modern Paper versions.
 */
public final class ServerCompat {
    private ServerCompat() {
    }

    public static int getMinHeight(World world) {
        try {
            Method method = world.getClass().getMethod("getMinHeight");
            Object value = method.invoke(world);
            if (value instanceof Integer) {
                return ((Integer) value).intValue();
            }
        } catch (ReflectiveOperationException ignored) {
            // Bukkit 1.8.8 through 1.16 worlds start at Y=0.
        }
        return 0;
    }

    public static Material material(String... names) {
        if (names == null) return null;
        for (String name : names) {
            if (name == null || name.trim().isEmpty()) continue;
            Material material = Material.matchMaterial(name);
            if (material != null && material.isBlock()) return material;
        }
        return null;
    }

    public static Material materialFromConfig(String name) {
        if (name == null) return null;
        String normalized = name.trim().toUpperCase();

        // 1.8-1.12 name -> 1.13+ name aliases.
        if ("QUARTZ_ORE".equals(normalized)) {
            return material("NETHER_QUARTZ_ORE", "QUARTZ_ORE");
        }

        Material direct = material(normalized);
        if (direct != null) return direct;

        // Some modern servers expose old names through LEGACY_* constants.
        return material("LEGACY_" + normalized);
    }

    public static boolean isExposing(Material material) {
        if (material == null) return true;
        String name = material.name();
        if (name.endsWith("AIR") || "AIR".equals(name)) return true;
        if ("WATER".equals(name) || "STATIONARY_WATER".equals(name)) return true;
        if ("LAVA".equals(name) || "STATIONARY_LAVA".equals(name)) return true;
        return !material.isSolid();
    }

    public static String serverVersion() {
        String bukkitVersion = Bukkit.getBukkitVersion();
        return bukkitVersion == null ? "unknown" : bukkitVersion;
    }
}
