package org.hp.hptlib.material;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.tools.stat.INumericToolStat;
import slimeknights.tconstruct.library.tools.stat.IToolStat;
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder;
import slimeknights.tconstruct.library.tools.stat.ToolStatId;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record PartFormula(List<StatBonus> stats, List<StatFormula.TraitFormula> traits) {
    private static final Set<String> OPERATIONS = Set.of("base", "add", "percent", "multiply");

    public static PartFormula parse(JsonObject json, String context) {
        List<StatBonus> stats = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "stats", new JsonArray())) {
            JsonObject entry = GsonHelper.convertToJsonObject(element, "stat");
            String name = GsonHelper.getAsString(entry, "stat");
            ResourceLocation id = new ResourceLocation(name.contains(":") ? name : "tconstruct:" + name);
            IToolStat<?> stat = ToolStats.getToolStat(new ToolStatId(id));
            if (!(stat instanceof INumericToolStat<?> numeric)) {
                throw new JsonParseException(context + ": " + id + " is not a numeric Tinkers tool stat");
            }
            String op = GsonHelper.getAsString(entry, "op", "add");
            if (!OPERATIONS.contains(op)) {
                throw new JsonParseException(context + ": unknown op '" + op + "' for " + id + ", allowed: " + OPERATIONS);
            }
            stats.add(new StatBonus(numeric, op, LinearFormula.parse(GsonHelper.getAsJsonObject(entry, "value"), context + "." + id)));
        }
        List<StatFormula.TraitFormula> traits = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "traits", new JsonArray())) {
            JsonObject trait = GsonHelper.convertToJsonObject(element, "trait");
            String modifier = GsonHelper.getAsString(trait, "modifier");
            traits.add(new StatFormula.TraitFormula(
                    new ModifierId(modifier),
                    LinearFormula.parse(GsonHelper.getAsJsonObject(trait, "level"), context + ".traits." + modifier),
                    GsonHelper.getAsInt(trait, "max", Integer.MAX_VALUE)));
        }
        return new PartFormula(List.copyOf(stats), List.copyOf(traits));
    }

    public record StatBonus(INumericToolStat<?> stat, String op, LinearFormula value) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void apply(ModifierStatsBuilder builder, Map<String, Double> properties, float scale) {
            float amount = (float) this.value.evaluate(properties) * scale;
            switch (this.op) {
                case "add" -> this.stat.add(builder, amount);
                case "percent" -> this.stat.percent(builder, amount);
                case "multiply" -> this.stat.multiply(builder, amount);
                default -> ((INumericToolStat) this.stat).update(builder, Float.valueOf(amount));
            }
        }
    }
}
