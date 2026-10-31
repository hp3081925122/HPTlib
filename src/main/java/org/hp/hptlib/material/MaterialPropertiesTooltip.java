package org.hp.hptlib.material;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import slimeknights.tconstruct.library.tools.stat.IToolStat;

import java.text.DecimalFormat;
import java.util.List;

public final class MaterialPropertiesTooltip {
    private static final DecimalFormat NUMBER = new DecimalFormat("#.##");

    private MaterialPropertiesTooltip() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(MaterialPropertiesTooltip::onTooltip);
    }

    private static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof IMaterialItem materialItem)) {
            return;
        }
        MaterialVariantId variant = materialItem.getMaterial(stack);
        if (variant == null) {
            return;
        }
        MaterialProperties properties = HptMaterialProperties.get(variant.getId());
        if (properties == null) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        PartFormula formula = HptMaterialProperties.getPartFormula(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if (formula == null) {
            return;
        }
        boolean header = false;
        for (PartFormula.StatBonus bonus : formula.stats()) {
            if (!header) {
                tooltip.add(Component.translatable("tooltip.hptlib.part_bonus").withStyle(ChatFormatting.GOLD));
                header = true;
            }
            float value = (float) bonus.value().evaluate(properties.values());
            String key = bonus.stat().getTranslationKey();
            tooltip.add(switch (bonus.op()) {
                case "add" -> IToolStat.formatColoredBonus(key, value);
                case "percent" -> IToolStat.formatColoredPercentBoost(key, value);
                case "multiply" -> IToolStat.formatColoredMultiplier(key, value);
                default -> Component.translatable(key).append(": " + NUMBER.format(value)).withStyle(ChatFormatting.GRAY);
            });
        }
        for (StatFormula.TraitFormula trait : formula.traits()) {
            int level = trait.compute(properties.values());
            if (level < 1) {
                continue;
            }
            if (!header) {
                tooltip.add(Component.translatable("tooltip.hptlib.part_bonus").withStyle(ChatFormatting.GOLD));
                header = true;
            }
            tooltip.add(new ModifierEntry(trait.modifier(), level).getDisplayName());
        }
    }
}
