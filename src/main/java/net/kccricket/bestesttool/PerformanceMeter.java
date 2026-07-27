package net.kccricket.bestesttool;

import net.kccricket.bestesttool.security.Permissions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.Locale;

public class PerformanceMeter {

    Main main;

    PerformanceMeter(Main main) {
        this.main=main;
    }

    long puffer;
    int i=0;
    int max=50;

    int cached=0;
    int uncached=0;

    long start=0;
    long end=0;

    final static int nanoPerMilli=1000000;

    void add(long t,boolean cached) {
        long t2 = System.nanoTime();
        if(!main.measurePerformance) return;
        if(cached) { this.cached++; } else { uncached++; }
        if(start==0) start =t2;
        puffer+=t2-t;
        i++;
        if(i==max) printAndReset();
    }

    private void printAndReset() {
        long end=System.nanoTime();
        double secondsBetween = (end-start) / (double)(nanoPerMilli*1000);
        double calcTime = puffer/(double)nanoPerMilli;
        double calcTimePercent = (calcTime /  (secondsBetween*1000)) * 100;


        main.getLogger().warning(String.format(Locale.US,
                "%10.2f ms elapsed, of which BestTools took %5.2f ms or %5.3f %% - %2d / %2d "
                + " queries served by cache (%3d %%)",

                secondsBetween*1000,
                calcTime,
                calcTimePercent,
                cached,
                cached+uncached,
                (int) Math.ceil(cached / (double) (cached+uncached) * 100)
        ));

        NamedTextColor color = NamedTextColor.GREEN;
        if(calcTimePercent>=1) color = NamedTextColor.YELLOW;
        if(calcTimePercent>=2) color = NamedTextColor.RED;

        NamedTextColor color2 = NamedTextColor.GREEN;
        if(calcTimePercent<=20) color2 = NamedTextColor.YELLOW;
        if(calcTimePercent==0) color2 = NamedTextColor.RED;

        int cachePercent = (int) Math.ceil(cached / (double) (cached+uncached) * 100);
        Component message = Component.text(String.format(Locale.US,
                        "Elapsed: %.2f ms, BestTools: %3.2f ms or ", secondsBetween*1000, calcTime))
                .append(Component.text(String.format(Locale.US, "%2.3f %%", calcTimePercent), color))
                .append(Component.newline())
                .append(Component.text(String.format(Locale.US,
                        "%d / %d queries served by cache ", cached, cached+uncached)))
                .append(Component.text(String.format(Locale.US, "(%3d %%)", cachePercent), color2));

        for(Player p : main.getServer().getOnlinePlayers()) {
            if(Permissions.isAllowedTo(p, Permissions.PERM_DEBUG))
                p.sendMessage(message);
        }

        i=0;puffer=0;start=0;
    }

}
