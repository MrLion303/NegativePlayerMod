package com.negativestudios.negativeplayer.util;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

public final class VideoUrlValidator {
    private static final Set<String> YOUTUBE_HOSTS = Set.of(
            "youtube.com",
            "www.youtube.com",
            "m.youtube.com",
            "youtu.be",
            "www.youtu.be",
            "youtube-nocookie.com",
            "www.youtube-nocookie.com"
    );

    private static final Set<String> DRIVE_HOSTS = Set.of(
            "drive.google.com",
            "docs.google.com"
    );

    private VideoUrlValidator() {
    }

    public enum Provider {
        YOUTUBE,
        GOOGLE_DRIVE,
        UNSUPPORTED
    }

    public static Provider detectProvider(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Provider.UNSUPPORTED;
        }

        try {
            URI uri = URI.create(rawUrl.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getPort() != -1) {
                return Provider.UNSUPPORTED;
            }

            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();

            if (YOUTUBE_HOSTS.contains(host) && isPlausibleYoutubeVideo(uri, host, path)) {
                return Provider.YOUTUBE;
            }

            if (DRIVE_HOSTS.contains(host) && isPlausibleDriveFile(uri, path)) {
                return Provider.GOOGLE_DRIVE;
            }
        } catch (IllegalArgumentException ignored) {
            return Provider.UNSUPPORTED;
        }

        return Provider.UNSUPPORTED;
    }

    private static boolean isPlausibleYoutubeVideo(URI uri, String host, String path) {
        if ("youtu.be".equals(host) || "www.youtu.be".equals(host)) {
            return path.length() > 1;
        }

        if (path.startsWith("/shorts/")
                || path.startsWith("/live/")
                || path.startsWith("/embed/")) {
            return path.split("/").length >= 3;
        }

        if ("/watch".equals(path)) {
            String query = uri.getRawQuery();
            return query != null && query.matches(".*(?:^|&)v=[^&]+.*");
        }

        return false;
    }

    private static boolean isPlausibleDriveFile(URI uri, String path) {
        if (path.startsWith("/file/d/")) {
            return path.split("/").length >= 4;
        }

        if ("/uc".equals(path) || "/open".equals(path)) {
            String query = uri.getRawQuery();
            return query != null && query.matches(".*(?:^|&)(?:id|export)=[^&]+.*");
        }

        return false;
    }
}
