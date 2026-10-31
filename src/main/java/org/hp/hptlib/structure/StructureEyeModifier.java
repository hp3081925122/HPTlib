package org.hp.hptlib.structure;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import slimeknights.tconstruct.common.TinkerTags;
import slimeknights.tconstruct.library.modifiers.ModifierEntry;
import slimeknights.tconstruct.library.modifiers.ModifierHooks;
import slimeknights.tconstruct.library.modifiers.hook.interaction.GeneralInteractionModifierHook;
import slimeknights.tconstruct.library.modifiers.hook.interaction.InteractionSource;
import slimeknights.tconstruct.library.modifiers.impl.NoLevelsModifier;
import slimeknights.tconstruct.library.module.ModuleHookMap;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

import java.util.ArrayList;
import java.util.List;

/** HPTlib 通用的结构定位与传送匠魂能力。 */
public class StructureEyeModifier extends NoLevelsModifier implements GeneralInteractionModifierHook {
    /** 结构定位的搜索半径。 */
    private static final int SEARCH_RADIUS = 100;
    /** 防止连续右键重复传送。 */
    private static final int COOLDOWN_TICKS = 60;

    /** 将词条接入匠魂通用右键交互钩子。 */
    @Override
    protected void registerHooks(ModuleHookMap.Builder hookBuilder) {
        hookBuilder.addHook(this, ModifierHooks.GENERAL_INTERACT);
    }

    /** 右键手杖时在服务端定位并传送到当前绑定的结构。 */
    @Override
    public InteractionResult onToolUse(IToolStackView tool, ModifierEntry modifier, Player player,
                                       InteractionHand hand, InteractionSource source) {
        if (source != InteractionSource.RIGHT_CLICK || tool.isBroken()
                || !tool.hasTag(TinkerTags.Items.STAFFS)) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.CONSUME;
        }
        if (serverPlayer.getCooldowns().isOnCooldown(tool.getItem())) {
            return InteractionResult.CONSUME;
        }

        StructureEyeTarget target = getTarget(tool, modifier);
        if (target == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.hptlib.structure_eye.no_target"), true);
            return InteractionResult.CONSUME;
        }

        ServerLevel serverLevel = serverPlayer.serverLevel();
        BlockPos located = serverLevel.findNearestMapStructure(
                target.structureTag(), serverPlayer.blockPosition(), SEARCH_RADIUS, false);
        if (located == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.hptlib.structure_eye.not_found", target.displayName()), true);
            return InteractionResult.CONSUME;
        }

        BlockPos destination = findTeleportPosition(serverLevel, located);
        if (destination == null) {
            serverPlayer.displayClientMessage(
                    Component.translatable("message.hptlib.structure_eye.no_safe_position", target.displayName()), true);
            return InteractionResult.CONSUME;
        }

        serverPlayer.teleportTo(serverLevel, destination.getX() + 0.5D, destination.getY(),
                destination.getZ() + 0.5D, serverPlayer.getYRot(), serverPlayer.getXRot());
        serverLevel.playSound(null, destination.getX() + 0.5D, destination.getY(), destination.getZ() + 0.5D,
                SoundEvents.ENDERMAN_TELEPORT, serverPlayer.getSoundSource(), 1.0F, 1.0F);
        serverPlayer.getCooldowns().addCooldown(tool.getItem(), COOLDOWN_TICKS);
        return InteractionResult.CONSUME;
    }

    /** 在结构定位点附近寻找脚下有方块且头脚均为空的安全落点。 */
    private static BlockPos findTeleportPosition(ServerLevel level, BlockPos structurePos) {
        int minY = level.getMinBuildHeight() + 1;
        int maxY = level.getMaxBuildHeight() - 2;
        int centerY = Math.max(minY, Math.min(maxY, structurePos.getY()));

        for (int radius = 0; radius <= 6; radius++) {
            for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                for (int offsetZ = -radius; offsetZ <= radius; offsetZ++) {
                    if (Math.max(Math.abs(offsetX), Math.abs(offsetZ)) != radius) {
                        continue;
                    }
                    int x = structurePos.getX() + offsetX;
                    int z = structurePos.getZ() + offsetZ;
                    for (int verticalOffset = 0; verticalOffset <= 80; verticalOffset++) {
                        int upperY = centerY + verticalOffset;
                        if (upperY <= maxY) {
                            BlockPos candidate = new BlockPos(x, upperY, z);
                            if (isSafeDestination(level, candidate)) {
                                return candidate;
                            }
                        }
                        if (verticalOffset > 0) {
                            int lowerY = centerY - verticalOffset;
                            if (lowerY >= minY) {
                                BlockPos candidate = new BlockPos(x, lowerY, z);
                                if (isSafeDestination(level, candidate)) {
                                    return candidate;
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /** 判断落点脚下是否坚实、脚部和头部是否没有方块或流体。 */
    private static boolean isSafeDestination(ServerLevel level, BlockPos position) {
        BlockState feet = level.getBlockState(position);
        BlockState head = level.getBlockState(position.above());
        BlockPos floorPos = position.below();
        BlockState floor = level.getBlockState(floorPos);
        return feet.isAir() && head.isAir()
                && level.getFluidState(position).isEmpty()
                && level.getFluidState(position.above()).isEmpty()
                && floor.isFaceSturdy(level, floorPos, Direction.UP);
    }

    /** 从可切换升级写入的持久化值查询结构定位目标。 */
    private static StructureEyeTarget getTarget(IToolStackView tool, ModifierEntry modifier) {
        String eyeValue = tool.getPersistentData().getString(modifier.getId());
        ResourceLocation eyeItem = ResourceLocation.tryParse(eyeValue);
        return eyeItem == null ? null : StructureEyeRegistry.get(eyeItem);
    }

    /** 在词条名称后显示当前绑定的定位眼睛。 */
    @Override
    public Component getDisplayName(IToolStackView tool, ModifierEntry modifier, RegistryAccess registryAccess) {
        Component displayName = super.getDisplayName(tool, modifier, registryAccess);
        StructureEyeTarget target = getTarget(tool, modifier);
        if (target == null) {
            return displayName;
        }
        return displayName.copy().append(Component.literal(" "))
                .append(Component.translatable("modifier.hptlib.structure_eye.bound", target.displayName())
                        .withStyle(ChatFormatting.GRAY));
    }

    /** 在工具提示中显示当前手杖绑定的定位眼睛。 */
    @Override
    public List<Component> getDescriptionList(IToolStackView tool, ModifierEntry modifier) {
        List<Component> descriptions = new ArrayList<>(super.getDescriptionList(tool, modifier));
        StructureEyeTarget target = getTarget(tool, modifier);
        if (target != null) {
            descriptions.add(Component.translatable("modifier.hptlib.structure_eye.selected", target.displayName()));
        }
        return descriptions;
    }
}
