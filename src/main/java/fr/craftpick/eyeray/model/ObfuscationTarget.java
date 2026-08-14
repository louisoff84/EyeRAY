package fr.craftpick.eyeray.model;

import org.bukkit.Material;

public final class ObfuscationTarget {
    private final BlockPos position;
    private final Material expectedMaterial;
    private final Material fakeMaterial;
    private final boolean decoy;

    public ObfuscationTarget(BlockPos position, Material expectedMaterial, Material fakeMaterial, boolean decoy) {
        this.position = position;
        this.expectedMaterial = expectedMaterial;
        this.fakeMaterial = fakeMaterial;
        this.decoy = decoy;
    }

    public BlockPos position() { return position; }
    public Material expectedMaterial() { return expectedMaterial; }
    public Material fakeMaterial() { return fakeMaterial; }
    public boolean decoy() { return decoy; }
}
