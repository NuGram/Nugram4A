package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.utils.NugramHooks;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;

public class NugramSubpageActivity extends BaseFragment {

    private final int titleResId;

    public NugramSubpageActivity(int titleResId) {
        this.titleResId = titleResId;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(titleResId));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout contentView = new FrameLayout(context);
        contentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        if (titleResId == R.string.General) {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            boolean zalgoRemoverEnabled = preferences.getBoolean(NugramHooks.PREF_ZALGO_REMOVER, false);
            boolean restrictedForwardEnabled = preferences.getBoolean(NugramHooks.PREF_RESTRICTED_FORWARD, false);
            boolean ghostModeEnabled = NugramHooks.isGhostModeEnabled();

            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            contentView.addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextCheckCell zalgoRemoverCell = new TextCheckCell(context);
            zalgoRemoverCell.setTextAndCheck(LocaleController.getString(R.string.NugramZalgoRemover), zalgoRemoverEnabled, false);
            zalgoRemoverCell.setOnClickListener(v -> {
                boolean enabled = !preferences.getBoolean(NugramHooks.PREF_ZALGO_REMOVER, false);
                preferences.edit().putBoolean(NugramHooks.PREF_ZALGO_REMOVER, enabled).apply();
                ((TextCheckCell) v).setChecked(enabled);
                for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                    if (UserConfig.getInstance(account).isClientActivated()) {
                        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.notificationsSettingsUpdated);
                    }
                }
            });
            layout.addView(zalgoRemoverCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextInfoPrivacyCell infoCell = new TextInfoPrivacyCell(context);
            infoCell.setText(LocaleController.getString(R.string.NugramZalgoRemoverInfo));
            layout.addView(infoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextCheckCell restrictedForwardCell = new TextCheckCell(context);
            restrictedForwardCell.setTextAndCheck(LocaleController.getString(R.string.NugramRestrictedForward), restrictedForwardEnabled, false);
            restrictedForwardCell.setOnClickListener(v -> {
                boolean enabled = !preferences.getBoolean(NugramHooks.PREF_RESTRICTED_FORWARD, false);
                preferences.edit().putBoolean(NugramHooks.PREF_RESTRICTED_FORWARD, enabled).apply();
                ((TextCheckCell) v).setChecked(enabled);
            });
            layout.addView(restrictedForwardCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextInfoPrivacyCell restrictedForwardInfoCell = new TextInfoPrivacyCell(context);
            restrictedForwardInfoCell.setText(LocaleController.getString(R.string.NugramRestrictedForwardInfo));
            layout.addView(restrictedForwardInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextCheckCell ghostModeCell = new TextCheckCell(context);
            ghostModeCell.setTextAndCheck(LocaleController.getString(R.string.NugramGhostMode), ghostModeEnabled, false);
            ghostModeCell.setOnClickListener(v -> {
                boolean enabled = !NugramHooks.isGhostModeEnabled();
                org.telegram.messenger.utils.NugramGhostMode.setGhostModeEnabled(enabled);
                ((TextCheckCell) v).setChecked(NugramHooks.isGhostModeEnabled());
            });
            layout.addView(ghostModeCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextInfoPrivacyCell ghostModeInfoCell = new TextInfoPrivacyCell(context);
            ghostModeInfoCell.setText(LocaleController.getString(R.string.NugramGhostModeInfo));
            layout.addView(ghostModeInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else if (titleResId == R.string.Appearance) {
            SharedPreferences preferences = MessagesController.getGlobalMainSettings();
            boolean disableNumberRoundingEnabled = preferences.getBoolean(NugramHooks.PREF_DISABLE_NUMBER_ROUNDING, false);
            boolean timeWithSecondsEnabled = preferences.getBoolean(NugramHooks.PREF_TIME_WITH_SECONDS, false);
            boolean hidePhoneNumberEnabled = preferences.getBoolean(NugramHooks.PREF_HIDE_PHONE_NUMBER, false);

            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            contentView.addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextCheckCell disableNumberRoundingCell = new TextCheckCell(context);
            disableNumberRoundingCell.setTextAndCheck(LocaleController.getString(R.string.NugramDisableNumberRounding), disableNumberRoundingEnabled, false);
            disableNumberRoundingCell.setOnClickListener(v -> {
                boolean enabled = !preferences.getBoolean(NugramHooks.PREF_DISABLE_NUMBER_ROUNDING, false);
                preferences.edit().putBoolean(NugramHooks.PREF_DISABLE_NUMBER_ROUNDING, enabled).apply();
                ((TextCheckCell) v).setChecked(enabled);
                for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                    if (UserConfig.getInstance(account).isClientActivated()) {
                        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.notificationsSettingsUpdated);
                    }
                }
            });
            layout.addView(disableNumberRoundingCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextInfoPrivacyCell disableNumberRoundingInfoCell = new TextInfoPrivacyCell(context);
            disableNumberRoundingInfoCell.setText(LocaleController.getString(R.string.NugramDisableNumberRoundingInfo));
            layout.addView(disableNumberRoundingInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextCheckCell timeWithSecondsCell = new TextCheckCell(context);
            timeWithSecondsCell.setTextAndCheck(LocaleController.getString(R.string.NugramTimeWithSeconds), timeWithSecondsEnabled, false);
            timeWithSecondsCell.setOnClickListener(v -> {
                boolean enabled = !preferences.getBoolean(NugramHooks.PREF_TIME_WITH_SECONDS, false);
                preferences.edit().putBoolean(NugramHooks.PREF_TIME_WITH_SECONDS, enabled).apply();
                ((TextCheckCell) v).setChecked(enabled);
                for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                    if (UserConfig.getInstance(account).isClientActivated()) {
                        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.notificationsSettingsUpdated);
                    }
                }
            });
            layout.addView(timeWithSecondsCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextInfoPrivacyCell timeWithSecondsInfoCell = new TextInfoPrivacyCell(context);
            timeWithSecondsInfoCell.setText(LocaleController.getString(R.string.NugramTimeWithSecondsInfo));
            layout.addView(timeWithSecondsInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextCheckCell hidePhoneNumberCell = new TextCheckCell(context);
            hidePhoneNumberCell.setTextAndCheck(LocaleController.getString(R.string.NugramHidePhoneNumber), hidePhoneNumberEnabled, false);
            hidePhoneNumberCell.setOnClickListener(v -> {
                boolean enabled = !preferences.getBoolean(NugramHooks.PREF_HIDE_PHONE_NUMBER, false);
                preferences.edit().putBoolean(NugramHooks.PREF_HIDE_PHONE_NUMBER, enabled).apply();
                ((TextCheckCell) v).setChecked(enabled);
                for (int account = 0; account < UserConfig.MAX_ACCOUNT_COUNT; account++) {
                    if (UserConfig.getInstance(account).isClientActivated()) {
                        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.notificationsSettingsUpdated);
                    }
                }
            });
            layout.addView(hidePhoneNumberCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextInfoPrivacyCell hidePhoneNumberInfoCell = new TextInfoPrivacyCell(context);
            hidePhoneNumberInfoCell.setText(LocaleController.getString(R.string.NugramHidePhoneNumberInfo));
            layout.addView(hidePhoneNumberInfoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else {
            TextView emptyView = new TextView(context);
            emptyView.setText(LocaleController.getString(R.string.NugramComingSoon));
            emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            emptyView.setTextSize(16);
            emptyView.setGravity(android.view.Gravity.CENTER);
            contentView.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, android.view.Gravity.CENTER));
        }

        fragmentView = contentView;
        return fragmentView;
    }
}
