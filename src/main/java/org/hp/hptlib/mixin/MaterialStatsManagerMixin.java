package org.hp.hptlib.mixin;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.hp.hptlib.material.HptMaterialProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsManager;

import java.util.Map;

@Mixin(value = MaterialStatsManager.class, remap = false)
public abstract class MaterialStatsManagerMixin {
    @Inject(method = "finishLoad(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;)V", at = @At("HEAD"))
    private void hptlib$applyStatFormulas(Map<ResourceLocation, Map<ResourceLocation, JsonObject>> map, ResourceManager manager, CallbackInfo ci) {
        MaterialStatsManager self = (MaterialStatsManager) (Object) this;
        HptMaterialProperties.applyStats(map, manager, self::getStatType);
    }
}
