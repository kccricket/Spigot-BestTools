package net.kccricket.bestesttool.selftest;

import net.kccricket.bestesttool.BestestToolPlugin;
import net.kccricket.kcmclib.logging.Log;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kccricket.bestesttool.tool.BestToolsHandler;

/**
 * Loads {@code selftest/stages.yml} — the bundled jar resource, or (if present)
 * {@code plugins/BestestTool/selftest.yml} as a wholesale override — into a {@link SelfTestSpec}.
 * Mirrors {@code LangConfig}'s internal/on-disk split, but the override here fully replaces the
 * bundled file rather than layering sparsely on top of it: the stage list is a single ordered
 * sequence, not independent keys, so a partial overlay has no sensible per-key meaning.
 * <p>
 * Malformed entries (unknown material, unparsable case, ...) are skipped with a warning rather
 * than thrown — the same "skip, don't crash" convention as
 * {@code BestToolsHandler}'s global-block-blacklist parsing.
 */
public final class SelfTestStages {

    private SelfTestStages() {}

    static SelfTestSpec load(BestestToolPlugin main) {
        File override = new File(main.getDataFolder(), "selftest.yml");
        YamlConfiguration yaml;
        if (override.isFile()) {
            yaml = YamlConfiguration.loadConfiguration(override);
            Log.debug("Loaded self-test stages from on-disk override: " + override);
        } else {
            yaml = loadBundled(main);
        }
        return parse(yaml);
    }

    private static YamlConfiguration loadBundled(BestestToolPlugin main) {
        try (InputStream in = main.getResource("selftest/stages.yml")) {
            if (in == null) {
                throw new IllegalStateException("Bundled selftest/stages.yml resource is missing");
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read bundled selftest/stages.yml", e);
        }
    }

    private static SelfTestSpec parse(YamlConfiguration yaml) {
        Material pedestal = matchMaterial(yaml.getString("pedestal", "SMOOTH_STONE"));
        if (pedestal == null) pedestal = Material.SMOOTH_STONE;

        List<SelfTestSpec.Stage> stages = new ArrayList<>();
        for (Map<?, ?> rawStage : yaml.getMapList("stages")) {
            SelfTestSpec.Stage stage = parseStage(rawStage);
            if (stage != null) stages.add(stage);
        }
        return new SelfTestSpec(pedestal, stages);
    }

    private static SelfTestSpec.Stage parseStage(Map<?, ?> rawStage) {
        String name = str(rawStage.get("name"));
        if (name == null) {
            Log.warning("Skipping self-test stage with no 'name': " + rawStage);
            return null;
        }
        SelfTestSpec.StageKind kind = parseKind(str(rawStage.get("kind")));
        if (kind == null) {
            Log.warning("Skipping self-test stage '" + name + "': unknown or missing 'kind'");
            return null;
        }

        List<SelfTestSpec.KitItem> kit = new ArrayList<>();
        for (Map<?, ?> rawKitItem : asMapList(rawStage.get("kit"))) {
            SelfTestSpec.KitItem item = parseKitItem(name, rawKitItem);
            if (item != null) kit.add(item);
        }

        List<SelfTestSpec.Case> cases = new ArrayList<>();
        for (Map<?, ?> rawCase : asMapList(rawStage.get("cases"))) {
            SelfTestSpec.Case c = parseCase(name, kind, rawCase);
            if (c != null) cases.add(c);
        }

        return new SelfTestSpec.Stage(name, kind, kit, cases);
    }

    private static SelfTestSpec.StageKind parseKind(String raw) {
        if (raw == null) return null;
        try {
            return SelfTestSpec.StageKind.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static SelfTestSpec.KitItem parseKitItem(String stageName, Map<?, ?> raw) {
        Object slotObj = raw.get("slot");
        if (!(slotObj instanceof Number slotNum)) {
            Log.warning("Skipping self-test kit item in stage '" + stageName + "' with no numeric 'slot': " + raw);
            return null;
        }
        int slot = slotNum.intValue();
        if (slot < 0 || slot > 8) {
            Log.warning("Skipping self-test kit item in stage '" + stageName + "': slot " + slot + " is out of hotbar range 0-8");
            return null;
        }
        Material material = matchMaterial(str(raw.get("material")));
        if (material == null) {
            Log.warning("Skipping self-test kit item in stage '" + stageName + "', slot " + slot + ": unknown material");
            return null;
        }
        int amount = raw.get("amount") instanceof Number n ? Math.max(1, n.intValue()) : 1;

        Map<String, Integer> enchantments = new LinkedHashMap<>();
        if (raw.get("enchantments") instanceof Map<?, ?> rawEnchants) {
            for (Map.Entry<?, ?> e : rawEnchants.entrySet()) {
                if (e.getValue() instanceof Number level) {
                    enchantments.put(String.valueOf(e.getKey()), level.intValue());
                }
            }
        }
        return new SelfTestSpec.KitItem(slot, material, amount, enchantments);
    }

    private static SelfTestSpec.Case parseCase(String stageName, SelfTestSpec.StageKind kind, Map<?, ?> raw) {
        SelfTestSpec.Expectation expectation = parseExpectation(raw.get("expect"));
        if (expectation == null) {
            Log.warning("Skipping self-test case in stage '" + stageName + "' with no valid 'expect': " + raw);
            return null;
        }

        if (kind == SelfTestSpec.StageKind.COMBAT) {
            EntityType entity = parseEntityType(str(raw.get("entity")));
            if (entity == null) {
                Log.warning("Skipping self-test combat case in stage '" + stageName + "': unknown or missing 'entity'");
                return null;
            }
            return SelfTestSpec.Case.forEntity(entity, expectation);
        }

        Material block = matchMaterial(str(raw.get("block")));
        if (block == null) {
            Log.warning("Skipping self-test case in stage '" + stageName + "': unknown or missing 'block'");
            return null;
        }
        boolean requireSilk = Boolean.TRUE.equals(raw.get("silk"));
        return SelfTestSpec.Case.forBlock(block, expectation, requireSilk);
    }

    private static SelfTestSpec.Expectation parseExpectation(Object raw) {
        if (raw instanceof String s) {
            if (s.equalsIgnoreCase("BARE_HAND")) return SelfTestSpec.Expectation.bareHand();
            if (s.equalsIgnoreCase("UNCHANGED")) return SelfTestSpec.Expectation.unchanged();
            Material m = matchMaterial(s);
            return m == null ? null : SelfTestSpec.Expectation.exact(m);
        }
        if (raw instanceof List<?> list) {
            List<Material> materials = new ArrayList<>();
            for (Object o : list) {
                Material m = matchMaterial(str(o));
                if (m != null) materials.add(m);
            }
            return materials.isEmpty() ? null : SelfTestSpec.Expectation.anyOf(materials);
        }
        return null;
    }

    private static EntityType parseEntityType(String raw) {
        if (raw == null) return null;
        try {
            return EntityType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<Map<?, ?>> asMapList(Object raw) {
        if (!(raw instanceof List<?> list)) return List.of();
        List<Map<?, ?>> result = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) result.add(m);
        }
        return result;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Material matchMaterial(String name) {
        if (name == null) return null;
        return Material.matchMaterial(name);
    }
}
