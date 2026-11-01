package com.negativestudios.negativeplayer.client;

import de.keksuccino.rinku.Rinku;
import de.keksuccino.rinku.RinkuBrowser;
import com.negativestudios.negativeplayer.NegativePlayer;
import com.negativestudios.negativeplayer.network.PlaybackRequestPacket;
import com.negativestudios.negativeplayer.util.VideoUrlValidator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import org.cef.browser.CefFrame;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Vector;

public final class NegativePlayerScreen extends Screen {
    private enum PlaybackState { WAITING, PLAYING, STOPPING }

    private final PlaybackRequestPacket request;
    private final Minecraft minecraft = Minecraft.getInstance();

    private RinkuBrowser browser;
    private PlaybackState state = PlaybackState.WAITING;
    private int volume;
    private int ticks;
    private boolean startRequested;
    private boolean paused;
    private boolean sawPlaying;
    private boolean playbackVisible;
    private boolean closeIssued;
    private boolean worldAudioMuted;
    private boolean loggedTextureReady;
    private float previousMasterVolume = -1.0F;
    private long fadeInStartedAt;
    private long stopStartedAt;
    private long startRequestedAt;
    private long browserCreatedAt;

    public NegativePlayerScreen(PlaybackRequestPacket request) {
        super(Component.literal("NegativePlayer"));
        this.request = request;
        this.volume = request.volume();
    }

    @Override
    protected void init() {
        super.init();
        muteMinecraftAudio();

        try {
            String playerUrl = LocalPlayerHttpServer.start(request.url());
            browser = Rinku.createBrowser(playerUrl, false);
            browser.useBrowserControls(false);
            browser.setWindowlessFrameRate(60);
            browser.setAudioMuted(false);
            browserCreatedAt = System.nanoTime();
            resizeBrowser();

            NegativePlayer.LOGGER.info("Navegador de NegativePlayer creado para el proveedor {}.",
                    VideoUrlValidator.detectProvider(request.url()));
        } catch (Throwable throwable) {
            NegativePlayer.LOGGER.error("No se pudo crear el navegador de NegativePlayer.", throwable);
            requestStop();
        }
    }

    private void muteMinecraftAudio() {
        if (worldAudioMuted) return;
        try {
            previousMasterVolume = minecraft.options.getSoundSourceVolume(SoundSource.MASTER);
            minecraft.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0D);
            worldAudioMuted = true;
            NegativePlayer.LOGGER.info("Audio de Minecraft silenciado durante la reproducción.");
        } catch (Throwable throwable) {
            NegativePlayer.LOGGER.warn("No se pudo silenciar temporalmente el audio de Minecraft.", throwable);
        }
    }

    private void restoreMinecraftAudio() {
        if (!worldAudioMuted) return;
        try {
            minecraft.options.getSoundSourceOptionInstance(SoundSource.MASTER).set((double) previousMasterVolume);
            NegativePlayer.LOGGER.info("Audio de Minecraft restaurado.");
        } catch (Throwable throwable) {
            NegativePlayer.LOGGER.warn("No se pudo restaurar el volumen de Minecraft.", throwable);
        } finally {
            worldAudioMuted = false;
            previousMasterVolume = -1.0F;
        }
    }

    private void resizeBrowser() {
        if (browser == null) return;
        double scale = minecraft.getWindow().getGuiScale();
        browser.resize(
                Math.max(1, (int) Math.round(width * scale)),
                Math.max(1, (int) Math.round(height * scale))
        );
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        resizeBrowser();
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;

        if (browser != null) {
            if (!loggedTextureReady && browser.isTextureReady()) {
                loggedTextureReady = true;
                NegativePlayer.LOGGER.info("Textura de Chromium lista: {}x{}.",
                        browser.getRenderer().getTextureWidth(),
                        browser.getRenderer().getTextureHeight());
            } else if (!loggedTextureReady && browserCreatedAt > 0L
                    && System.nanoTime() - browserCreatedAt > 15_000_000_000L) {
                NegativePlayer.LOGGER.warn("Chromium todavía no ha entregado una textura para NegativePlayer.");
                browserCreatedAt = System.nanoTime();
            }

            if (!startRequested && minecraft.level != null
                    && minecraft.level.getGameTime() >= request.startAtGameTime()) {
                startRequested = true;
                startRequestedAt = System.nanoTime();
                NegativePlayer.LOGGER.info("Se alcanzó el tick de inicio sincronizado; solicitando reproducción.");
            }

            if (ticks % 4 == 0) injectPlaybackScript();
            pollBrowserFrames();

            long now = System.nanoTime();
            if (startRequested && state == PlaybackState.WAITING && sawPlaying
                    && browser.isTextureReady() && browser.getTextureIdentifier() != null) {
                state = PlaybackState.PLAYING;
                playbackVisible = true;
                fadeInStartedAt = now;
                NegativePlayer.LOGGER.info("El video comenzó a reproducirse y su textura está lista; iniciando fade in.");
            }

            if (startRequested && !playbackVisible && state != PlaybackState.STOPPING
                    && now - startRequestedAt > 120_000_000_000L) {
                NegativePlayer.LOGGER.error("Chromium no produjo una textura visible en 120 segundos. Revisa la inicialización del navegador y la carga del proveedor.");
                requestStop();
            }
        }

        if (state == PlaybackState.STOPPING && playbackVisible && stopStartedAt > 0L
                && System.nanoTime() - stopStartedAt >= fadeOutDurationNanos()) {
            minecraft.setScreen(null);
        }
    }

    private List<CefFrame> getBrowserFrames() {
        List<CefFrame> frames = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        try {
            CefFrame mainFrame = browser.getMainFrame();
            if (mainFrame != null && mainFrame.isValid()) {
                frames.add(mainFrame);
                seen.add(mainFrame.getIdentifier());
            }

            Vector<String> frameIds = browser.getFrameIdentifiers();
            if (frameIds != null) {
                for (String frameId : frameIds) {
                    CefFrame frame = browser.getFrameByIdentifier(frameId);
                    if (frame == null || !frame.isValid()) continue;
                    String identifier = frame.getIdentifier();
                    if (identifier == null || seen.add(identifier)) frames.add(frame);
                }
            }
        } catch (Throwable throwable) {
            NegativePlayer.LOGGER.debug("No se pudieron enumerar los frames del navegador.", throwable);
        }

        return frames;
    }

    private void injectPlaybackScript() {
        if (browser == null) return;

        String volumeValue = String.format(Locale.ROOT, "%.3f", volume / 100.0);
        boolean shouldPlay = startRequested && !paused && state != PlaybackState.STOPPING;

        String script = "(function(){try{"
                + "var npVolume=" + volumeValue + ";window.__negativePlayerVolume=npVolume;"
                + "if(window.__npSetVolume){window.__npSetVolume(" + volume + ");}"
                + "if(window.__npPlayer){"
                + "if(" + shouldPlay + "){"
                + "try{window.__npPlayer.mute();window.__npPlayer.playVideo();}catch(e){}"
                + "}else if(" + paused + "){try{window.__npPlayer.pauseVideo();}catch(e){}}"
                + "}"
                + "var npMark=function(s){try{window.name='negativeplayer:'+s;}catch(e){}"
                + "try{if(location.hash!=='#np-'+s){history.replaceState(null,'',location.pathname+location.search+'#np-'+s);}}catch(e){}};"
                + "var v=document.querySelector('video');"
                + "if(v){"
                + "if(!v.__negativePlayerBound){v.__negativePlayerBound=true;"
                + "v.addEventListener('playing',function(){try{v.muted=false;v.volume=window.__negativePlayerVolume;}catch(e){}npMark('playing');});"
                + "v.addEventListener('ended',function(){npMark('ended');});"
                + "v.addEventListener('error',function(){npMark('error');});}"
                + "if(" + shouldPlay + "){"
                + "if(v.paused){v.muted=true;v.volume=0;var p=v.play();if(p&&p.catch){p.catch(function(){npMark('blocked');});}}"
                + "else{v.muted=false;v.volume=npVolume;npMark('playing');}"
                + "}else if(" + paused + "&&!v.paused){v.pause();}"
                + "if(v.ended){npMark('ended');}"
                + "}else{var s=(document.body&&document.body.innerText||'').toLowerCase();"
                + "if(/video unavailable|this video is unavailable|file does not exist|you need access/.test(s)){npMark('error');}}"
                + "}catch(e){}})();";

        for (CefFrame frame : getBrowserFrames()) {
            try {
                frame.executeJavaScript(script, "", 0);
            } catch (Throwable throwable) {
                NegativePlayer.LOGGER.debug("No se pudo ejecutar el control de reproducción en un frame.", throwable);
            }
        }
    }

    private void pollBrowserFrames() {
        if (browser == null) return;

        for (CefFrame frame : getBrowserFrames()) {
            try {
                String frameName = frame.getName();
                String frameUrl = frame.getURL();
                String status = ((frameName == null ? "" : frameName) + " "
                        + (frameUrl == null ? "" : frameUrl)).toLowerCase(Locale.ROOT);

                if (status.contains("negativeplayer:playing") || status.contains("#np-playing")) {
                    if (!sawPlaying) {
                        sawPlaying = true;
                        NegativePlayer.LOGGER.info("El reproductor confirmó que el video está reproduciéndose.");
                    }
                } else if (status.contains("negativeplayer:ended") || status.contains("#np-ended")) {
                    if (state != PlaybackState.STOPPING) {
                        NegativePlayer.LOGGER.info("El video terminó; aplicando fade out.");
                        requestStop();
                    }
                } else if (status.contains("negativeplayer:error") || status.contains("#np-error")) {
                    if (state != PlaybackState.STOPPING) {
                        NegativePlayer.LOGGER.error("El reproductor informó que el video no está disponible o no se puede abrir.");
                        requestStop();
                    }
                } else if (status.contains("negativeplayer:blocked") || status.contains("#np-blocked")) {
                    if (state != PlaybackState.STOPPING) {
                        NegativePlayer.LOGGER.error("Chromium bloqueó el inicio del video incluso después de solicitar reproducción silenciada.");
                        requestStop();
                    }
                }
            } catch (Throwable throwable) {
                NegativePlayer.LOGGER.debug("No se pudo consultar el estado de un frame de video.", throwable);
            }
        }
    }

    public void setPaused(boolean paused) {
        if (state == PlaybackState.STOPPING) return;
        this.paused = paused;
        NegativePlayer.LOGGER.info(paused ? "Reproducción pausada." : "Reproducción reanudada.");
    }

    public void setVolume(int volume) {
        this.volume = Math.max(1, Math.min(100, volume));
        NegativePlayer.LOGGER.info("Volumen de NegativePlayer actualizado a {}%.", this.volume);
    }

    public void requestStop() {
        if (state == PlaybackState.STOPPING) return;
        state = PlaybackState.STOPPING;
        stopStartedAt = System.nanoTime();
        NegativePlayer.LOGGER.info("Cerrando la reproducción con fade out.");
        if (!playbackVisible || request.fadeOutTicks() == 0) minecraft.setScreen(null);
    }

    private long fadeOutDurationNanos() {
        return request.fadeOutTicks() * 50_000_000L;
    }

    private int overlayAlpha() {
        long now = System.nanoTime();
        if (state == PlaybackState.WAITING) return 0;

        if (state == PlaybackState.STOPPING) {
            if (request.fadeOutTicks() == 0) return 255;
            long elapsed = Math.max(0L, now - stopStartedAt);
            return (int) Math.min(255L, elapsed * 255L / fadeOutDurationNanos());
        }

        if (!sawPlaying) return 255;
        if (request.fadeInTicks() == 0) return 0;

        long elapsed = Math.max(0L, now - fadeInStartedAt);
        return (int) Math.max(0L, 255L - elapsed * 255L / (request.fadeInTicks() * 50_000_000L));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Mientras el navegador se prepara, deja visible el mundo. La página no debe
        // taparlo antes del tick programado de reproducción.
        if (state == PlaybackState.WAITING) return;

        if (playbackVisible) {
            graphics.fill(0, 0, width, height, 0xFF000000);

            if (browser != null && browser.isTextureReady()) {
                ResourceLocation texture = browser.getTextureIdentifier();
                int textureWidth = browser.getRenderer().getTextureWidth();
                int textureHeight = browser.getRenderer().getTextureHeight();

                if (texture != null && textureWidth > 0 && textureHeight > 0) {
                    graphics.blit(texture, 0, 0, 0.0F, 0.0F,
                            width, height, textureWidth, textureHeight);
                }
            }
        }

        int alpha = overlayAlpha();
        if (alpha > 0) graphics.fill(0, 0, width, height, alpha << 24);
    }

    @Override
    public void onClose() {
        requestStop();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            requestStop();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (!closeIssued && browser != null) {
            closeIssued = true;
            try {
                browser.close();
            } catch (Throwable throwable) {
                NegativePlayer.LOGGER.warn("No se pudo cerrar limpiamente el navegador de NegativePlayer.", throwable);
            }
            browser = null;
        }

        LocalPlayerHttpServer.stop();
        restoreMinecraftAudio();
        super.removed();
    }
}
