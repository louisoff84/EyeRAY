package fr.craftpick.eyeray.model;

import org.bukkit.Material;

public record ObfuscationTarget(BlockPos position, Material expectedMaterial, Material fakeMaterial, boolean decoy) {
}
