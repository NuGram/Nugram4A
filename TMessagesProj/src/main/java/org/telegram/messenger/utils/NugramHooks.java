package org.telegram.messenger.utils;

import android.text.TextUtils;

import org.telegram.messenger.MessagesController;

public class NugramHooks {

    public static final String PREF_ZALGO_REMOVER = "nugram_zalgo_remover";
    public static final String PREF_RESTRICTED_FORWARD = "nugram_restricted_forward";

    public static boolean isZalgoRemoverEnabled() {
        try {
            return MessagesController.getGlobalMainSettings().getBoolean(PREF_ZALGO_REMOVER, false);
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static boolean isRestrictedForwardEnabled() {
        try {
            return MessagesController.getGlobalMainSettings().getBoolean(PREF_RESTRICTED_FORWARD, false);
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static CharSequence maybeRemoveZalgo(CharSequence text) {
        if (TextUtils.isEmpty(text) || !isZalgoRemoverEnabled()) {
            return text;
        }
        return removeExcessiveCombiningMarks(text.toString());
    }

    private static String removeExcessiveCombiningMarks(String text) {
        StringBuilder result = new StringBuilder(text.length());
        StringBuilder pendingMarks = new StringBuilder();
        int combiningMarksCount = 0;

        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            int charCount = Character.charCount(codePoint);

            if (isCombiningMark(codePoint)) {
                pendingMarks.appendCodePoint(codePoint);
                combiningMarksCount++;
            } else {
                if (combiningMarksCount == 1) {
                    result.append(pendingMarks);
                }
                pendingMarks.setLength(0);
                combiningMarksCount = 0;
                result.appendCodePoint(codePoint);
            }

            i += charCount;
        }

        if (combiningMarksCount == 1) {
            result.append(pendingMarks);
        }

        return result.toString();
    }

    private static boolean isCombiningMark(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK
            || type == Character.COMBINING_SPACING_MARK
            || type == Character.ENCLOSING_MARK;
    }
}
