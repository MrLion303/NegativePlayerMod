package com.negativestudios.negativeplayer.network;

import com.negativestudios.negativeplayer.client.NegativePlayerClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PlaybackRequestPacket(
        String url,
        int fadeInTicks,
        int fadeOutTicks,
        int volume,
        long startAtGameTime
) {
    public PlaybackRequestPacket {
        if (url == null || url.length() > 8_192) {
            throw new IllegalArgumentException("Invalid video URL payload");
        }
        if (fadeInTicks < 0 || fadeInTicks > 1_200
                || fadeOutTicks < 0 || fadeOutTicks > 1_200
                || volume < 1 || volume > 100) {
            throw new IllegalArgumentException("Invalid playback settings");
        }
    }

    public static void encode(PlaybackRequestPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUtf(packet.url(), 8_192);
        buffer.writeVarInt(packet.fadeInTicks());
        buffer.writeVarInt(packet.fadeOutTicks());
        buffer.writeVarInt(packet.volume());
        buffer.writeLong(packet.startAtGameTime());
    }

    public static PlaybackRequestPacket decode(FriendlyByteBuf buffer) {
        return new PlaybackRequestPacket(
                buffer.readUtf(8_192),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readLong()
        );
    }

    public static void handle(PlaybackRequestPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> NegativePlayerClient.receive(packet)
        ));
        context.setPacketHandled(true);
    }
}
