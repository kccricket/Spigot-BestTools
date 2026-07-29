package net.kccricket.bestesttool.util;

import net.kccricket.bestesttool.BestestToolPlugin;
import net.kccricket.kcmclib.logging.DebugLevel;
import net.kccricket.kcmclib.logging.Log;

import org.bukkit.Material;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class FileUtils {
    final BestestToolPlugin main;

    public FileUtils(BestestToolPlugin main) {
        this.main=main;
    }

    public void dumpFile(File file) throws IOException {
        FileWriter fileWriter = new FileWriter(file);
        PrintWriter printWriter = new PrintWriter(fileWriter);
        // getBestToolType() logs a Log.debug() line per material; suppress that spam for the
        // duration of iterating every Material, then restore whatever level was active.
        DebugLevel previousLevel = Log.getDebugLevel();
        Log.setDebugLevel(DebugLevel.OFF);
        for(Material mat: Material.values()) {
            if(!mat.isBlock()) continue;
            printWriter.printf("%s,%s\n",mat.name(),main.toolHandler.getBestToolType(mat).name());
        }
        Log.setDebugLevel(previousLevel);

        printWriter.close();
    }

}
