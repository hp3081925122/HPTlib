package org.hp.hptlib.material;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import org.hp.hptlib.mixin.MaterialTraitsBuilderAccessor;
import org.slf4j.Logger;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.materials.stats.IMaterialStats;
import slimeknights.tconstruct.library.materials.stats.MaterialStatType;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.materials.traits.MaterialTraits;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class HptMaterialProperties {
    public static final String PROPERTIES_FOLDER = "tinkering/material_properties";
    public static final String FORMULAS_FOLDER = "tinkering/stat_formulas";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Map<MaterialId, MaterialProperties> properties = Map.of();
    private static volatile Map<MaterialStatsId, StatFormula> formulas = Map.of();
    private static ResourceManager loadedFrom;

    private HptMaterialProperties() {}

    public static MaterialProperties get(MaterialId material) {
        return properties.get(material);
    }

    public static Map<MaterialId, MaterialProperties> getAll() {
        return properties;
    }

    public static StatFormula getFormula(MaterialStatsId statType) {
        return formulas.get(statType);
    }

    public static Map<MaterialStatsId, StatFormula> getFormulas() {
        return formulas;
    }

    private static synchronized void load(ResourceManager manager) {
        if (loadedFrom == manager) {
            return;
        }
        Map<MaterialId, MaterialProperties> newProperties = new LinkedHashMap<>();
        readFolder(manager, PROPERTIES_FOLDER, (id, json) -> newProperties.put(new MaterialId(id), MaterialProperties.parse(json)));
        Map<MaterialStatsId, StatFormula> newFormulas = new LinkedHashMap<>();
        readFolder(manager, FORMULAS_FOLDER, (id, json) -> newFormulas.put(new MaterialStatsId(id), StatFormula.parse(json, "Stat formula " + id)));
        properties = Collections.unmodifiableMap(newProperties);
        formulas = Collections.unmodifiableMap(newFormulas);
        loadedFrom = manager;
        LOGGER.info("Loaded material properties for {} materials and {} stat formulas", newProperties.size(), newFormulas.size());
    }

    private static void readFolder(ResourceManager manager, String folder, BiConsumer<ResourceLocation, JsonObject> consumer) {
        for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources(folder, path -> path.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation file = entry.getKey();
            String path = file.getPath();
            ResourceLocation id = new ResourceLocation(file.getNamespace(), path.substring(folder.length() + 1, path.length() - ".json".length()));
            try (Reader reader = entry.getValue().openAsReader()) {
                consumer.accept(id, GsonHelper.convertToJsonObject(JsonParser.parseReader(reader), id.toString()));
            } catch (Exception e) {
                LOGGER.error("Failed to load {} from data pack {}", file, entry.getValue().sourcePackId(), e);
            }
        }
    }

    public static void applyStats(Map<ResourceLocation, Map<ResourceLocation, JsonObject>> map, ResourceManager manager, Function<MaterialStatsId, MaterialStatType<?>> statTypes) {
        load(manager);
        if (properties.isEmpty() || formulas.isEmpty()) {
            return;
        }
        for (Map.Entry<MaterialStatsId, StatFormula> formula : formulas.entrySet()) {
            MaterialStatType<?> type = statTypes.apply(formula.getKey());
            if (type == null) {
                LOGGER.warn("Stat formula {} targets an unregistered material stat type, it will be skipped by Tinkers", formula.getKey());
                continue;
            }
            Set<String> known = knownFields(type);
            for (String field : formula.getValue().fields().keySet()) {
                if (!known.contains(field)) {
                    LOGGER.warn("Stat formula {} writes field '{}', which is not a default field of this stat type (known: {})", formula.getKey(), field, known);
                }
            }
        }
        int count = 0;
        for (Map.Entry<MaterialId, MaterialProperties> material : properties.entrySet()) {
            Map<String, Double> values = material.getValue().values();
            for (Map.Entry<MaterialStatsId, StatFormula> formula : formulas.entrySet()) {
                if (material.getValue().exclude().contains(formula.getKey())) {
                    continue;
                }
                JsonObject stats = map.computeIfAbsent(material.getKey(), id -> new HashMap<>()).computeIfAbsent(formula.getKey(), id -> new JsonObject());
                for (Map.Entry<String, FieldFormula> field : formula.getValue().fields().entrySet()) {
                    if (!stats.has(field.getKey())) {
                        stats.add(field.getKey(), field.getValue().compute(values));
                    }
                }
                count++;
            }
        }
        LOGGER.info("Applied stat formulas to {} material stat entries", count);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Set<String> knownFields(MaterialStatType type) {
        try {
            JsonObject json = new JsonObject();
            type.getLoadable().serialize((IMaterialStats) type.getDefaultStats(), json);
            return json.keySet();
        } catch (RuntimeException e) {
            return Set.of();
        }
    }

    public static void applyTraits(Map<ResourceLocation, MaterialTraits.Builder> map, ResourceManager manager, Map<MaterialStatsId, MaterialStatsId> fallbacks) {
        load(manager);
        if (properties.isEmpty() || formulas.isEmpty()) {
            return;
        }
        int count = 0;
        for (Map.Entry<MaterialId, MaterialProperties> material : properties.entrySet()) {
            Map<String, Double> values = material.getValue().values();
            for (Map.Entry<MaterialStatsId, StatFormula> formula : formulas.entrySet()) {
                if (formula.getValue().traits().isEmpty() || material.getValue().exclude().contains(formula.getKey())) {
                    continue;
                }
                List<ModifierEntry> added = new ArrayList<>();
                for (StatFormula.TraitFormula trait : formula.getValue().traits()) {
                    int level = trait.compute(values);
                    if (level >= 1) {
                        added.add(new ModifierEntry(trait.modifier(), level));
                    }
                }
                if (added.isEmpty()) {
                    continue;
                }
                MaterialTraits.Builder builder = map.computeIfAbsent(material.getKey(), id -> new MaterialTraits.Builder());
                MaterialTraitsBuilderAccessor accessor = (MaterialTraitsBuilderAccessor) builder;
                List<ModifierEntry> base = accessor.hptlib$getTraitsPerStats().get(formula.getKey());
                if (base == null && fallbacks.containsKey(formula.getKey())) {
                    base = accessor.hptlib$getTraitsPerStats().get(fallbacks.get(formula.getKey()));
                }
                if (base == null) {
                    base = accessor.hptlib$getDefaultTraits();
                }
                List<ModifierEntry> merged = new ArrayList<>(base == null ? List.of() : base);
                for (ModifierEntry entry : added) {
                    int index = indexOf(merged, entry);
                    if (index < 0) {
                        merged.add(entry);
                    } else if (merged.get(index).getLevel() < entry.getLevel()) {
                        merged.set(index, entry);
                    }
                }
                builder.setTraits(formula.getKey(), List.copyOf(merged));
                count++;
            }
        }
        LOGGER.info("Applied trait formulas to {} material stat entries", count);
    }

    private static int indexOf(List<ModifierEntry> entries, ModifierEntry entry) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getId().equals(entry.getId())) {
                return i;
            }
        }
        return -1;
    }
}
