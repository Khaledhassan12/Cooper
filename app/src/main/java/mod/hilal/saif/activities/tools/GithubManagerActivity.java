package mod.hilal.saif.activities.tools;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.besome.sketch.lib.base.BaseAppCompatActivity;

import mod.hey.studios.util.Helper;
import pro.sketchware.R;

/**
 * Cooper GitHub Manager landing screen.
 *
 * This screen intentionally exposes only verified UI entry points until the local Git
 * backend and OAuth configuration are implemented. It never reports a remote operation
 * as successful based solely on a user action.
 */
public class GithubManagerActivity extends BaseAppCompatActivity {
    private LinearLayout content;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Github Manager");
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            return insets;
        });
        setContentView(root);

        addSectionTitle("Smart — ذكي وآمن");
        addInfoCard("GitHub account", "Not connected",
                "Secure sign-in is not configured yet. No credentials are stored by this screen.");
        addActionCard("Connect GitHub", "Open GitHub to review the account and app authorization setup.",
                v -> new MaterialAlertDialogBuilder(this)
                        .setTitle("GitHub connection")
                        .setMessage("OAuth must be configured for Cooper before in-app account access can be enabled. No token or client secret is embedded in this build.")
                        .setPositiveButton("Open GitHub", (dialog, which) ->
                                startActivity(new Intent(Intent.ACTION_VIEW,
                                        Uri.parse("https://github.com/settings/applications"))))
                        .setNegativeButton(R.string.common_word_cancel, null)
                        .show());

        addSectionTitle("Repositories");
        addActionCard("Browse repositories", "Repository listing becomes available after secure authentication is implemented.",
                v -> showNotReady("Repository browsing"));
        addActionCard("Link Cooper project", "Bind a Sketchware project to a verified repository without moving or deleting project files.",
                v -> showNotReady("Project binding"));
        addActionCard("Create repository", "Create a public or private repository after authentication and explicit confirmation.",
                v -> showNotReady("Repository creation"));

        addSectionTitle("Local Git");
        addInfoCard("Local repository status", "Not initialized",
                "This screen does not yet modify project files or initialize Git repositories.");
        addActionCard("Inspect changes", "View tracked, modified, deleted, and untracked files once the Android-compatible Git backend is integrated.",
                v -> showNotReady("Local Git status"));
        addActionCard("Commit and sync", "Stage, review, commit, fetch, push, and pull will require a verified operation plan.",
                v -> showNotReady("Commit and synchronization"));

        addSectionTitle("Safety");
        addInfoCard("Operation policy", "Smart — review before writes",
                "Remote writes, project-changing merges, and destructive operations must require explicit approval and post-operation verification.");
        addActionCard("View implementation status", "See which integration layers are available in this build.",
                v -> new MaterialAlertDialogBuilder(this)
                        .setTitle("Implementation status")
                        .setMessage("Available: manager entry point and UI scaffold. Pending: OAuth/PKCE, local Git backend, repository API services, persistent operation journal, secret scanning, sync/conflict handling, and automated verification tests.")
                        .setPositiveButton(R.string.common_word_ok, null)
                        .show());
    }

    private void addSectionTitle(String title) {
        TextView view = new TextView(this);
        view.setText(title);
        view.setTextSize(18);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setPadding(dp(4), dp(18), dp(4), dp(8));
        content.addView(view);
    }

    private void addInfoCard(String title, String status, String description) {
        MaterialCardView card = card();
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16), dp(14), dp(16), dp(14));
        TextView heading = text(title, 16, true);
        TextView state = text(status, 14, true);
        state.setPadding(0, dp(6), 0, dp(4));
        TextView detail = text(description, 13, false);
        column.addView(heading);
        column.addView(state);
        column.addView(detail);
        card.addView(column);
        content.addView(card);
    }

    private void addActionCard(String title, String description, View.OnClickListener listener) {
        MaterialCardView card = card();
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(listener);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(text(title, 15, true));
        TextView detail = text(description, 13, false);
        detail.setPadding(0, dp(5), 0, 0);
        column.addView(detail);
        row.addView(column, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView arrow = text("›", 26, false);
        row.addView(arrow);
        card.addView(row);
        content.addView(card);
    }

    private MaterialCardView card() {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(16));
        card.setCardElevation(dp(1));
        card.setUseCompatPadding(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        card.setLayoutParams(params);
        return card;
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void showNotReady(String feature) {
        Snackbar.make(content, feature + " is not available until its verified backend is implemented.", Snackbar.LENGTH_LONG).show();
    }
}
