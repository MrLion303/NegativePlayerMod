package com.negativestudios.negativeplayer.util;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VideoUrlValidator {
    private static final Set<String> YOUTUBE_HOSTS = Set.of(
            "youtube.com", "www.youtube.com", "m.youtube.com",
            "youtu.be", "www.youtu.be",
            "youtube-nocookie.com", "www.youtube-nocookie.com"
    );
    private static final Set<String> DRIVE_HOSTS = Set.of("drive.google.com", "docs.google.com");
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern DRIVE_ID = Pattern.compile("[A-Za-z0-9_-]{10,200}");

    private VideoUrlValidator() {
    }

    public enum Provider { YOUTUBE, GOOGLE_DRIVE, UNSUPPORTED }

    public static Provider detectProvider(String rawUrl) {
        URI uri = parse(rawUrl);
        if (uri == null) return Provider.UNSUPPORTED;

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (YOUTUBE_HOSTS.contains(host) && extractYoutubeId(uri) != null) return Provider.YOUTUBE;
        if (DRIVE_HOSTS.contains(host) && extractDriveId(uri) != null) return Provider.GOOGLE_DRIVE;
        return Provider.UNSUPPORTED;
    }

    public static String toPlayerUrl(String rawUrl) {
        URI uri = parse(rawUrl);
        if (uri == null) throw new IllegalArgumentException("URL no válida");

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (YOUTUBE_HOSTS.contains(host)) {
            String id = extractYoutubeId(uri);
            if (id == null) throw new IllegalArgumentException("No se pudo extraer el ID del video de YouTube");
            return "https://www.youtube.com/embed/" + id
                    + "?autoplay=0&controls=0&rel=0&modestbranding=1"
                    + "&playsinline=1&disablekb=1&fs=0&iv_load_policy=3";
        }

        if (DRIVE_HOSTS.contains(host)) {
            String id = extractDriveId(uri);
            if (id == null) throw new IllegalArgumentException("No se pudo extraer el ID del archivo de Drive");
            return "https://drive.google.com/file/d/" + id + "/preview?autoplay=0&rm=minimal";
        }

        throw new IllegalArgumentException("Proveedor de video no compatible");
    }

    private static URI parse(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) return null;
        try {
            URI uri = URI.create(rawUrl.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getPort() != -1) return null;
            return uri;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String extractYoutubeId(URI uri) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String path = uri.getPath() == null ? "" : uri.getPath();
        String candidate = null;

        if (host.equals("youtu.be") || host.equals("www.youtu.be")) {
            candidate = firstSegment(path);
        } else if (path.equals("/watch")) {
            candidate = queryValue(uri, "v");
        } else if (path.startsWith("/embed/") || path.startsWith("/shorts/") || path.startsWith("/live/")) {
            candidate = firstSegment(path.substring(path.indexOf('/', 1) + 1));
        }

        return candidate != null && VIDEO_ID.matcher(candidate).matches() ? candidate : null;
    }

    private static String extractDriveId(URI uri) {
        String path = uri.getPath() == null ? "" : uri.getPath();
        String candidate = null;
        Matcher matcher = Pattern.compile("^/file/d/([^/]+)").matcher(path);
        if (matcher.find()) candidate = matcher.group(1);
        if (candidate == null && (path.equals("/uc") || path.equals("/open"))) {
            candidate = queryValue(uri, "id");
        }
        return candidate != null && DRIVE_ID.matcher(candidate).matches() ? candidate : null;
    }

    private static String firstSegment(String path) {
        if (path == null) return null;
        String value = path.startsWith("/") ? path.substring(1) : path;
        int slash = value.indexOf('/');
        return slash >= 0 ? value.substring(0, slash) : value;
    }

    private static String queryValue(URI uri, String key) {
        String query = uri.getRawQuery();
        if (query == null) return null;
        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            if (separator <= 0) continue;
            String name = URLDecoder.decode(pair.substring(0, separator), StandardCharsets.UTF_8);
            if (name.equals(key)) {
                return URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
