package org.hp.hptlib;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.hp.hptlib.material.MaterialPropertiesNetwork;
import org.hp.hptlib.material.MaterialPropertiesTooltip;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.StructureTags;
import org.hp.hptlib.structure.StructureEyeModifier;
import org.hp.hptlib.structure.StructureEyeRegistry;
import org.hp.hptlib.structure.StructureEyeModifierRecipe;
import slimeknights.mantle.recipe.helper.LoadableRecipeSerializer;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.modifiers.ModifierManager;

@Mod(Hptlib.MOD_ID)
public final class Hptlib {
    /** HPTlib 的模组命名空间。 */
    public static final String MOD_ID = "hptlib";
    /** HPTlib 提供的通用结构定位词条 ID。 */
    public static final ModifierId STRUCTURE_EYE_MODIFIER = new ModifierId(MOD_ID, "structure_eye");
    /** 注册 HPTlib 的动态结构定位升级配方序列化器。 */
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.RECIPE_SERIALIZERS, MOD_ID);
    /** 结构定位升级配方的序列化器注册对象。 */
    public static final RegistryObject<RecipeSerializer<StructureEyeModifierRecipe>> STRUCTURE_EYE_RECIPE_SERIALIZER =
            RECIPE_SERIALIZERS.register("structure_eye", () -> LoadableRecipeSerializer.of(
                    StructureEyeModifierRecipe.LOADER));

    /** 初始化 HPTlib 的通用匠魂词条和配方注册。 */
    public Hptlib(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        RECIPE_SERIALIZERS.register(modEventBus);
        modEventBus.addListener(this::registerModifiers);
        MaterialPropertiesNetwork.register();
        StructureEyeRegistry.register(new ResourceLocation("minecraft", "ender_eye"), StructureTags.EYE_OF_ENDER_LOCATED, "structure_eye.hptlib.target.ender_eye");
        if (FMLEnvironment.dist == Dist.CLIENT) {
            MaterialPropertiesTooltip.register();
        }
    }

    /** 注册所有由 HPTlib 自身提供的静态匠魂词条。 */
    private void registerModifiers(ModifierManager.ModifierRegistrationEvent event) {
        event.registerStatic(STRUCTURE_EYE_MODIFIER, new StructureEyeModifier());
    }
}
