package com.clippocket.app;
import java.util.Locale;

final class SizeEstimate {
    static double bytes(double exact, double approximate, double durationSeconds, double bitrateKbps) {
        if (Double.isFinite(exact) && exact > 0) return exact;
        if (Double.isFinite(approximate) && approximate > 0) return approximate;
        if (Double.isFinite(durationSeconds) && Double.isFinite(bitrateKbps) && durationSeconds > 0 && bitrateKbps > 0)
            return durationSeconds * bitrateKbps * 1000d / 8d;
        return -1;
    }
    static String display(double bytes) {
        if (!Double.isFinite(bytes) || bytes < 0) return "Size unavailable";
        if (bytes >= 1_000_000_000d) return String.format(Locale.US, "%.2f GB", bytes / 1_000_000_000d);
        if (bytes >= 1_000_000d) return String.format(Locale.US, "%.1f MB", bytes / 1_000_000d);
        if (bytes >= 1000d) return String.format(Locale.US, "%.1f KB", bytes / 1000d);
        return String.format(Locale.US, "%.0f bytes", bytes);
    }
}
