package org.hp.hptlib.structure;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

/** 保存一个定位眼睛对应的结构标签和显示名称。 */
public record StructureEyeTarget(TagKey<Structure> structureTag, Component displayName) {
}
