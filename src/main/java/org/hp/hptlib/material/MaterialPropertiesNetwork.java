package org.hp.hptlib.material;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.hp.hptlib.Hptlib;

import java.util.Map;

public final class MaterialPropertiesNetwork {
    private static final String VERSION = "1";
    private static final int MAX_JSON = 1 << 20;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Hptlib.MOD_ID, "material_properties"), () -> VERSION, VERSION::equals, VERSION::equals);

    private MaterialPropertiesNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(SyncPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncPacket::encode)
                .decoder(SyncPacket::decode)
                .consumerMainThread((packet, context) -> HptMaterialProperties.acceptSync(packet.properties(), packet.partFormulas()))
                .add();
        MinecraftForge.EVENT_BUS.addListener(MaterialPropertiesNetwork::onDatapackSync);
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        SyncPacket packet = new SyncPacket(HptMaterialProperties.getRawProperties(), HptMaterialProperties.getRawPartFormulas());
        if (event.getPlayer() == null) {
            CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
        } else {
            CHANNEL.send(PacketDistributor.PLAYER.with(event::getPlayer), packet);
        }
    }

    public record SyncPacket(Map<ResourceLocation, JsonObject> properties, Map<ResourceLocation, JsonObject> partFormulas) {
        private void encode(FriendlyByteBuf buffer) {
            write(buffer, this.properties);
            write(buffer, this.partFormulas);
        }

        private static SyncPacket decode(FriendlyByteBuf buffer) {
            return new SyncPacket(read(buffer), read(buffer));
        }

        private static void write(FriendlyByteBuf buffer, Map<ResourceLocation, JsonObject> map) {
            buffer.writeMap(map, FriendlyByteBuf::writeResourceLocation, (buf, json) -> buf.writeUtf(json.toString(), MAX_JSON));
        }

        private static Map<ResourceLocation, JsonObject> read(FriendlyByteBuf buffer) {
            return buffer.readMap(FriendlyByteBuf::readResourceLocation, buf -> JsonParser.parseString(buf.readUtf(MAX_JSON)).getAsJsonObject());
        }
    }
}
