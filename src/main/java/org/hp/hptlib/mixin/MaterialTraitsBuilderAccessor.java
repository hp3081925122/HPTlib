package org.hp.hptlib.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.materials.traits.MaterialTraits;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;

import java.util.List;
import java.util.Map;

@Mixin(value = MaterialTraits.Builder.class, remap = false)
public interface MaterialTraitsBuilderAccessor {
    @Accessor("defaultTraits")
    List<ModifierEntry> hptlib$getDefaultTraits();

    @Accessor("traitsPerStats")
    Map<MaterialStatsId, List<ModifierEntry>> hptlib$getTraitsPerStats();
}
