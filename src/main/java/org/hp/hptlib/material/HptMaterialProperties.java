package org.hp.hptlib.material;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
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
import slimeknights.tconstruct.library.tools.helper.ModifierBuilder;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.part.IToolPart;
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder;

import java.io.Reader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

public final class HptMaterialProperties {
    public static final String STATS_FOLDER = "tinkering/materials/stats";
    public static final String PROPERTIES_KEY = "properties";
    public static final String FORMULAS_FOLDER = "tinkering/stat_formulas";
    public static final String PART_FORMULAS_FOLDER = "tinkering/part_formulas";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile Map<MaterialId, MaterialProperties> properties = Map.of();
    private static volatile Map<MaterialStatsId, StatFormula> formulas = Map.of();
    private static volatile Map<ResourceLocation, PartFormula> partFormulas = Map.of();
    private static volatile Map<ResourceLocation, JsonObject> rawProperties = Map.of();
    private static volatile Map<ResourceLocation, JsonObject> rawPartFormulas = Map.of();
    private static WeakReference<ResourceManager> loadedFrom = new WeakReference<>(null);

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

    public static PartFormula getPartFormula(ResourceLocation part) {
        return partFormulas.get(part);
    }

    public static Map<ResourceLocation, PartFormula> getPartFormulas() {
        return partFormulas;
    }

    public static Map<ResourceLocation, JsonObject> getRawProperties() {
        return rawProperties;
    }

    public static Map<ResourceLocation, JsonObject> getRawPartFormulas() {
        return rawPartFormulas;
    }

    public static synchronized void acceptSync(Map<ResourceLocation, JsonObject> syncedProperties, Map<ResourceLocation, JsonObject> syncedPartFormulas) {
        Map<MaterialId, MaterialProperties> newProperties = new LinkedHashMap<>();
        syncedProperties.forEach((id, json) -> newProperties.put(new MaterialId(id), MaterialProperties.parse(json)));
        Map<ResourceLocation, PartFormula> newPartFormulas = new LinkedHashMap<>();
        syncedPartFormulas.forEach((id, json) -> {
            try {
                newPartFormulas.put(id, PartFormula.parse(json, "Part formula " + id));
            } catch (RuntimeException e) {
                LOGGER.error("Failed to read synced part formula {}", id, e);
            }
        });
        properties = Collections.unmodifiableMap(newProperties);
        partFormulas = Collections.unmodifiableMap(newPartFormulas);
        rawProperties = Map.copyOf(syncedProperties);
        rawPartFormulas = Map.copyOf(syncedPartFormulas);
        LOGGER.info("Received synced material properties for {} materials and {} part formulas", newProperties.size(), newPartFormulas.size());
    }

    private static synchronized void load(ResourceManager manager) {
        if (loadedFrom.get() == manager) {
            return;
        }
        Map<MaterialId, MaterialProperties> newProperties = new LinkedHashMap<>();
        Map<ResourceLocation, JsonObject> newRawProperties = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, List<Resource>> entry : manager.listResourceStacks(STATS_FOLDER, path -> path.getPath().endsWith(".json")).entrySet()) {
            String path = entry.getKey().getPath();
            ResourceLocation id = new ResourceLocation(entry.getKey().getNamespace(), path.substring(STATS_FOLDER.length() + 1, path.length() - ".json".length()));
            JsonObject merged = new JsonObject();
            boolean found = false;
            for (Resource resource : entry.getValue()) {
                try (Reader reader = resource.openAsReader()) {
                    JsonObject file = GsonHelper.convertToJsonObject(JsonParser.parseReader(reader), id.toString());
                    if (file.has(PROPERTIES_KEY)) {
                        GsonHelper.getAsJsonObject(file, PROPERTIES_KEY).entrySet().forEach(property -> merged.add(property.getKey(), property.getValue()));
                        found = true;
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to load material properties of {} from data pack {}", id, resource.sourcePackId(), e);
                }
            }
            if (!found) {
                continue;
            }
            try {
                newProperties.put(new MaterialId(id), MaterialProperties.parse(merged));
                newRawProperties.put(id, merged);
            } catch (RuntimeException e) {
                LOGGER.error("Failed to parse material properties of {}", id, e);
            }
        }
        Map<MaterialStatsId, StatFormula> newFormulas = new LinkedHashMap<>();
        readFolder(manager, FORMULAS_FOLDER, (id, json) -> newFormulas.put(new MaterialStatsId(id), StatFormula.parse(json, "Stat formula " + id)));
        Map<ResourceLocation, PartFormula> newPartFormulas = new LinkedHashMap<>();
        Map<ResourceLocation, JsonObject> newRawPartFormulas = new LinkedHashMap<>();
        readFolder(manager, PART_FORMULAS_FOLDER, (id, json) -> {
            newPartFormulas.put(id, PartFormula.parse(json, "Part formula " + id));
            newRawPartFormulas.put(id, json);
        });
        properties = Collections.unmodifiableMap(newProperties);
        formulas = Collections.unmodifiableMap(newFormulas);
        partFormulas = Collections.unmodifiableMap(newPartFormulas);
        rawProperties = Collections.unmodifiableMap(newRawProperties);
        rawPartFormulas = Collections.unmodifiableMap(newRawPartFormulas);
        loadedFrom = new WeakReference<>(manager);
        LOGGER.info("Loaded material properties for {} materials, {} stat formulas and {} part formulas", newProperties.size(), newFormulas.size(), newPartFormulas.size());
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
            Set<String> missing = new LinkedHashSet<>(known);
            missing.removeAll(formula.getValue().fields().keySet());
            if (!missing.isEmpty() && properties.values().stream().anyMatch(MaterialProperties::replace)) {
                LOGGER.warn("Stat formula {} does not define fields {}, materials in replace mode will use the stat type defaults for them", formula.getKey(), missing);
            }
        }
        int count = 0;
        for (Map.Entry<MaterialId, MaterialProperties> material : properties.entrySet()) {
            Map<String, Double> values = material.getValue().values();
            for (Map.Entry<MaterialStatsId, StatFormula> formula : formulas.entrySet()) {
                if (material.getValue().exclude().contains(formula.getKey())) {
                    continue;
                }
                Map<ResourceLocation, JsonObject> materialStats = map.computeIfAbsent(material.getKey(), id -> new HashMap<>());
                JsonObject stats = material.getValue().replace() ? new JsonObject() : materialStats.computeIfAbsent(formula.getKey(), id -> new JsonObject());
                materialStats.put(formula.getKey(), stats);
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
                    } else {
                        merged.set(index, new ModifierEntry(entry.getId(), merged.get(index).getLevel() + entry.getLevel()));
                    }
                }
                builder.setTraits(formula.getKey(), List.copyOf(merged));
                count++;
            }
        }
        LOGGER.info("Applied trait formulas to {} material stat entries", count);
    }

    public static void applyPartStats(List<IToolPart> parts, MaterialNBT materials, float[] scales, ModifierStatsBuilder builder) {
        if (partFormulas.isEmpty()) {
            return;
        }
        int max = Math.min(parts.size(), materials.size());
        for (int i = 0; i < max; i++) {
            PartFormula formula = partFormulas.get(BuiltInRegistries.ITEM.getKey(parts.get(i).asItem()));
            if (formula == null) {
                continue;
            }
            MaterialProperties material = properties.get(materials.get(i).getId());
            if (material == null) {
                continue;
            }
            float scale = i < scales.length ? scales[i] : 1;
            for (PartFormula.StatBonus bonus : formula.stats()) {
                bonus.apply(builder, material.values(), scale);
            }
        }
    }

    public static void applyPartTraits(List<IToolPart> parts, MaterialNBT materials, ModifierBuilder builder) {
        if (partFormulas.isEmpty()) {
            return;
        }
        int max = Math.min(parts.size(), materials.size());
        for (int i = 0; i < max; i++) {
            PartFormula formula = partFormulas.get(BuiltInRegistries.ITEM.getKey(parts.get(i).asItem()));
            if (formula == null || formula.traits().isEmpty()) {
                continue;
            }
            MaterialProperties material = properties.get(materials.get(i).getId());
            if (material == null) {
                continue;
            }
            for (StatFormula.TraitFormula trait : formula.traits()) {
                int level = trait.compute(material.values());
                if (level >= 1) {
                    builder.add(new ModifierEntry(trait.modifier(), level));
                }
            }
        }
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
