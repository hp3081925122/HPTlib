package org.hp.hptlib.material;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.util.GsonHelper;
import slimeknights.tconstruct.library.modifiers.ModifierId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record StatFormula(Map<String, FieldFormula> fields, List<TraitFormula> traits) {
    public static StatFormula parse(JsonObject json, String context) {
        Map<String, FieldFormula> fields = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : GsonHelper.getAsJsonObject(json, "fields", new JsonObject()).entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                throw new JsonParseException(context + ": field '" + entry.getKey() + "' must be an object");
            }
            fields.put(entry.getKey(), FieldFormula.parse(entry.getValue().getAsJsonObject(), context + "." + entry.getKey()));
        }
        List<TraitFormula> traits = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "traits", new JsonArray())) {
            JsonObject trait = GsonHelper.convertToJsonObject(element, "trait");
            String modifier = GsonHelper.getAsString(trait, "modifier");
            traits.add(new TraitFormula(
                    new ModifierId(modifier),
                    LinearFormula.parse(GsonHelper.getAsJsonObject(trait, "level"), context + ".traits." + modifier),
                    GsonHelper.getAsInt(trait, "max", Integer.MAX_VALUE)));
        }
        return new StatFormula(java.util.Collections.unmodifiableMap(fields), List.copyOf(traits));
    }

    public record TraitFormula(ModifierId modifier, LinearFormula level, int max) {
        public int compute(Map<String, Double> properties) {
            return (int) Math.min(this.max, Math.floor(this.level.evaluate(properties)));
        }
    }
}
