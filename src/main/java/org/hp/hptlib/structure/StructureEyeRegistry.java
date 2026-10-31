package org.hp.hptlib.structure;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/** 由各附属注册定位眼睛与目标结构的对应关系。 */
public final class StructureEyeRegistry {
    /** 保持注册顺序，便于匠魂配方界面稳定展示各附属提供的眼睛。 */
    private static final Map<ResourceLocation, StructureEyeTarget> TARGETS = new LinkedHashMap<>();

    /** 注册一只眼睛对应的结构标签和本地化显示名称。 */
    public static void register(ResourceLocation eyeItem, TagKey<Structure> structureTag, String displayKey) {
        if (TARGETS.containsKey(eyeItem)) {
            throw new IllegalArgumentException("Duplicate structure eye registration: " + eyeItem);
        }
        TARGETS.put(eyeItem, new StructureEyeTarget(structureTag, Component.translatable(displayKey)));
    }

    /** 根据眼睛物品 ID 查询结构定位目标。 */
    @Nullable
    public static StructureEyeTarget get(ResourceLocation eyeItem) {
        return TARGETS.get(eyeItem);
    }

    /** 判断某个眼睛物品是否已经注册结构定位目标。 */
    public static boolean contains(ResourceLocation eyeItem) {
        return TARGETS.containsKey(eyeItem);
    }

    /** 禁止实例化注册表。 */
    private StructureEyeRegistry() {
    }
}
