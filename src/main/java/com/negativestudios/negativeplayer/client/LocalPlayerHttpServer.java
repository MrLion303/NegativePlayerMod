package com.negativestudios.negativeplayer.client;

import com.negativestudios.negativeplayer.util.VideoUrlValidator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LocalPlayerHttpServer {
    private static HttpServer server;
    private static ExecutorService executor;

    private LocalPlayerHttpServer() {
    }

    public static synchronized String start(String sourceUrl) throws IOException {
        if (VideoUrlValidator.detectProvider(sourceUrl) == VideoUrlValidator.Provider.UNSUPPORTED) {
            throw new IllegalArgumentException("Proveedor de video no compatible");
        }

        if (server == null) {
            server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
            executor = Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "NegativePlayer-local-player");
                thread.setDaemon(true);
                return thread;
            });
            server.setExecutor(executor);
            server.createContext("/player", LocalPlayerHttpServer::handle);
            server.start();
        }

        String address = "http://127.0.0.1:" + server.getAddress().getPort();
        return address + "/player?url=" + URLEncoder.encode(sourceUrl, StandardCharsets.UTF_8);
    }

    private static void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        try {
            String sourceUrl = queryValue(exchange.getRequestURI().getRawQuery(), "url");
            if (sourceUrl == null || sourceUrl.length() > 8_192
                    || VideoUrlValidator.detectProvider(sourceUrl) == VideoUrlValidator.Provider.UNSUPPORTED) {
                send(exchange, 400, errorPage("NegativePlayer recibió una URL no válida."));
                return;
            }

            String origin = "http://127.0.0.1:" + server.getAddress().getPort();
            send(exchange, 200, createPlayerPage(sourceUrl, origin));
        } catch (IllegalArgumentException exception) {
            send(exchange, 400, errorPage("No se pudo preparar la fuente de video."));
        } catch (Throwable throwable) {
            send(exchange, 500, errorPage("No se pudo preparar el reproductor."));
        }
    }

    private static String createPlayerPage(String sourceUrl, String origin) {
        VideoUrlValidator.Provider provider = VideoUrlValidator.detectProvider(sourceUrl);
        String playerUrl = htmlAttribute(VideoUrlValidator.toPlayerUrl(sourceUrl, origin));

        if (provider == VideoUrlValidator.Provider.YOUTUBE) {
            return """
                    <!doctype html>
                    <html lang="en">
                    <head>
                      <meta charset="utf-8">
                      <meta name="viewport" content="width=device-width, initial-scale=1">
                      <meta name="referrer" content="strict-origin-when-cross-origin">
                      <title>NegativePlayer</title>
                      <style>
                        html, body { margin: 0; width: 100%; height: 100%; overflow: hidden; background: #000; }
                        #np-player { position: fixed; inset: 0; width: 100%; height: 100%; border: 0; }
                      </style>
                    </head>
                    <body>
                      <iframe id="np-player" src="{{PLAYER_URL}}" title="YouTube video player"
                        allow="autoplay; encrypted-media; picture-in-picture"
                        referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe>
                      <script>
                        (function () {
                          function mark(state) {
                            try {
                              history.replaceState(null, "", location.pathname + location.search + "#np-" + state);
                            } catch (error) {
                              location.hash = "np-" + state;
                            }
                          }
                          window.__npVolume = 100;
                          window.__npPlayer = null;
                          window.__npMark = mark;
                          window.__npPlay = function () {
                            if (!window.__npPlayer) return;
                            try {
                              window.__npPlayer.mute();
                              window.__npPlayer.playVideo();
                            } catch (error) {}
                          };
                          window.__npPause = function () {
                            if (!window.__npPlayer) return;
                            try { window.__npPlayer.pauseVideo(); } catch (error) {}
                          };
                          window.__npSetVolume = function (volume) {
                            window.__npVolume = Math.max(1, Math.min(100, Number(volume) || 1));
                            if (!window.__npPlayer) return;
                            try {
                              window.__npPlayer.setVolume(window.__npVolume);
                              if (window.__npPlayer.getPlayerState() === 1) window.__npPlayer.unMute();
                            } catch (error) {}
                          };
                          window.onYouTubeIframeAPIReady = function () {
                            window.__npPlayer = new YT.Player("np-player", {
                              events: {
                                onReady: function (event) {
                                  try { event.target.mute(); } catch (error) {}
                                  mark("ready");
                                },
                                onStateChange: function (event) {
                                  if (event.data === 1) {
                                    try {
                                      event.target.setVolume(window.__npVolume);
                                      event.target.unMute();
                                    } catch (error) {}
                                    mark("playing");
                                  } else if (event.data === 0) {
                                    mark("ended");
                                  }
                                },
                                onError: function (event) {
                                  mark("error-" + event.data);
                                }
                              }
                            });
                          };
                        })();
                      </script>
                      <script src="https://www.youtube.com/iframe_api"></script>
                    </body>
                    </html>
                    """.replace("{{PLAYER_URL}}", playerUrl);
        }

        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <meta name="referrer" content="strict-origin-when-cross-origin">
                  <title>NegativePlayer</title>
                  <style>
                    html, body { margin: 0; width: 100%; height: 100%; overflow: hidden; background: #000; }
                    iframe { position: fixed; inset: 0; width: 100%; height: 100%; border: 0; background: #000; }
                  </style>
                  <script>
                    window.__npMark = function (state) {
                      try {
                        history.replaceState(null, "", location.pathname + location.search + "#np-" + state);
                      } catch (error) {
                        location.hash = "np-" + state;
                      }
                    };
                  </script>
                </head>
                <body>
                  <iframe src="{{PLAYER_URL}}" title="Google Drive video player"
                    allow="autoplay; fullscreen" referrerpolicy="strict-origin-when-cross-origin"
                    allowfullscreen></iframe>
                </body>
                </html>
                """.replace("{{PLAYER_URL}}", playerUrl);
    }

    private static String queryValue(String query, String key) {
        if (query == null || query.isBlank()) return null;
        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            if (separator < 0) continue;
            String name = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
            if (key.equals(name)) {
                return URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String htmlAttribute(String value) {
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String errorPage(String message) {
        return "<!doctype html><html><body style=\"margin:0;background:#000;color:#fff;font:16px sans-serif\">"
                + htmlAttribute(message) + "</body></html>";
    }

    private static void send(HttpExchange exchange, int status, String html) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Referrer-Policy", "strict-origin-when-cross-origin");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    public static synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
