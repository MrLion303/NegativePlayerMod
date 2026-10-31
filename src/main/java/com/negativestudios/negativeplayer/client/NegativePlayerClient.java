package com.negativestudios.negativeplayer.client;

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
        // Intencionalmente silencioso: todavía no existe un backend de reproducción.
        // No se muestra ningún mensaje de estado en el chat ni en la pantalla.
    }
}
