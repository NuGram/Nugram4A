package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class NugramSettingsActivity extends BaseFragment {

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.getString(R.string.Nugram));
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

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        contentView.addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 0, dp(12), 0, 0));

        SettingsActivity.SettingCell generalCell = new SettingsActivity.SettingCell(context, resourceProvider);
        generalCell.set(0xFF2E8B57, 0xFF1F6B43, R.drawable.settings_nugram_general,
            LocaleController.getString(R.string.General), null, null);
        generalCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        generalCell.setOnClickListener(v -> presentFragment(new NugramSubpageActivity(R.string.General)));
        layout.addView(generalCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        SettingsActivity.SettingCell appearanceCell = new SettingsActivity.SettingCell(context, resourceProvider);
        appearanceCell.set(0xFF8E5DFF, 0xFF5D3FD3, R.drawable.settings_nugram_appearance,
            LocaleController.getString(R.string.Appearance), null, null);
        appearanceCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        appearanceCell.setOnClickListener(v -> presentFragment(new NugramSubpageActivity(R.string.Appearance)));
        layout.addView(appearanceCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        View shadow = new View(context);
        shadow.setBackground(Theme.getThemedDrawableByKey(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        layout.addView(shadow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 12));

        TextView footerView = new TextView(context);
        footerView.setText(LocaleController.getString(R.string.NugramSupportPrompt));
        footerView.setTextSize(14);
        footerView.setGravity(Gravity.CENTER);
        footerView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        footerView.setPadding(dp(24), dp(16), dp(24), dp(24));
        contentView.addView(footerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        fragmentView = contentView;
        return fragmentView;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Override
    public boolean drawEdgeNavigationBar() {
        return false;
    }
}
