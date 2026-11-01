package com.negativestudios.negativeplayer.client;

import com.cinemamod.mcef.MCEF;
import com.negativestudios.negativeplayer.NegativePlayer;
import com.negativestudios.negativeplayer.network.PlaybackControlPacket;
import com.negativestudios.negativeplayer.network.PlaybackRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NegativePlayer.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class NegativePlayerClient {
    private static PlaybackRequestPacket pendingRequest;
    private static long pendingSinceNanos;
    private static long lastWaitLogNanos;

    private NegativePlayerClient() {
    }

    public static void receive(PlaybackRequestPacket packet) {
        Minecraft.getInstance().execute(() -> {
            pendingRequest = packet;
            pendingSinceNanos = System.nanoTime();
            lastWaitLogNanos = 0L;
            NegativePlayer.LOGGER.info("Solicitud de reproducción recibida en el cliente. Preparando Chromium.");
        });
    }

    public static void control(PlaybackControlPacket packet) {
        Minecraft.getInstance().execute(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (packet.action() == PlaybackControlPacket.Action.STOP) {
                pendingRequest = null;
                if (minecraft.screen instanceof NegativePlayerScreen screen) screen.requestStop();
                return;
            }

            if (minecraft.screen instanceof NegativePlayerScreen screen) {
                switch (packet.action()) {
                    case PAUSE -> screen.setPaused(true);
                    case RESUME -> screen.setPaused(false);
                    case SET_VOLUME -> screen.setVolume(packet.volume());
                    default -> { }
                }
            }
        });
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pendingRequest == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        if (MCEF.isInitialized()) {
            PlaybackRequestPacket request = pendingRequest;
            pendingRequest = null;
            try {
                NegativePlayer.LOGGER.info("Chromium está listo. Abriendo pantalla de reproducción.");
                minecraft.setScreen(new NegativePlayerScreen(request));
            } catch (Throwable throwable) {
                NegativePlayer.LOGGER.error("No se pudo iniciar la pantalla de NegativePlayer.", throwable);
            }
            return;
        }

        long now = System.nanoTime();
        if (lastWaitLogNanos == 0L || now - lastWaitLogNanos >= 10_000_000_000L) {
            lastWaitLogNanos = now;
            NegativePlayer.LOGGER.info("NegativePlayer está esperando a que MCEF termine de inicializar Chromium.");
        }
        if (now - pendingSinceNanos >= 180_000_000_000L) {
            NegativePlayer.LOGGER.error("MCEF no se inicializó en 180 segundos. Revisa los errores de MCEF y la conexión para descargar sus componentes nativos.");
            pendingRequest = null;
        }
    }
}
