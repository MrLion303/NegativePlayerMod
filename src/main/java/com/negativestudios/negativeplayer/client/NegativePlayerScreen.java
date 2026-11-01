package com.negativestudios.negativeplayer.client;

import de.keksuccino.rinku.Rinku;
import de.keksuccino.rinku.RinkuBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.negativestudios.negativeplayer.NegativePlayer;
import com.negativestudios.negativeplayer.network.PlaybackRequestPacket;
import com.negativestudios.negativeplayer.util.VideoUrlValidator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import org.cef.browser.CefFrame;

import java.util.Locale;
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
    private long fadeInStartedAt;
    private long stopStartedAt;
    private long startRequestedAt;
    private boolean closeIssued;

    public NegativePlayerScreen(PlaybackRequestPacket request) {
        super(Component.literal("NegativePlayer"));
        this.request = request;
        this.volume = request.volume();
    }

    @Override
    protected void init() {
        super.init();
        try {
            String playerUrl = VideoUrlValidator.toPlayerUrl(request.url());
            browser = Rinku.createBrowser(playerUrl, false);
            browser.useBrowserControls(false);
            resizeBrowser();
            NegativePlayer.LOGGER.info("Navegador de NegativePlayer creado para el proveedor {}.",
                    VideoUrlValidator.detectProvider(request.url()));
        } catch (Throwable throwable) {
            NegativePlayer.LOGGER.error("No se pudo crear el navegador de NegativePlayer.", throwable);
            requestStop();
        }
    }

    private void resizeBrowser() {
        if (browser == null) return;
        double scale = minecraft.getWindow().getGuiScale();
        browser.resize(Math.max(1, (int) Math.round(width * scale)),
                Math.max(1, (int) Math.round(height * scale)));
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
            if (!startRequested && minecraft.level != null
                    && minecraft.level.getGameTime() >= request.startAtGameTime()) {
                startRequested = true;
                startRequestedAt = System.nanoTime();
                NegativePlayer.LOGGER.info("Se alcanzó el tick de inicio sincronizado; solicitando reproducción.");
            }

            if (ticks % 4 == 0) injectPlaybackScript();
            pollBrowserFrames();

            if (startRequested && !sawPlaying && state != PlaybackState.STOPPING
                    && System.nanoTime() - startRequestedAt > 120_000_000_000L) {
                NegativePlayer.LOGGER.error("El reproductor no inició en 120 segundos. Comprueba que el video sea público, permita inserción y esté disponible.");
                requestStop();
            }
        }

        if (state == PlaybackState.STOPPING && stopStartedAt > 0L
                && System.nanoTime() - stopStartedAt >= fadeOutDurationNanos()) {
            minecraft.setScreen(null);
        }
    }

    private void injectPlaybackScript() {
        if (browser == null) return;
        String volumeValue = String.format(Locale.ROOT, "%.3f", volume / 100.0);
        boolean shouldPlay = startRequested && !paused && state != PlaybackState.STOPPING;
        String script = "(function(){try{"
                + "if(location.hostname.indexOf('drive.google.com')!==-1){"
                + "var st=document.getElementById('negativeplayer-style');"
                + "if(!st){st=document.createElement('style');st.id='negativeplayer-style';"
                + "st.textContent='html,body{margin:0!important;background:#000!important;overflow:hidden!important}video{position:fixed!important;inset:0!important;width:100vw!important;height:100vh!important;object-fit:contain!important;background:#000!important;z-index:2147483647!important}';"
                + "document.head.appendChild(st);}}"
                + "var v=document.querySelector('video');"
                + "if(v){"
                + "if(!v.__negativePlayerBound){v.__negativePlayerBound=true;"
                + "v.addEventListener('playing',function(){window.location.hash='np-playing';});"
                + "v.addEventListener('ended',function(){window.location.hash='np-ended';});"
                + "v.addEventListener('error',function(){window.location.hash='np-error';});}"
                + "v.volume=" + volumeValue + ";v.muted=false;"
                + "if(" + shouldPlay + "){if(v.paused){var p=v.play();if(p&&p.catch){p.catch(function(){window.location.hash='np-blocked';});}}}"
                + "else if(!v.paused){v.pause();}"
                + "if(v.ended){window.location.hash='np-ended';}"
                + "}else{var s=(document.body&&document.body.innerText||'').toLowerCase();"
                + "if(/video unavailable|this video is unavailable|file does not exist|you need access/.test(s)){window.location.hash='np-error';}}"
                + "}catch(e){}})();";

        try {
            Vector<String> frameIds = browser.getFrameIdentifiers();
            for (Long frameId : frameIds) {
                CefFrame frame = browser.getFrameByIdentifier(frameId);
                if (frame != null && frame.isValid()) frame.executeJavaScript(script, "", 0);
            }
            if (frameIds.isEmpty()) {
                CefFrame frame = browser.getMainFrame();
                if (frame != null && frame.isValid()) frame.executeJavaScript(script, "", 0);
            }
        } catch (Throwable throwable) {
            NegativePlayer.LOGGER.debug("No se pudo actualizar el estado del reproductor en este ciclo.", throwable);
        }
    }

    private void pollBrowserFrames() {
        if (browser == null) return;
        try {
            Vector<String> frameIds = browser.getFrameIdentifiers();
            for (Long frameId : frameIds) {
                CefFrame frame = browser.getFrameByIdentifier(frameId);
                if (frame == null || !frame.isValid()) continue;
                String frameUrl = frame.getURL();
                if (frameUrl == null) continue;
                String status = frameUrl.toLowerCase(Locale.ROOT);
                if (status.contains("#np-playing")) {
                    if (!sawPlaying) {
                        sawPlaying = true;
                        state = PlaybackState.PLAYING;
                        fadeInStartedAt = System.nanoTime();
                        NegativePlayer.LOGGER.info("El video comenzó a reproducirse.");
                    }
                } else if (status.contains("#np-ended")) {
                    if (state != PlaybackState.STOPPING) {
                        NegativePlayer.LOGGER.info("El video terminó; aplicando fade out.");
                        requestStop();
                    }
                } else if (status.contains("#np-error")) {
                    if (state != PlaybackState.STOPPING) {
                        NegativePlayer.LOGGER.error("El reproductor informó que el video no está disponible o no se puede abrir.");
                        requestStop();
                    }
                } else if (status.contains("#np-blocked")) {
                    if (state != PlaybackState.STOPPING) {
                        NegativePlayer.LOGGER.error("Chromium bloqueó el inicio automático del video.");
                        requestStop();
                    }
                }
            }
        } catch (Throwable throwable) {
            NegativePlayer.LOGGER.debug("No se pudo consultar el estado de los frames de video.", throwable);
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
        if (request.fadeOutTicks() == 0) minecraft.setScreen(null);
    }

    private long fadeInDurationNanos() {
        return request.fadeInTicks() * 50_000_000L;
    }

    private long fadeOutDurationNanos() {
        return request.fadeOutTicks() * 50_000_000L;
    }

    private int overlayAlpha() {
        long now = System.nanoTime();
        if (state == PlaybackState.WAITING) return 255;
        if (state == PlaybackState.STOPPING) {
            if (request.fadeOutTicks() == 0) return 255;
            long elapsed = Math.max(0L, now - stopStartedAt);
            return (int) Math.min(255L, elapsed * 255L / fadeOutDurationNanos());
        }
        if (!sawPlaying) return 255;
        if (request.fadeInTicks() == 0) return 0;
        long elapsed = Math.max(0L, now - fadeInStartedAt);
        return (int) Math.max(0L, 255L - elapsed * 255L / fadeInDurationNanos());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF000000);

        if (browser != null && browser.isTextureReady() && browser.getRenderer().getTextureID() != 0) {
            RenderSystem.disableDepthTest();
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            RenderSystem.setShaderTexture(0, browser.getRenderer().getTextureID());

            Tesselator tessellator = Tesselator.getInstance();
            BufferBuilder buffer = tessellator.getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            buffer.vertex(0, height, 0).uv(0.0f, 1.0f).color(255, 255, 255, 255).endVertex();
            buffer.vertex(width, height, 0).uv(1.0f, 1.0f).color(255, 255, 255, 255).endVertex();
            buffer.vertex(width, 0, 0).uv(1.0f, 0.0f).color(255, 255, 255, 255).endVertex();
            buffer.vertex(0, 0, 0).uv(0.0f, 0.0f).color(255, 255, 255, 255).endVertex();
            tessellator.end();

            RenderSystem.setShaderTexture(0, 0);
            RenderSystem.enableDepthTest();
        }

        int alpha = overlayAlpha();
        if (alpha > 0) graphics.fill(0, 0, width, height, (alpha << 24));
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
        super.removed();
    }
}
