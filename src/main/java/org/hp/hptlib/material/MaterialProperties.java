package org.hp.hptlib.material;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.GsonHelper;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public record MaterialProperties(Map<String, Double> values, Set<MaterialStatsId> exclude, boolean replace) {
    public static final String HARDNESS = "hardness";
    public static final String DENSITY = "density";
    public static final String TOUGHNESS = "toughness";

    public static MaterialProperties parse(JsonObject json) {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put(HARDNESS, 0d);
        values.put(DENSITY, 0d);
        values.put(TOUGHNESS, 0d);
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (entry.getValue() instanceof JsonPrimitive primitive && primitive.isNumber()) {
                values.put(entry.getKey(), primitive.getAsDouble());
            }
        }
        Set<MaterialStatsId> exclude = new LinkedHashSet<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "exclude", new JsonArray())) {
            exclude.add(new MaterialStatsId(GsonHelper.convertToString(element, "exclude")));
        }
        return new MaterialProperties(java.util.Collections.unmodifiableMap(values), Set.copyOf(exclude), GsonHelper.getAsBoolean(json, "replace", false));
    }

    public double get(String key) {
        return this.values.getOrDefault(key, 0d);
    }

    public double hardness() {
        return this.get(HARDNESS);
    }

    public double density() {
        return this.get(DENSITY);
    }

    public double toughness() {
        return this.get(TOUGHNESS);
    }
}
