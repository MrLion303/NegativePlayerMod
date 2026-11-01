package com.negativestudios.negativeplayer.client;

import com.negativestudios.negativeplayer.NegativePlayer;
import com.negativestudios.negativeplayer.network.PlaybackRequestPacket;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Punto de entrada del cliente para las solicitudes de reproducción.
 * El motor multimedia se conectará aquí en la siguiente etapa.
 */
@OnlyIn(Dist.CLIENT)
public final class NegativePlayerClient {
    private NegativePlayerClient() {
    }

    public static void receive(PlaybackRequestPacket packet) {
        NegativePlayer.LOGGER.warn(
                "Se recibió una solicitud de NegativePlayer en el cliente, pero el motor de reproducción "
                        + "todavía no está implementado. fadeIn={} ticks, fadeOut={} ticks, volumen={}.",
                packet.fadeInTicks(),
                packet.fadeOutTicks(),
                packet.volume()
        );
    }
}
