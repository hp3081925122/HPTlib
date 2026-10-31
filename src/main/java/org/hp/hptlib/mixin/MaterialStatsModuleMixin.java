package org.hp.hptlib.mixin;

import org.hp.hptlib.material.HptMaterialProperties;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.definition.module.material.MaterialStatsModule;
import slimeknights.tconstruct.library.tools.definition.module.material.ToolPartsHook;
import slimeknights.tconstruct.library.tools.helper.ModifierBuilder;
import slimeknights.tconstruct.library.tools.nbt.IToolContext;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder;

@Mixin(value = MaterialStatsModule.class, remap = false)
public abstract class MaterialStatsModuleMixin {
    @Shadow
    @Final
    float[] scales;

    @Inject(method = "addToolStats", at = @At("TAIL"))
    private void hptlib$applyPartStats(IToolContext context, ModifierStatsBuilder builder, CallbackInfo ci) {
        if ((Object) this instanceof ToolPartsHook parts && !context.getMaterials().isEmpty()) {
            HptMaterialProperties.applyPartStats(parts.getParts(context.getDefinition()), context.getMaterials(), this.scales, builder);
        }
    }

    @Inject(method = "addTraits", at = @At("TAIL"))
    private void hptlib$applyPartTraits(ToolDefinition definition, MaterialNBT materials, ModifierBuilder builder, CallbackInfo ci) {
        if ((Object) this instanceof ToolPartsHook parts && !materials.isEmpty()) {
            HptMaterialProperties.applyPartTraits(parts.getParts(definition), materials, builder);
        }
    }
}
