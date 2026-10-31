package org.hp.hptlib;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** HPTlib 提供的跨附属物品标签。 */
public final class HptlibTags {
    /** HPTlib 的物品标签集合。 */
    public static final class Items {
        /** 允许作为结构定位目标的眼睛物品。 */
        public static final TagKey<Item> STRUCTURE_EYES = TagKey.create(
                Registries.ITEM, new ResourceLocation(Hptlib.MOD_ID, "structure_eyes"));

        /** 禁止实例化物品标签集合。 */
        private Items() {
        }
    }

    /** 禁止实例化标签入口。 */
    private HptlibTags() {
    }
}
