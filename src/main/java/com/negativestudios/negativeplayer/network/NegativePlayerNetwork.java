package com.negativestudios.negativeplayer.network;

import com.negativestudios.negativeplayer.NegativePlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class NegativePlayerNetwork {
    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(NegativePlayer.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static boolean registered;

    private NegativePlayerNetwork() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }

        CHANNEL.registerMessage(
                0,
                PlaybackRequestPacket.class,
                PlaybackRequestPacket::encode,
                PlaybackRequestPacket::decode,
                PlaybackRequestPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        registered = true;
    }

    public static void sendToPlayer(ServerPlayer player, PlaybackRequestPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
