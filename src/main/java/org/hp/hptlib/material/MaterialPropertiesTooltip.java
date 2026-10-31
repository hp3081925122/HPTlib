package org.hp.hptlib.material;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import slimeknights.mantle.client.SafeClientAccess;
import slimeknights.mantle.client.TooltipKey;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import slimeknights.tconstruct.library.tools.part.IToolPart;
import slimeknights.tconstruct.library.tools.stat.IToolStat;

import java.text.DecimalFormat;
import java.util.List;

public final class MaterialPropertiesTooltip {
    private static final DecimalFormat NUMBER = new DecimalFormat("#.##");
    private static final DecimalFormat VALUE = new DecimalFormat("#,##0.##");
    private static final DecimalFormat PERCENT = new DecimalFormat("+#.##%;-#.##%");

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
        PartFormula formula = HptMaterialProperties.getPartFormula(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        if (formula == null) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        TooltipKey key = SafeClientAccess.getTooltipKey();
        int split = tooltip.size();
        for (int i = 1; i < tooltip.size(); i++) {
            if (tooltip.get(i).getString().isEmpty()) {
                split = i;
                break;
            }
        }
        List<ModifierEntry> materialTraits = stack.getItem() instanceof IToolPart part ? MaterialRegistry.getInstance().getTraits(variant.getId(), part.getStatType()) : List.of();
        for (StatFormula.TraitFormula trait : formula.traits()) {
            int level = trait.compute(properties.values());
            if (level < 1) {
                continue;
            }
            int index = -1;
            for (ModifierEntry entry : materialTraits) {
                if (entry.isBound() && entry.getId().equals(trait.modifier())) {
                    index = tooltip.subList(0, split).indexOf(entry.getDisplayName());
                    if (index >= 0) {
                        level += entry.getLevel();
                    }
                    break;
                }
            }
            ModifierEntry merged = new ModifierEntry(trait.modifier(), level);
            if (index >= 0) {
                tooltip.set(index, merged.getDisplayName());
                continue;
            }
            tooltip.add(split++, merged.getDisplayName());
            if (key == TooltipKey.CONTROL) {
                for (Component line : merged.getModifier().getDescriptionList(merged.getLevel())) {
                    tooltip.add(split++, line.copy().withStyle(ChatFormatting.GRAY));
                }
            }
        }
        if (!key.isShiftOrUnknown()) {
            return;
        }
        boolean advanced = event.getFlags().isAdvanced();
        for (PartFormula.StatBonus bonus : formula.stats()) {
            float value = (float) bonus.value().evaluate(properties.values());
            String statKey = bonus.stat().getTranslationKey();
            Component formatted = switch (bonus.op()) {
                case "add" -> IToolStat.formatColoredBonus(statKey, value);
                case "percent" -> IToolStat.formatColoredPercentBoost(statKey, value);
                case "multiply" -> IToolStat.formatColoredMultiplier(statKey, value);
                default -> Component.translatable(statKey).append(NUMBER.format(value)).withStyle(ChatFormatting.GRAY);
            };
            List<Component> bonusParts = formatted.getSiblings();
            Component bonusValue = bonusParts.isEmpty() ? Component.literal(NUMBER.format(value)) : bonusParts.get(bonusParts.size() - 1);
            int index = -1;
            for (int i = split; i < tooltip.size(); i++) {
                if (tooltip.get(i).getContents() instanceof TranslatableContents contents && contents.getKey().equals(statKey)) {
                    index = i;
                    break;
                }
            }
            if (index < 0 && !advanced) {
                tooltip.add(formatted);
                continue;
            }
            if (index < 0) {
                tooltip.add(Component.translatable(statKey)
                    .append(Component.literal("(").withStyle(ChatFormatting.GRAY))
                    .append(bonusValue)
                    .append(Component.literal(")").withStyle(ChatFormatting.GRAY)));
                continue;
            }
            Component line = tooltip.get(index);
            List<Component> lineParts = line.getSiblings();
            Component merged = advanced || lineParts.isEmpty() ? null : mergeValue(lineParts.get(lineParts.size() - 1), bonus.op(), value);
            if (merged == null) {
                tooltip.set(index, line.copy()
                    .append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
                    .append(bonusValue)
                    .append(Component.literal(")").withStyle(ChatFormatting.GRAY)));
                continue;
            }
            MutableComponent replaced = line.plainCopy().withStyle(line.getStyle());
            for (int i = 0; i < lineParts.size() - 1; i++) {
                replaced.append(lineParts.get(i));
            }
            tooltip.set(index, replaced.append(merged));
        }
    }

    private static Component mergeValue(Component original, String op, float bonus) {
        String text = original.getString().trim();
        boolean percent = text.endsWith("%");
        double number;
        try {
            number = Double.parseDouble(text.replace(",", "").replace("%", "").replace("+", ""));
        } catch (NumberFormatException e) {
            return null;
        }
        if (percent) {
            if (!op.equals("percent")) {
                return null;
            }
            return Component.literal(PERCENT.format(number / 100 + bonus)).withStyle(original.getStyle());
        }
        double result = switch (op) {
            case "add" -> number + bonus;
            case "percent" -> number * (1 + bonus);
            case "multiply" -> number * bonus;
            default -> Double.NaN;
        };
        if (Double.isNaN(result)) {
            return null;
        }
        return Component.literal(VALUE.format(result)).withStyle(original.getStyle());
    }
}
