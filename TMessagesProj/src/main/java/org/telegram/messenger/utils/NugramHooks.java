package org.telegram.messenger.utils;

import android.text.TextUtils;

import org.telegram.messenger.MessagesController;

import java.util.ArrayList;
import java.util.List;

public class NugramHooks {

    public static final String PREF_ZALGO_REMOVER = "nugram_zalgo_remover";
    public static final String PREF_RESTRICTED_FORWARD = "nugram_restricted_forward";
    public static final String PREF_UNLIMITED_LOCAL_FILTERS = "nugram_unlimited_local_filters";
    public static final String PREF_UNLIMITED_PINS = "nugram_unlimited_pins";
    public static final String PREF_UNLIMITED_FOLDERS = "nugram_unlimited_folders";
    public static final String PREF_UNLIMITED_LOGINS = "nugram_unlimited_logins";
    public static final String PREF_DISABLE_NUMBER_ROUNDING = "nugram_disable_number_rounding";
    public static final String PREF_TIME_WITH_SECONDS = "nugram_time_with_seconds";
    public static final String PREF_HIDE_PHONE_NUMBER = "nugram_hide_phone_number";

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

    public static boolean isUnlimitedPinsEnabled() {
        try {
            return MessagesController.getGlobalMainSettings().getBoolean(PREF_UNLIMITED_PINS,
                MessagesController.getGlobalMainSettings().getBoolean(PREF_UNLIMITED_LOCAL_FILTERS, false));
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static boolean isUnlimitedFoldersEnabled() {
        try {
            return MessagesController.getGlobalMainSettings().getBoolean(PREF_UNLIMITED_FOLDERS,
                MessagesController.getGlobalMainSettings().getBoolean(PREF_UNLIMITED_LOCAL_FILTERS, false));
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static boolean isUnlimitedLoginsEnabled() {
        try {
            return MessagesController.getGlobalMainSettings().getBoolean(PREF_UNLIMITED_LOGINS, true);
        } catch (Throwable ignore) {
            return true;
        }
    }

    public static boolean canUseLocalFolderColors(boolean premium) {
        return premium || isUnlimitedFoldersEnabled();
    }

    public static boolean isUnlimitedPinsFoldersEnabled() {
        try {
            return isUnlimitedPinsEnabled() || isUnlimitedFoldersEnabled();
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static boolean isGhostModeEnabled() {
        try {
            return NugramGhostMode.isGhostModeActive();
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static boolean isDisableNumberRoundingEnabled() {
        try {
            return MessagesController.getGlobalMainSettings().getBoolean(PREF_DISABLE_NUMBER_ROUNDING, false);
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static boolean isTimeWithSecondsEnabled() {
        try {
            return MessagesController.getGlobalMainSettings().getBoolean(PREF_TIME_WITH_SECONDS, false);
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static boolean isHidePhoneNumberEnabled() {
        try {
            return MessagesController.getGlobalMainSettings().getBoolean(PREF_HIDE_PHONE_NUMBER, false);
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static ArrayList<Long> getLocalPinnedDialogs(int account, int folderId) {
        ArrayList<Long> result = new ArrayList<>();
        try {
            String value = MessagesController.getMainSettings(account).getString("nugram_local_pins_" + folderId, "");
            if (TextUtils.isEmpty(value)) {
                return result;
            }
            String[] parts = value.split(",");
            for (String part : parts) {
                if (TextUtils.isEmpty(part)) {
                    continue;
                }
                result.add(Long.parseLong(part));
            }
        } catch (Throwable ignore) {
        }
        return result;
    }

    public static void saveLocalPinnedDialogs(int account, int folderId, List<Long> dialogIds) {
        try {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < dialogIds.size(); i++) {
                if (i > 0) {
                    builder.append(',');
                }
                builder.append(dialogIds.get(i));
            }
            MessagesController.getMainSettings(account)
                .edit()
                .putString("nugram_local_pins_" + folderId, builder.toString())
                .apply();
        } catch (Throwable ignore) {
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
