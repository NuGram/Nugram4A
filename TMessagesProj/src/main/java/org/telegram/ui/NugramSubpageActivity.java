package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;

public class NugramSubpageActivity extends BaseFragment {

    private static final String PREF_ZALGO_REMOVER = "nugram_zalgo_remover";

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
            boolean zalgoRemoverEnabled = preferences.getBoolean(PREF_ZALGO_REMOVER, false);

            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            contentView.addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextCheckCell zalgoRemoverCell = new TextCheckCell(context);
            zalgoRemoverCell.setTextAndCheck(LocaleController.getString(R.string.NugramZalgoRemover), zalgoRemoverEnabled, false);
            zalgoRemoverCell.setOnClickListener(v -> {
                boolean enabled = !preferences.getBoolean(PREF_ZALGO_REMOVER, false);
                preferences.edit().putBoolean(PREF_ZALGO_REMOVER, enabled).apply();
                ((TextCheckCell) v).setChecked(enabled);
            });
            layout.addView(zalgoRemoverCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextInfoPrivacyCell infoCell = new TextInfoPrivacyCell(context);
            infoCell.setText(LocaleController.getString(R.string.NugramZalgoRemoverInfo));
            layout.addView(infoCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
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
