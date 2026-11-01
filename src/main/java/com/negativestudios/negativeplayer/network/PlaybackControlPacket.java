package com.negativestudios.negativeplayer.network;

import com.negativestudios.negativeplayer.client.NegativePlayerClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.DecoderException;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PlaybackControlPacket(Action action, int volume) {
    public PlaybackControlPacket {
        if (action == null || (action == Action.SET_VOLUME && (volume < 1 || volume > 100))) {
            throw new IllegalArgumentException("Invalid NegativePlayer control packet");
        }
    }

    public enum Action {
        STOP,
        PAUSE,
        RESUME,
        SET_VOLUME
    }

    public static void encode(PlaybackControlPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.action().ordinal());
        buffer.writeVarInt(packet.volume());
    }

    public static PlaybackControlPacket decode(FriendlyByteBuf buffer) {
        int id = buffer.readVarInt();
        if (id < 0 || id >= Action.values().length) {
            throw new DecoderException("Unknown NegativePlayer control action: " + id);
        }
        return new PlaybackControlPacket(Action.values()[id], buffer.readVarInt());
    }

    public static void handle(PlaybackControlPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> NegativePlayerClient.control(packet)
        ));
        context.setPacketHandled(true);
    }
}
