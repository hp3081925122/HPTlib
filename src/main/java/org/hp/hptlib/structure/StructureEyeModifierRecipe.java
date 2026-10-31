package org.hp.hptlib.structure;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import slimeknights.mantle.data.loadable.field.ContextKey;
import slimeknights.mantle.data.loadable.field.RecordField;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.mantle.recipe.ingredient.SizedIngredient;
import slimeknights.tconstruct.library.json.IntRange;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierId;
import slimeknights.tconstruct.library.recipe.RecipeResult;
import slimeknights.tconstruct.library.recipe.modifiers.adding.IDisplayModifierRecipe;
import slimeknights.tconstruct.library.recipe.modifiers.adding.ModifierRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.nbt.LazyToolStack;
import slimeknights.tconstruct.library.tools.nbt.ToolDataNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

import java.util.ArrayList;
import java.util.List;

/** 使用结构定位眼睛标签并自动写入眼睛 ID 的匠魂可切换升级配方。 */
public class StructureEyeModifierRecipe extends ModifierRecipe {
    /** 使用匠魂通用字段读取结构定位升级配方。 */
    public static final RecordLoadable<StructureEyeModifierRecipe> LOADER = RecordLoadable.create(
            (RecordField) ContextKey.ID.requiredField(),
            (RecordField) INPUTS_FIELD,
            (RecordField) TOOLS_FIELD,
            (RecordField) MAX_TOOL_SIZE_FIELD,
            (RecordField) RESULT_FIELD,
            (RecordField) LEVEL_FIELD,
            (RecordField) SLOTS_FIELD,
            (RecordField) ALLOW_CRYSTAL_FIELD,
            (RecordField) CHECK_TRAIT_LEVEL_FIELD,
            StructureEyeModifierRecipe::new);

    /** 创建结构定位升级配方实例。 */
    public StructureEyeModifierRecipe(ResourceLocation id, List<SizedIngredient> inputs, Ingredient toolRequirement,
                                      int maxToolSize, ModifierId result, IntRange level,
                                      SlotType.SlotCount slots, boolean allowCrystal, boolean checkTraitLevel) {
        super(id, inputs, toolRequirement, maxToolSize, result, level, slots, allowCrystal, checkTraitLevel);
    }

    /** 根据匠魂工作台中实际放入的眼睛写入对应的绑定值。 */
    @Override
    public RecipeResult<LazyToolStack> getValidatedResult(ITinkerStationContainer inv, RegistryAccess access) {
        ResourceLocation eyeItem = findEyeItem(inv);
        if (eyeItem == null) {
            return RecipeResult.failure(Component.translatable("recipe.hptlib.structure_eye.invalid_eye"));
        }

        StructureEyeTarget target = StructureEyeRegistry.get(eyeItem);
        if (target == null) {
            return RecipeResult.failure(Component.translatable("recipe.hptlib.structure_eye.invalid_eye"));
        }

        ToolStack tool = inv.getTinkerable();
        ModifierId modifier = this.result.getId();
        int level = tool.getUpgrades().getLevel(modifier);
        boolean needsModifier = level == 0;
        if (needsModifier) {
            Component commonError = this.validatePrerequisites(tool);
            if (commonError != null) {
                return RecipeResult.failure(commonError);
            }
        }
        if (level > 0 && tool.getPersistentData().getString(modifier).equals(eyeItem.toString())) {
            return RecipeResult.failure(Component.translatable("recipe.hptlib.structure_eye.already_bound",
                    this.result.get().getDisplayName(), target.displayName()));
        }

        tool = tool.copy();
        ToolDataNBT persistentData = tool.getPersistentData();
        if (needsModifier && this.getSlots() != null) {
            persistentData.addSlots(this.getSlots().type(), -this.getSlots().count());
        }
        persistentData.putString(modifier, eyeItem.toString());
        if (needsModifier) {
            tool.addModifier(modifier, 1);
        } else {
            tool.rebuildStats();
        }
        Component toolValidation = tool.tryValidate();
        if (toolValidation != null) {
            return RecipeResult.failure(toolValidation);
        }
        return this.success(tool, inv);
    }

    /** 将标签中的每只眼睛写入展示用工具，令 JEI 和匠魂界面展示完整绑定结果。 */
    @Override
    public List<ItemStack> getToolWithModifier() {
        List<ItemStack> resultStacks = new ArrayList<>();
        ModifierEntry displayResult = this.getDisplayResult();
        for (ItemStack eye : this.getDisplayItems(0)) {
            ResourceLocation eyeItem = BuiltInRegistries.ITEM.getKey(eye.getItem());
            if (!StructureEyeRegistry.contains(eyeItem)) {
                continue;
            }
            for (ItemStack tool : this.getToolInputs()) {
                resultStacks.add(IDisplayModifierRecipe.withModifiers(tool, this.maxToolSize,
                        IDisplayModifierRecipe.modifiersForResult(displayResult, displayResult),
                        data -> data.putString(displayResult.getId(), eyeItem.toString())));
            }
        }
        return resultStacks;
    }

    /** 从匠魂工作台输入中找到符合 HPTlib 定位眼睛标签且已注册的眼睛。 */
    private static ResourceLocation findEyeItem(ITinkerStationContainer inv) {
        for (int i = 0; i < inv.getInputCount(); i++) {
            ItemStack stack = inv.getInput(i);
            if (!stack.isEmpty() && stack.is(org.hp.hptlib.HptlibTags.Items.STRUCTURE_EYES)) {
                ResourceLocation eyeItem = BuiltInRegistries.ITEM.getKey(stack.getItem());
                if (StructureEyeRegistry.contains(eyeItem)) {
                    return eyeItem;
                }
            }
        }
        return null;
    }

    /** 返回 HPTlib 注册的结构定位升级配方序列化器。 */
    @Override
    public RecipeSerializer<?> getSerializer() {
        return org.hp.hptlib.Hptlib.STRUCTURE_EYE_RECIPE_SERIALIZER.get();
    }
}
