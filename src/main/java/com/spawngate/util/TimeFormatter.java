package com.spawngate.util;

public final class TimeFormatter {

    private TimeFormatter() {}

    /**
     * Форматирует количество секунд в человекочитаемую строку вида: "47ч. 15мин. 20сек."
     */
    public static String formatRemainingTime(long remainingSeconds) {
        if (remainingSeconds <= 0) {
            return "0 сек.";
        }

        long hours = remainingSeconds / 3600;
        long minutes = (remainingSeconds % 3600) / 60;
        long seconds = remainingSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append("ч. ");
        }
        if (minutes > 0 || hours > 0) {
            sb.append(minutes).append("мин. ");
        }
        sb.append(seconds).append("сек.");

        return sb.toString().trim();
    }
}
