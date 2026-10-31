package org.hp.hptlib.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.hp.hptlib.material.HptMaterialProperties;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.materials.traits.MaterialTraits;
import slimeknights.tconstruct.library.materials.traits.MaterialTraitsManager;

import java.util.Map;

@Mixin(value = MaterialTraitsManager.class, remap = false)
public abstract class MaterialTraitsManagerMixin {
    @Shadow
    @Final
    private Map<MaterialStatsId, MaterialStatsId> statTypeFallbacks;

    @Inject(method = "finishLoad(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;)V", at = @At("HEAD"))
    private void hptlib$applyTraitFormulas(Map<ResourceLocation, MaterialTraits.Builder> map, ResourceManager manager, CallbackInfo ci) {
        HptMaterialProperties.applyTraits(map, manager, this.statTypeFallbacks);
    }
}
