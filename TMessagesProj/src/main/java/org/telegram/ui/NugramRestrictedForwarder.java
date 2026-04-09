package org.telegram.ui;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessageSuggestionParams;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.utils.NugramHooks;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class NugramRestrictedForwarder {

    private static final long SEND_WAIT_TIMEOUT_MS = 120_000L;

    public static boolean maybeForwardMessages(
        ChatActivity chatActivity,
        ArrayList<MessageObject> messages,
        boolean forwardFromMyName,
        boolean hideCaption,
        boolean notify,
        int scheduleDate,
        int scheduleRepeatPeriod,
        MessageObject replyToTopMsg,
        long payStars,
        long monoForumPeerId,
        MessageSuggestionParams suggestionParams
    ) {
        return maybeForwardMessages(chatActivity, messages, forwardFromMyName, hideCaption, notify, scheduleDate, scheduleRepeatPeriod, replyToTopMsg, payStars, monoForumPeerId, suggestionParams, chatActivity.getDialogId());
    }

    public static boolean maybeForwardMessages(
        ChatActivity chatActivity,
        ArrayList<MessageObject> messages,
        boolean forwardFromMyName,
        boolean hideCaption,
        boolean notify,
        int scheduleDate,
        int scheduleRepeatPeriod,
        MessageObject replyToTopMsg,
        long payStars,
        long monoForumPeerId,
        MessageSuggestionParams suggestionParams,
        long targetDialogId
    ) {
        if (!NugramHooks.isRestrictedForwardEnabled() || messages == null || messages.isEmpty() || chatActivity.getParentActivity() == null) {
            return false;
        }

        boolean needsRestrictedFlow = false;
        for (int i = 0; i < messages.size(); i++) {
            if (needsRestrictedForward(messages.get(i))) {
                needsRestrictedFlow = true;
                break;
            }
        }
        if (!needsRestrictedFlow) {
            return false;
        }

        ArrayList<MessageObject> messagesCopy = new ArrayList<>(messages);
        int currentAccount = chatActivity.getCurrentAccount();
        boolean scheduled = scheduleDate != 0;
        AlertDialog progressDialog = new AlertDialog(chatActivity.getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER, chatActivity.themeDelegate);
        progressDialog.setCanCancel(false);
        progressDialog.setMessage(LocaleController.getString(R.string.NugramRestrictedForwardWorking));
        progressDialog.showDelayed(200);

        Utilities.globalQueue.postRunnable(() -> {
            int skippedCount = 0;
            int sentCount = 0;
            try {
                SendMessagesHelper sendMessagesHelper = SendMessagesHelper.getInstance(currentAccount);
                for (int i = 0; i < messagesCopy.size(); ) {
                    MessageObject messageObject = messagesCopy.get(i);
                    ArrayList<MessageObject> groupedRestrictedPhotos = collectGroupedRestrictedPhotos(messagesCopy, i);
                    if (groupedRestrictedPhotos != null) {
                        boolean dispatched = dispatchRestrictedPhotoGroup(
                            chatActivity,
                            groupedRestrictedPhotos,
                            targetDialogId,
                            hideCaption,
                            notify,
                            scheduleDate,
                            scheduleRepeatPeriod,
                            replyToTopMsg,
                            payStars,
                            monoForumPeerId,
                            suggestionParams
                        );
                        if (!dispatched) {
                            skippedCount += groupedRestrictedPhotos.size();
                        } else if (awaitSendResult(currentAccount, targetDialogId, scheduled)) {
                            sentCount += groupedRestrictedPhotos.size();
                        } else {
                            skippedCount += groupedRestrictedPhotos.size();
                        }
                        i += groupedRestrictedPhotos.size();
                        continue;
                    }

                    boolean restricted = needsRestrictedForward(messageObject);
                    boolean dispatched;
                    if (restricted) {
                        dispatched = dispatchRestrictedMessage(
                            chatActivity,
                            sendMessagesHelper,
                            messageObject,
                            targetDialogId,
                            hideCaption,
                            notify,
                            scheduleDate,
                            scheduleRepeatPeriod,
                            replyToTopMsg,
                            payStars,
                            monoForumPeerId,
                            suggestionParams
                        );
                    } else {
                        dispatched = dispatchNormalForward(
                            sendMessagesHelper,
                            messageObject,
                            targetDialogId,
                            forwardFromMyName,
                            hideCaption,
                            notify,
                            scheduleDate,
                            scheduleRepeatPeriod,
                            replyToTopMsg,
                            payStars,
                            monoForumPeerId,
                            suggestionParams
                        );
                    }

                    if (!dispatched) {
                        skippedCount++;
                        i++;
                        continue;
                    }

                    if (awaitSendResult(currentAccount, targetDialogId, scheduled)) {
                        sentCount++;
                    } else {
                        skippedCount++;
                    }
                    i++;
                }
            } finally {
                int finalSentCount = sentCount;
                int finalSkippedCount = skippedCount;
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        progressDialog.dismiss();
                    } catch (Exception ignore) {
                    }
                    chatActivity.hideFieldPanel(true);

                    if (finalSkippedCount > 0) {
                        BulletinFactory.of(chatActivity)
                            .createErrorBulletin(LocaleController.formatString(
                                R.string.NugramRestrictedForwardFinishedPartial,
                                finalSentCount,
                                finalSkippedCount
                            ))
                            .show();
                    } else if (finalSentCount > 0) {
                        BulletinFactory.of(chatActivity)
                            .createSimpleBulletin(
                                R.raw.forward,
                                LocaleController.formatString(R.string.NugramRestrictedForwardFinished, finalSentCount)
                            )
                            .show();
                    }
                });
            }
        });

        return true;
    }

    private static boolean dispatchNormalForward(
        SendMessagesHelper sendMessagesHelper,
        MessageObject messageObject,
        long targetDialogId,
        boolean forwardFromMyName,
        boolean hideCaption,
        boolean notify,
        int scheduleDate,
        int scheduleRepeatPeriod,
        MessageObject replyToTopMsg,
        long payStars,
        long monoForumPeerId,
        MessageSuggestionParams suggestionParams
    ) {
        CountDownLatch latch = new CountDownLatch(1);
        AndroidUtilities.runOnUIThread(() -> {
            try {
                sendMessagesHelper.sendMessage(
                    new ArrayList<>(Collections.singletonList(messageObject)),
                    targetDialogId,
                    forwardFromMyName,
                    hideCaption,
                    notify,
                    scheduleDate,
                    scheduleRepeatPeriod,
                    replyToTopMsg,
                    -1,
                    payStars,
                    monoForumPeerId,
                    suggestionParams
                );
            } finally {
                latch.countDown();
            }
        });
        awaitLatch(latch, 5_000L);
        return true;
    }

    private static boolean dispatchRestrictedMessage(
        ChatActivity chatActivity,
        SendMessagesHelper sendMessagesHelper,
        MessageObject messageObject,
        long targetDialogId,
        boolean hideCaption,
        boolean notify,
        int scheduleDate,
        int scheduleRepeatPeriod,
        MessageObject replyToTopMsg,
        long payStars,
        long monoForumPeerId,
        MessageSuggestionParams suggestionParams
    ) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return false;
        }

        if (messageObject.type == MessageObject.TYPE_TEXT || messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage) {
            return dispatchText(
                sendMessagesHelper,
                messageObject,
                targetDialogId,
                notify,
                scheduleDate,
                scheduleRepeatPeriod,
                replyToTopMsg,
                payStars,
                monoForumPeerId,
                suggestionParams
            );
        }

        if (messageObject.isSticker() || messageObject.isAnimatedSticker()) {
            TLRPC.Document document = messageObject.getDocument();
            if (document == null) {
                return false;
            }
            CountDownLatch latch = new CountDownLatch(1);
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    sendMessagesHelper.sendSticker(
                        document,
                        null,
                        targetDialogId,
                        null,
                        replyToTopMsg,
                        null,
                        null,
                        null,
                        notify,
                        scheduleDate,
                        scheduleRepeatPeriod,
                        false,
                        messageObject,
                        null,
                        0,
                        payStars,
                        monoForumPeerId,
                        suggestionParams
                    );
                } finally {
                    latch.countDown();
                }
            });
            return awaitLatch(latch, 5_000L);
        }

        String localPath = ensureLocalPath(messageObject);
        if (TextUtils.isEmpty(localPath)) {
            return false;
        }

        if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && !messageObject.isVideo()) {
            CountDownLatch latch = new CountDownLatch(1);
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    SendMessagesHelper.prepareSendingPhoto(
                        chatActivity.getAccountInstance(),
                        localPath,
                        null,
                        null,
                        targetDialogId,
                        null,
                        replyToTopMsg,
                        null,
                        null,
                        hideCaption ? null : messageObject.messageOwner.entities,
                        null,
                        null,
                        0,
                        null,
                        null,
                        notify,
                        scheduleDate,
                        scheduleRepeatPeriod,
                        0,
                        false,
                        hideCaption ? null : messageObject.messageOwner.message,
                        null,
                        0,
                        0,
                        payStars,
                        monoForumPeerId,
                        suggestionParams
                    );
                } finally {
                    latch.countDown();
                }
            });
            return awaitLatch(latch, 5_000L);
        }

        TLRPC.Document document = messageObject.getDocument();
        if (document == null) {
            return false;
        }

        CountDownLatch latch = new CountDownLatch(1);
        AndroidUtilities.runOnUIThread(() -> {
            try {
                ArrayList<String> paths = new ArrayList<>(1);
                paths.add(localPath);
                ArrayList<String> originalPaths = new ArrayList<>(1);
                originalPaths.add(localPath);
                SendMessagesHelper.prepareSendingDocuments(
                    chatActivity.getAccountInstance(),
                    paths,
                    originalPaths,
                    null,
                    hideCaption ? null : messageObject.messageOwner.message,
                    hideCaption ? null : messageObject.messageOwner.entities,
                    document.mime_type,
                    targetDialogId,
                    null,
                    replyToTopMsg,
                    null,
                    null,
                    null,
                    notify,
                    scheduleDate,
                    scheduleRepeatPeriod,
                    null,
                    null,
                    0,
                    0,
                    false,
                    payStars,
                    monoForumPeerId,
                    suggestionParams
                );
            } finally {
                latch.countDown();
            }
        });
        return awaitLatch(latch, 5_000L);
    }

    private static boolean dispatchRestrictedPhotoGroup(
        ChatActivity chatActivity,
        ArrayList<MessageObject> messages,
        long targetDialogId,
        boolean hideCaption,
        boolean notify,
        int scheduleDate,
        int scheduleRepeatPeriod,
        MessageObject replyToTopMsg,
        long payStars,
        long monoForumPeerId,
        MessageSuggestionParams suggestionParams
    ) {
        ArrayList<SendMessagesHelper.SendingMediaInfo> infos = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            MessageObject messageObject = messages.get(i);
            String localPath = ensureLocalPath(messageObject);
            if (TextUtils.isEmpty(localPath)) {
                return false;
            }
            SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
            info.path = localPath;
            info.entities = hideCaption ? null : messageObject.messageOwner.entities;
            info.caption = hideCaption ? null : messageObject.messageOwner.message;
            info.hasMediaSpoilers = messageObject.hasMediaSpoilers();
            infos.add(info);
        }

        CountDownLatch latch = new CountDownLatch(1);
        AndroidUtilities.runOnUIThread(() -> {
            try {
                SendMessagesHelper.prepareSendingMedia(
                    chatActivity.getAccountInstance(),
                    infos,
                    targetDialogId,
                    null,
                    replyToTopMsg,
                    null,
                    null,
                    false,
                    true,
                    null,
                    notify,
                    scheduleDate,
                    scheduleRepeatPeriod,
                    chatActivity.getChatMode(),
                    false,
                    null,
                    null,
                    0,
                    0,
                    false,
                    payStars,
                    monoForumPeerId,
                    suggestionParams
                );
            } finally {
                latch.countDown();
            }
        });
        return awaitLatch(latch, 5_000L);
    }

    private static boolean dispatchText(
        SendMessagesHelper sendMessagesHelper,
        MessageObject messageObject,
        long targetDialogId,
        boolean notify,
        int scheduleDate,
        int scheduleRepeatPeriod,
        MessageObject replyToTopMsg,
        long payStars,
        long monoForumPeerId,
        MessageSuggestionParams suggestionParams
    ) {
        TLRPC.WebPage webPage = messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage
            ? messageObject.messageOwner.media.webpage
            : null;
        CountDownLatch latch = new CountDownLatch(1);
        AndroidUtilities.runOnUIThread(() -> {
            try {
                SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                    !TextUtils.isEmpty(messageObject.messageOwner.message) ? messageObject.messageOwner.message : (messageObject.messageText != null ? messageObject.messageText.toString() : null),
                    targetDialogId,
                    null,
                    replyToTopMsg,
                    webPage,
                    webPage != null,
                    messageObject.messageOwner.entities,
                    null,
                    null,
                    notify,
                    scheduleDate,
                    scheduleRepeatPeriod,
                    null,
                    false
                );
                params.payStars = payStars;
                params.monoForumPeer = monoForumPeerId;
                params.suggestionParams = suggestionParams;
                sendMessagesHelper.sendMessage(params);
            } finally {
                latch.countDown();
            }
        });
        return awaitLatch(latch, 5_000L);
    }

    private static boolean needsRestrictedForward(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return false;
        }
        boolean sourceNoForwards = MessagesController.getInstance(messageObject.currentAccount).isPeerNoForwards(messageObject.getDialogId());
        if (sourceNoForwards || messageObject.messageOwner.noforwards) {
            return true;
        }
        if (DialogObject.isEncryptedDialog(messageObject.getDialogId()) || messageObject.messageOwner instanceof TLRPC.TL_message_secret) {
            return true;
        }
        if (messageObject.messageOwner.ttl != 0) {
            return true;
        }
        return messageObject.messageOwner.media != null
            && (messageObject.messageOwner.media.ttl_seconds != 0 || messageObject.type == MessageObject.TYPE_PAID_MEDIA);
    }

    private static String resolveLocalPath(MessageObject messageObject) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return null;
        }
        if (!TextUtils.isEmpty(messageObject.messageOwner.attachPath)) {
            File attachFile = new File(messageObject.messageOwner.attachPath);
            if (attachFile.exists()) {
                return attachFile.getAbsolutePath();
            }
        }

        FileLoader fileLoader = FileLoader.getInstance(messageObject.currentAccount);
        File path = fileLoader.getPathToMessage(messageObject.messageOwner, false);
        if (path != null && path.exists()) {
            return path.getAbsolutePath();
        }

        path = fileLoader.getPathToMessage(messageObject.messageOwner, true);
        if (path != null && path.exists()) {
            return path.getAbsolutePath();
        }

        TLRPC.Document document = messageObject.getDocument();
        if (document != null) {
            path = fileLoader.getPathToAttach(document, null, false, true);
            if (path != null && path.exists()) {
                return path.getAbsolutePath();
            }
            path = fileLoader.getPathToAttach(document, null, true, true);
            if (path != null && path.exists()) {
                return path.getAbsolutePath();
            }
        }

        return null;
    }

    private static String ensureLocalPath(MessageObject messageObject) {
        String localPath = resolveLocalPath(messageObject);
        if (!TextUtils.isEmpty(localPath)) {
            return localPath;
        }
        if (messageObject == null || messageObject.messageOwner == null) {
            return null;
        }

        NotificationCenter notificationCenter = NotificationCenter.getInstance(messageObject.currentAccount);
        CountDownLatch attached = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);
        final String[] expectedName = new String[1];

        TLRPC.Document document = messageObject.getDocument();
        TLRPC.PhotoSize photoSize = null;
        if (document != null) {
            expectedName[0] = FileLoader.getAttachFileName(document);
        } else if (messageObject.messageOwner.media != null && messageObject.messageOwner.media.photo != null) {
            photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.messageOwner.media.photo.sizes, AndroidUtilities.getPhotoSize(true));
            if (photoSize != null) {
                expectedName[0] = FileLoader.getAttachFileName(photoSize);
            }
        }
        if (TextUtils.isEmpty(expectedName[0])) {
            return null;
        }

        final TLRPC.PhotoSize finalPhotoSize = photoSize;
        NotificationCenter.NotificationCenterDelegate delegate = (id, account, args) -> {
            if (id == NotificationCenter.fileLoaded && args != null && args.length > 0 && expectedName[0].equals(args[0])) {
                success.set(true);
                completed.countDown();
            } else if (id == NotificationCenter.fileLoadFailed && args != null && args.length > 0 && expectedName[0].equals(args[0])) {
                completed.countDown();
            }
        };

        AndroidUtilities.runOnUIThread(() -> {
            notificationCenter.addObserver(delegate, NotificationCenter.fileLoaded);
            notificationCenter.addObserver(delegate, NotificationCenter.fileLoadFailed);
            try {
                FileLoader fileLoader = FileLoader.getInstance(messageObject.currentAccount);
                if (document != null) {
                    fileLoader.loadFile(document, messageObject, FileLoader.PRIORITY_HIGH, 0);
                } else if (finalPhotoSize != null && messageObject.messageOwner.media != null && messageObject.messageOwner.media.photo != null) {
                    fileLoader.loadFile(ImageLocation.getForPhoto(finalPhotoSize, messageObject.messageOwner.media.photo), messageObject, null, FileLoader.PRIORITY_HIGH, 0);
                } else {
                    completed.countDown();
                }
            } finally {
                attached.countDown();
            }
        });

        awaitLatch(attached, 5_000L);
        awaitLatch(completed, SEND_WAIT_TIMEOUT_MS);
        AndroidUtilities.runOnUIThread(() -> {
            notificationCenter.removeObserver(delegate, NotificationCenter.fileLoaded);
            notificationCenter.removeObserver(delegate, NotificationCenter.fileLoadFailed);
        });
        if (!success.get()) {
            return null;
        }
        return resolveLocalPath(messageObject);
    }

    private static ArrayList<MessageObject> collectGroupedRestrictedPhotos(ArrayList<MessageObject> messages, int startIndex) {
        if (messages == null || startIndex < 0 || startIndex >= messages.size()) {
            return null;
        }
        MessageObject first = messages.get(startIndex);
        if (first == null || first.getGroupId() == 0 || !needsRestrictedForward(first) || first.messageOwner == null || !(first.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) || first.isVideo()) {
            return null;
        }

        long groupId = first.getGroupId();
        ArrayList<MessageObject> result = new ArrayList<>();
        for (int i = startIndex; i < messages.size(); i++) {
            MessageObject messageObject = messages.get(i);
            if (messageObject == null || messageObject.getGroupId() != groupId || !needsRestrictedForward(messageObject) || messageObject.messageOwner == null || !(messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) || messageObject.isVideo()) {
                break;
            }
            result.add(messageObject);
        }
        return result.size() > 1 ? result : null;
    }

    private static boolean awaitSendResult(int currentAccount, long targetDialogId, boolean scheduled) {
        NotificationCenter notificationCenter = NotificationCenter.getInstance(currentAccount);
        CountDownLatch attached = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        NotificationCenter.NotificationCenterDelegate delegate = (id, account, args) -> {
            if (id == NotificationCenter.messageReceivedByServer2) {
                long dialogId = 0;
                boolean eventScheduled = false;
                if (args != null && args.length > 3 && args[3] instanceof Long) {
                    dialogId = (Long) args[3];
                }
                if (args != null && args.length > 6 && args[6] instanceof Boolean) {
                    eventScheduled = (Boolean) args[6];
                }
                if (dialogId == targetDialogId && eventScheduled == scheduled) {
                    success.set(true);
                    completed.countDown();
                }
            } else if (id == NotificationCenter.messageSendError) {
                completed.countDown();
            }
        };

        AndroidUtilities.runOnUIThread(() -> {
            notificationCenter.addObserver(delegate, NotificationCenter.messageReceivedByServer2);
            notificationCenter.addObserver(delegate, NotificationCenter.messageSendError);
            attached.countDown();
        });
        awaitLatch(attached, 5_000L);
        awaitLatch(completed, SEND_WAIT_TIMEOUT_MS);
        AndroidUtilities.runOnUIThread(() -> {
            notificationCenter.removeObserver(delegate, NotificationCenter.messageReceivedByServer2);
            notificationCenter.removeObserver(delegate, NotificationCenter.messageSendError);
        });
        return success.get();
    }

    private static boolean awaitLatch(CountDownLatch latch, long timeoutMs) {
        try {
            return latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
