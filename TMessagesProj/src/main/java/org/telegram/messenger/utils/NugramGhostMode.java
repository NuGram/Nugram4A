package org.telegram.messenger.utils;

import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.utils.tlutils.TlUtils;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.tgnet.tl.TL_stories;

public class NugramGhostMode {

    public static final String PREF_SEND_READ_MESSAGES = "nugram_ghost_send_read_messages";
    public static final String PREF_SEND_READ_STORIES = "nugram_ghost_send_read_stories";
    public static final String PREF_SEND_ONLINE_PACKETS = "nugram_ghost_send_online_packets";
    public static final String PREF_SEND_UPLOAD_PROGRESS = "nugram_ghost_send_upload_progress";
    public static final String PREF_SEND_OFFLINE_AFTER_ONLINE = "nugram_ghost_send_offline_after_online";
    public static final String PREF_MARK_READ_AFTER_ACTION = "nugram_ghost_mark_read_after_action";

    public static final String PREF_LOCK_READ_MESSAGES = "nugram_ghost_lock_read_messages";
    public static final String PREF_LOCK_READ_STORIES = "nugram_ghost_lock_read_stories";
    public static final String PREF_LOCK_ONLINE_PACKETS = "nugram_ghost_lock_online_packets";
    public static final String PREF_LOCK_UPLOAD_PROGRESS = "nugram_ghost_lock_upload_progress";
    public static final String PREF_LOCK_OFFLINE_AFTER_ONLINE = "nugram_ghost_lock_offline_after_online";

    private static final Object lock = new Object();
    private static final int[] readRequestBypass = new int[UserConfig.MAX_ACCOUNT_COUNT];
    private static final int[] presenceRequestBypass = new int[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Runnable[] offlineRunnables = new Runnable[UserConfig.MAX_ACCOUNT_COUNT];

    private static class GhostSettings {
        boolean sendReadMessages = true;
        boolean sendReadStories = true;
        boolean sendOnlinePackets = true;
        boolean sendUploadProgress = true;
        boolean sendOfflineAfterOnline = false;
        boolean markReadAfterAction = true;

        boolean lockReadMessages;
        boolean lockReadStories;
        boolean lockOnlinePackets;
        boolean lockUploadProgress;
        boolean lockOfflineAfterOnline;
    }

    private static GhostSettings loadSettings() {
        GhostSettings settings = new GhostSettings();
        try {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            settings.sendReadMessages = preferences.getBoolean(PREF_SEND_READ_MESSAGES, true);
            settings.sendReadStories = preferences.getBoolean(PREF_SEND_READ_STORIES, true);
            settings.sendOnlinePackets = preferences.getBoolean(PREF_SEND_ONLINE_PACKETS, true);
            settings.sendUploadProgress = preferences.getBoolean(PREF_SEND_UPLOAD_PROGRESS, true);
            settings.sendOfflineAfterOnline = preferences.getBoolean(PREF_SEND_OFFLINE_AFTER_ONLINE, false);
            settings.markReadAfterAction = preferences.getBoolean(PREF_MARK_READ_AFTER_ACTION, true);

            settings.lockReadMessages = preferences.getBoolean(PREF_LOCK_READ_MESSAGES, false);
            settings.lockReadStories = preferences.getBoolean(PREF_LOCK_READ_STORIES, false);
            settings.lockOnlinePackets = preferences.getBoolean(PREF_LOCK_ONLINE_PACKETS, false);
            settings.lockUploadProgress = preferences.getBoolean(PREF_LOCK_UPLOAD_PROGRESS, false);
            settings.lockOfflineAfterOnline = preferences.getBoolean(PREF_LOCK_OFFLINE_AFTER_ONLINE, false);
        } catch (Throwable ignore) {
        }
        return settings;
    }

    public static boolean isGhostModeActive() {
        GhostSettings settings = loadSettings();
        if (settings.sendReadMessages && !settings.lockReadMessages) {
            return false;
        }
        if (settings.sendReadStories && !settings.lockReadStories) {
            return false;
        }
        if (settings.sendOnlinePackets && !settings.lockOnlinePackets) {
            return false;
        }
        if (settings.sendUploadProgress && !settings.lockUploadProgress) {
            return false;
        }
        return settings.sendOfflineAfterOnline || settings.lockOfflineAfterOnline;
    }

    public static void setGhostModeEnabled(boolean enabled) {
        try {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            GhostSettings settings = loadSettings();
            SharedPreferences.Editor editor = preferences.edit();
            if (!settings.lockReadMessages) {
                editor.putBoolean(PREF_SEND_READ_MESSAGES, !enabled);
            }
            if (!settings.lockReadStories) {
                editor.putBoolean(PREF_SEND_READ_STORIES, !enabled);
            }
            if (!settings.lockOnlinePackets) {
                editor.putBoolean(PREF_SEND_ONLINE_PACKETS, !enabled);
            }
            if (!settings.lockUploadProgress) {
                editor.putBoolean(PREF_SEND_UPLOAD_PROGRESS, !enabled);
            }
            if (!settings.lockOfflineAfterOnline) {
                editor.putBoolean(PREF_SEND_OFFLINE_AFTER_ONLINE, enabled);
            }
            if (!preferences.contains(PREF_MARK_READ_AFTER_ACTION)) {
                editor.putBoolean(PREF_MARK_READ_AFTER_ACTION, true);
            }
            editor.apply();
        } catch (Throwable ignore) {
        }

        for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
            if (!UserConfig.getInstance(account).isClientActivated()) {
                continue;
            }
            if (enabled) {
                sendOfflineNow(account);
            } else {
                sendOnlineNow(account);
            }
        }
    }

    public static boolean shouldDropTypingRequest(int currentAccount, TLObject request) {
        GhostSettings settings = loadSettings();
        return isGhostModeActive() && !settings.sendUploadProgress && isTypingRequest(request);
    }

    public static boolean shouldDropReadRequest(int currentAccount, TLObject request) {
        if (!isReadRequest(request)) {
            return false;
        }
        if (consumeReadBypass(currentAccount)) {
            return false;
        }
        GhostSettings settings = loadSettings();
        return isGhostModeActive() && !settings.sendReadMessages;
    }

    public static boolean shouldDropStoryReadRequest(TLObject request) {
        GhostSettings settings = loadSettings();
        return isGhostModeActive() && !settings.sendReadStories && request instanceof TL_stories.TL_stories_readStories;
    }

    public static boolean shouldRewritePresenceOffline(int currentAccount, TLObject request) {
        if (!(request instanceof TL_account.updateStatus)) {
            return false;
        }
        if (consumePresenceBypass(currentAccount)) {
            return false;
        }
        GhostSettings settings = loadSettings();
        return isGhostModeActive() && !settings.sendOnlinePackets;
    }

    public static void rewritePresenceOffline(TLObject request) {
        if (request instanceof TL_account.updateStatus) {
            ((TL_account.updateStatus) request).offline = true;
        }
    }

    public static boolean shouldMarkReadAfterAction(TLObject request) {
        GhostSettings settings = loadSettings();
        return isGhostModeActive()
            && !settings.sendReadMessages
            && settings.markReadAfterAction
            && isReadAfterActionRequest(request);
    }

    public static boolean shouldScheduleOfflineAfterAction(TLObject request) {
        GhostSettings settings = loadSettings();
        return isGhostModeActive()
            && settings.sendOfflineAfterOnline
            && isOnlineRevealingRequest(request);
    }

    public static long getActionDialogId(TLObject request) {
        return extractDialogId(request);
    }

    public static int getActionMessageId(TLObject request) {
        return extractMessageId(request);
    }

    public static void markDialogReadAfterAction(int currentAccount, long dialogId, int maxId) {
        if (dialogId == 0 || DialogObject.isEncryptedDialog(dialogId)) {
            return;
        }

        MessagesController messagesController = MessagesController.getInstance(currentAccount);
        TLRPC.InputPeer inputPeer = messagesController.getInputPeer(dialogId);
        if (inputPeer == null) {
            return;
        }

        if (maxId <= 0) {
            maxId = Integer.MAX_VALUE;
        }

        TLObject readRequest;
        if (inputPeer instanceof TLRPC.TL_inputPeerChannel) {
            TLRPC.TL_channels_readHistory channelRequest = new TLRPC.TL_channels_readHistory();
            channelRequest.channel = messagesController.getInputChannel(-dialogId);
            channelRequest.max_id = maxId;
            readRequest = channelRequest;
        } else {
            TLRPC.TL_messages_readHistory historyRequest = new TLRPC.TL_messages_readHistory();
            historyRequest.peer = inputPeer;
            historyRequest.max_id = maxId;
            readRequest = historyRequest;
        }

        allowNextReadRequest(currentAccount);
        ConnectionsManager.getInstance(currentAccount).sendRequest(readRequest, null, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    public static void scheduleOfflinePacket(int currentAccount) {
        synchronized (lock) {
            Runnable previous = offlineRunnables[currentAccount];
            if (previous != null) {
                AndroidUtilities.cancelRunOnUIThread(previous);
            }
            final Runnable[] holder = new Runnable[1];
            Runnable runnable = () -> {
                synchronized (lock) {
                    if (offlineRunnables[currentAccount] == holder[0]) {
                        offlineRunnables[currentAccount] = null;
                    }
                }
                sendOfflineNow(currentAccount);
            };
            holder[0] = runnable;
            offlineRunnables[currentAccount] = runnable;
            AndroidUtilities.runOnUIThread(runnable, 1500);
        }
    }

    public static void sendOfflineNow(int currentAccount) {
        TL_account.updateStatus request = new TL_account.updateStatus();
        request.offline = true;
        allowNextPresenceRequest(currentAccount);
        ConnectionsManager.getInstance(currentAccount).sendRequest(request, null, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    public static void sendOnlineNow(int currentAccount) {
        TL_account.updateStatus request = new TL_account.updateStatus();
        request.offline = false;
        allowNextPresenceRequest(currentAccount);
        ConnectionsManager.getInstance(currentAccount).sendRequest(request, null, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    private static boolean isTypingRequest(TLObject request) {
        return request instanceof TLRPC.TL_messages_setTyping
            || request instanceof TLRPC.TL_messages_setEncryptedTyping;
    }

    private static boolean isReadRequest(TLObject request) {
        return request instanceof TLRPC.TL_messages_readHistory
            || request instanceof TLRPC.TL_channels_readHistory
            || request instanceof TLRPC.TL_messages_readEncryptedHistory
            || request instanceof TLRPC.TL_messages_readDiscussion
            || request instanceof TLRPC.TL_messages_readSavedHistory;
    }

    private static boolean isReadAfterActionRequest(TLObject request) {
        return request instanceof TLRPC.TL_messages_sendMessage
            || request instanceof TLRPC.TL_messages_sendMedia
            || request instanceof TLRPC.TL_messages_sendMultiMedia
            || request instanceof TLRPC.TL_messages_sendInlineBotResult
            || request instanceof TLRPC.TL_messages_sendReaction
            || request instanceof TLRPC.TL_messages_sendVote;
    }

    private static boolean isOnlineRevealingRequest(TLObject request) {
        return isReadAfterActionRequest(request)
            || request instanceof TLRPC.TL_messages_editMessage
            || request instanceof TLRPC.TL_messages_readHistory
            || request instanceof TLRPC.TL_channels_readHistory
            || request instanceof TLRPC.TL_messages_readDiscussion
            || request instanceof TLRPC.TL_messages_readSavedHistory
            || request instanceof TL_stories.TL_stories_readStories
            || request instanceof TL_stories.TL_stories_sendReaction;
    }

    private static long extractDialogId(TLObject request) {
        TLRPC.InputPeer peer = TlUtils.getInputPeerFromSendMessageRequest(request);
        if (peer != null) {
            return DialogObject.getPeerDialogId(peer);
        }
        if (request instanceof TLRPC.TL_messages_setTyping) {
            return DialogObject.getPeerDialogId(((TLRPC.TL_messages_setTyping) request).peer);
        }
        if (request instanceof TLRPC.TL_messages_readHistory) {
            return DialogObject.getPeerDialogId(((TLRPC.TL_messages_readHistory) request).peer);
        }
        if (request instanceof TLRPC.TL_messages_readDiscussion) {
            return DialogObject.getPeerDialogId(((TLRPC.TL_messages_readDiscussion) request).peer);
        }
        if (request instanceof TLRPC.TL_messages_readSavedHistory) {
            return DialogObject.getPeerDialogId(((TLRPC.TL_messages_readSavedHistory) request).parent_peer);
        }
        if (request instanceof TLRPC.TL_messages_sendReaction) {
            return DialogObject.getPeerDialogId(((TLRPC.TL_messages_sendReaction) request).peer);
        }
        if (request instanceof TLRPC.TL_messages_sendVote) {
            return DialogObject.getPeerDialogId(((TLRPC.TL_messages_sendVote) request).peer);
        }
        if (request instanceof TL_stories.TL_stories_readStories) {
            return DialogObject.getPeerDialogId(((TL_stories.TL_stories_readStories) request).peer);
        }
        if (request instanceof TL_stories.TL_stories_sendReaction) {
            return DialogObject.getPeerDialogId(((TL_stories.TL_stories_sendReaction) request).peer);
        }
        return 0;
    }

    private static int extractMessageId(TLObject request) {
        if (request instanceof TLRPC.TL_messages_sendReaction) {
            return ((TLRPC.TL_messages_sendReaction) request).msg_id;
        }
        if (request instanceof TLRPC.TL_messages_sendVote) {
            return ((TLRPC.TL_messages_sendVote) request).msg_id;
        }
        if (request instanceof TLRPC.TL_messages_readDiscussion) {
            return ((TLRPC.TL_messages_readDiscussion) request).read_max_id;
        }
        if (request instanceof TLRPC.TL_messages_readHistory) {
            return ((TLRPC.TL_messages_readHistory) request).max_id;
        }
        if (request instanceof TLRPC.TL_messages_readSavedHistory) {
            return ((TLRPC.TL_messages_readSavedHistory) request).max_id;
        }
        if (request instanceof TL_stories.TL_stories_readStories) {
            return ((TL_stories.TL_stories_readStories) request).max_id;
        }
        return 0;
    }

    private static void allowNextReadRequest(int currentAccount) {
        synchronized (lock) {
            readRequestBypass[currentAccount]++;
        }
    }

    private static boolean consumeReadBypass(int currentAccount) {
        synchronized (lock) {
            if (readRequestBypass[currentAccount] > 0) {
                readRequestBypass[currentAccount]--;
                return true;
            }
            return false;
        }
    }

    private static void allowNextPresenceRequest(int currentAccount) {
        synchronized (lock) {
            presenceRequestBypass[currentAccount]++;
        }
    }

    private static boolean consumePresenceBypass(int currentAccount) {
        synchronized (lock) {
            if (presenceRequestBypass[currentAccount] > 0) {
                presenceRequestBypass[currentAccount]--;
                return true;
            }
            return false;
        }
    }
}
