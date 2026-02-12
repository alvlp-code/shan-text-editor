package com.shin.util;

import java.util.concurrent.TimeUnit;

public class TimeUtils {

    public static String getRelativeTime(long createdAtMillis) {
        long now = System.currentTimeMillis();
        long diffMillis = now - createdAtMillis;

        if (diffMillis < 0) {
            return "just now";
        }

        long seconds = TimeUnit.MILLISECONDS.toSeconds(diffMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(diffMillis);
        long hours = TimeUnit.MILLISECONDS.toHours(diffMillis);
        long days = TimeUnit.MILLISECONDS.toDays(diffMillis);

        if (seconds < 60) {
            return "just now";
        } else if (minutes < 60) {
            return minutes + " minute" + (minutes == 1 ? "" : "s") + " ago";
        } else if (hours < 24) {
            return hours + " hour" + (hours == 1 ? "" : "s") + " ago";
        } else if (days == 1) {
            return "1 day ago";
        } else if (days < 7) {
            return days + " days ago";
        } else {
            // Optional: fallback to date (e.g., "Feb 1")
            // For now, keep it simple
            return days + " days ago";
        }
    }
}