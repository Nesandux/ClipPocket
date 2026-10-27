package com.clippocket.app;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Platform-neutral input and output rules; yt-dlp determines site support. */
final class DownloadRules {
    static boolean validUrl(String input) {
        if (input == null || input.isEmpty() || !input.equals(input.trim())) return false;
        try {
            URI uri = new URI(input);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && !uri.getHost().isEmpty()
                    && uri.getRawUserInfo() == null;
        } catch (Exception e) { return false; }
    }

    static String extractUrl(String input) {
        if (input == null) return "";
        Matcher matcher = Pattern.compile("https?://[^\\s<>\"\\u201c\\u201d]+", Pattern.CASE_INSENSITIVE).matcher(input);
        return matcher.find() ? matcher.group().replaceAll("[).,;!]+$", "") : input.trim();
    }

    static String videoFormat(int height) {
        // Unknown heights are common for direct links and social videos.
        return "bestvideo[height<=?" + height + "][ext=mp4]+bestaudio[ext=m4a]"
                + "/best[height<=?" + height + "][ext=mp4]"
                + "/bestvideo[height<=?" + height + "]+bestaudio"
                + "/best[height<=?" + height + "]";
    }

    static String mimeType(String name, boolean audio) {
        if (audio) return "audio/mp4";
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp4") || lower.endsWith(".m4v")) return "video/mp4";
        if (lower.endsWith(".webm")) return "video/webm";
        if (lower.endsWith(".mkv")) return "video/x-matroska";
        if (lower.endsWith(".mov")) return "video/quicktime";
        if (lower.endsWith(".flv")) return "video/x-flv";
        if (lower.endsWith(".3gp")) return "video/3gpp";
        if (lower.endsWith(".ts")) return "video/mp2t";
        return "application/octet-stream";
    }
}
