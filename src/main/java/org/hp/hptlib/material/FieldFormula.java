package org.hp.hptlib.material;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public record FieldFormula(LinearFormula value, List<Level> levels) {
    public static FieldFormula parse(JsonObject json, String context) {
        if (!json.has("levels")) {
            return new FieldFormula(LinearFormula.parse(json, context), List.of());
        }
        LinearFormula score = LinearFormula.parse(GsonHelper.getAsJsonObject(json, "score"), context + ".score");
        List<Level> levels = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "levels")) {
            if (!(element instanceof JsonArray pair) || pair.size() != 2 || !pair.get(0).isJsonPrimitive()) {
                throw new JsonParseException(context + ": each level must be [threshold, value]");
            }
            levels.add(new Level(pair.get(0).getAsDouble(), pair.get(1)));
        }
        if (levels.isEmpty()) {
            throw new JsonParseException(context + ": levels must not be empty");
        }
        levels.sort(Comparator.comparingDouble(Level::threshold));
        return new FieldFormula(score, List.copyOf(levels));
    }

    public JsonElement compute(Map<String, Double> properties) {
        double result = this.value.evaluate(properties);
        if (this.levels.isEmpty()) {
            return result == Math.rint(result) && Math.abs(result) < 1e15 ? new JsonPrimitive((long) result) : new JsonPrimitive(result);
        }
        JsonElement picked = this.levels.get(0).value();
        for (Level level : this.levels) {
            if (result >= level.threshold()) {
                picked = level.value();
            }
        }
        return picked.deepCopy();
    }

    public record Level(double threshold, JsonElement value) {}
}
