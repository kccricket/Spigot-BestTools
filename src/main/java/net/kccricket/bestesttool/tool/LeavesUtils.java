package net.kccricket.bestesttool.tool;

import org.bukkit.Material;

public class LeavesUtils {

    public static boolean isLeaves(Material mat) {
        return mat.name().endsWith("_LEAVES");
    }

}
