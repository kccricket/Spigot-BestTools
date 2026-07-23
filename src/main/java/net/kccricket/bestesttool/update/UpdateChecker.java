package net.kccricket.bestesttool.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kccricket.bestesttool.Main;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class UpdateChecker {

    private static final String API_URL = "https://api.modrinth.com/v2/project/bfE7PKmz/version";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final Main plugin;
    private ScheduledTask scheduledTask;

    public UpdateChecker(Main plugin) {
        this.plugin = plugin;
    }

    /** Fire the check asynchronously and log the outcome. Returns immediately. */
    public void check() {
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> run());
    }

    /**
     * (Re)starts the update-check subsystem: fires an immediate check (when "check-for-updates"
     * is "true" or "on-startup") and rearms the recurring schedule. The single entry point for
     * enable and /besttools reload, so every trigger gets the same cadence.
     */
    public void restart() {
        String mode = plugin.getConfig().getString("check-for-updates", "true");
        if (mode.equalsIgnoreCase("true") || mode.equalsIgnoreCase("on-startup")) {
            check();
        }
        reschedule();
    }

    /**
     * (Re)schedules the recurring update check per "check-for-updates"/"check-interval",
     * cancelling any previously scheduled task first. Safe to call repeatedly (on enable and
     * after every reload). Does not itself fire an immediate check.
     */
    public void reschedule() {
        stop();
        String mode = plugin.getConfig().getString("check-for-updates", "true");
        if (!mode.equalsIgnoreCase("true")) {
            return;
        }
        long periodHours = plugin.getConfig().getInt("check-interval", 4);
        if (periodHours <= 0) {
            periodHours = 4;
        }
        scheduledTask = plugin.getServer().getAsyncScheduler().runAtFixedRate(
                plugin, task -> run(), periodHours, periodHours, TimeUnit.HOURS);
    }

    /** Cancels the recurring check task, if one is scheduled. Safe to call when none is running. */
    public void stop() {
        if (scheduledTask != null) {
            try {
                scheduledTask.cancel();
            } catch (Exception e) {
                plugin.debug("Failed to cancel update check task: " + e.getClass().getSimpleName());
            } finally {
                scheduledTask = null;
            }
        }
    }

    private void run() {
        String current = plugin.getPluginMeta().getVersion();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
                    .timeout(TIMEOUT)
                    .header("User-Agent", "kccricket/BestestTool/" + current + " (github.com/kccricket/Spigot-BestTools)")
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                plugin.debug("Update check skipped: Modrinth returned HTTP " + response.statusCode());
                return;
            }

            String latest = latestRelease(response.body());
            if (latest == null) {
                plugin.debug("Update check: no release versions found on Modrinth.");
                return;
            }

            if (compareVersions(latest, current) > 0) {
                plugin.getLogger().log(Level.INFO, "A new version of BestestTool is available: "
                        + latest + " (you are running " + current + ").");
                plugin.getLogger().log(Level.INFO, "Download: "
                        + "https://modrinth.com/plugin/bfE7PKmz | "
                        + "https://hangar.papermc.io/kccricket/BestestTool | "
                        + "https://github.com/kccricket/Spigot-BestTools/releases");
            } else {
                plugin.debug("Update check: BestestTool is up to date (" + current + ").");
            }
        } catch (Exception e) {
            plugin.debug("Update check failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * Parse the Modrinth version-list JSON and return the highest {@code release} version number,
     * or {@code null} if none is present. Modrinth returns versions roughly newest-first, but we do
     * not rely on ordering.
     */
    private String latestRelease(String body) {
        JsonArray versions = JsonParser.parseString(body).getAsJsonArray();
        String best = null;
        for (var element : versions) {
            JsonObject version = element.getAsJsonObject();
            if (!version.has("version_number") || !version.has("version_type")) continue;
            if (!"release".equals(version.get("version_type").getAsString())) continue;
            String number = version.get("version_number").getAsString();
            if (best == null || compareVersions(number, best) > 0) {
                best = number;
            }
        }
        return best;
    }

    /**
     * Compare two dotted version strings numerically. A pre-release suffix (anything after a
     * {@code -}) sorts before the same base version. Returns &gt;0 if {@code a} is newer than
     * {@code b}, &lt;0 if older, 0 if equal. Tolerates a leading {@code v}.
     */
    static int compareVersions(String a, String b) {
        String[] aSplit = splitVersion(a);
        String[] bSplit = splitVersion(b);

        int cmp = compareNumeric(aSplit[0], bSplit[0]);
        if (cmp != 0) return cmp;

        boolean aPre = !aSplit[1].isEmpty();
        boolean bPre = !bSplit[1].isEmpty();
        if (aPre != bPre) return aPre ? -1 : 1;
        return aSplit[1].compareTo(bSplit[1]);
    }

    /** Returns [base, preReleaseSuffix]; suffix is "" when there is none. */
    private static String[] splitVersion(String v) {
        String stripped = v.startsWith("v") || v.startsWith("V") ? v.substring(1) : v;
        int dash = stripped.indexOf('-');
        if (dash < 0) return new String[]{stripped, ""};
        return new String[]{stripped.substring(0, dash), stripped.substring(dash + 1)};
    }

    private static int compareNumeric(String a, String b) {
        String[] aParts = a.split("\\.");
        String[] bParts = b.split("\\.");
        int len = Math.max(aParts.length, bParts.length);
        for (int i = 0; i < len; i++) {
            int aVal = i < aParts.length ? parse(aParts[i]) : 0;
            int bVal = i < bParts.length ? parse(bParts[i]) : 0;
            if (aVal != bVal) return Integer.compare(aVal, bVal);
        }
        return 0;
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
