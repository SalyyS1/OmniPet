package io.github.salyvn.omnipet.paper.studio.bukkit;

import java.io.IOException;
import java.util.regex.Pattern;

final class StudioErrorMessages {
    private static final String STORAGE_FAILURE = "storage transaction failed; check the server log";
    private static final Pattern ABSOLUTE_PATH = Pattern.compile(
            "(?is).*(?:[a-z]:[\\\\/]|(?:^|\\s)/(?:[^\\s/]+/)*[^\\s/]+|\\\\\\\\[^\\s]+).*"
    );

    private StudioErrorMessages() {
    }

    static String forAdmin(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (hasStorageCause(error) || message == null || message.isBlank() || ABSOLUTE_PATH.matcher(message).matches()) {
            return STORAGE_FAILURE;
        }
        return message.length() > 180 ? message.substring(0, 177) + "..." : message;
    }

    private static boolean hasStorageCause(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof IOException || cause instanceof SecurityException) {
                return true;
            }
        }
        return false;
    }
}
