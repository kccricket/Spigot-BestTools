package net.kccricket.bestesttool.tool;

import org.bukkit.Material;

public class BestToolsCache {

    // This is quite useful right now

    public boolean valid = false;

    public Material lastMat = null;

    public void invalidated() {
        lastMat=null;
        valid=false;
    }

    public void validate(Material material) {
        lastMat = material;
        valid=true;
    }
}
