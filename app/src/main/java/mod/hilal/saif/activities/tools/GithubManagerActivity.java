package mod.hilal.saif.activities.tools;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import pro.sketchware.BuildConfig;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Cooper GitHub Manager landing screen.
 *
 * This screen intentionally exposes only verified UI entry points until the local Git
 * backend and OAuth configuration are implemented. It never reports a remote operation
 * as successful based solely on a user action.
 */
public class GithubManagerActivity extends BaseAppCompatActivity {
    private LinearLayout content;
    private final ExecutorService authExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(R.string.cooper_github_manager_title);
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

        addSectionTitle(getString(R.string.cooper_github_smart_safe));
        boolean credentialStored = new GitCredentialStore(this).hasCredential();
        addInfoCard(getString(R.string.cooper_github_account),
                getString(credentialStored ? R.string.cooper_github_credential_saved : R.string.cooper_github_not_connected),
                getString(credentialStored ? R.string.cooper_github_credential_saved_desc : R.string.cooper_github_auth_not_configured));
        addActionCard(getString(R.string.cooper_github_connect), getString(R.string.cooper_github_connect_description),
                v -> startGitHubAuthorization());

        addSectionTitle(getString(R.string.cooper_github_repositories));
        addActionCard(getString(R.string.cooper_github_browse_repositories), getString(R.string.cooper_github_browse_repositories_description),
                v -> showNotReady("Repository browsing"));
        addActionCard(getString(R.string.cooper_github_link_project), getString(R.string.cooper_github_link_project_description),
                v -> showNotReady("Project binding"));
        addActionCard(getString(R.string.cooper_github_create_repository), getString(R.string.cooper_github_create_repository_description),
                v -> showNotReady("Repository creation"));

        addSectionTitle(getString(R.string.cooper_github_local_git));
        addInfoCard(getString(R.string.cooper_github_local_status), getString(R.string.cooper_github_not_initialized),
                getString(R.string.cooper_github_local_status_description));
        addActionCard(getString(R.string.cooper_github_inspect_changes), getString(R.string.cooper_github_inspect_changes_description),
                v -> showNotReady("Local Git status"));
        addActionCard(getString(R.string.cooper_github_commit_sync), getString(R.string.cooper_github_commit_sync_description),
                v -> showNotReady("Commit and synchronization"));

        addSectionTitle(getString(R.string.cooper_github_safety));
        addInfoCard(getString(R.string.cooper_github_operation_policy), getString(R.string.cooper_github_smart_review),
                getString(R.string.cooper_github_safety_description));
        addActionCard(getString(R.string.cooper_github_implementation_status), getString(R.string.cooper_github_implementation_status_description),
                v -> new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.cooper_github_status_dialog_title)
                        .setMessage(R.string.cooper_github_status_dialog_body)
                        .setPositiveButton(R.string.common_word_ok, null)
                        .show());
        handleOAuthCallback(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleOAuthCallback(intent);
    }

    private void startGitHubAuthorization() {
        if (BuildConfig.COOPER_GITHUB_CLIENT_ID == null || BuildConfig.COOPER_GITHUB_CLIENT_ID.trim().isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.cooper_github_connection_title)
                    .setMessage(R.string.cooper_github_oauth_not_configured)
                    .setPositiveButton(R.string.cooper_github_open_settings, (dialog, which) ->
                            startActivity(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/settings/developers"))))
                    .setNegativeButton(R.string.common_word_cancel, null)
                    .show();
            return;
        }
        try {
            Uri authorizationUrl = new GitHubAuthManager(this)
                    .beginAuthorization(BuildConfig.COOPER_GITHUB_CLIENT_ID);
            startActivity(new Intent(Intent.ACTION_VIEW, authorizationUrl));
        } catch (Exception e) {
            showAuthError(e.getMessage());
        }
    }

    private void handleOAuthCallback(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction()) || intent.getData() == null) return;
        Uri callback = intent.getData();
        if (!"cooper".equals(callback.getScheme()) || !"github-callback".equals(callback.getHost())) return;
        setIntent(new Intent(this, GithubManagerActivity.class));
        authExecutor.execute(() -> {
            try {
                new GitHubAuthManager(getApplicationContext()).completeAuthorization(
                        callback, BuildConfig.COOPER_GITHUB_CLIENT_ID);
                new Handler(Looper.getMainLooper()).post(() -> {
                    Snackbar.make(content, R.string.cooper_github_signin_success, Snackbar.LENGTH_LONG).show();
                    recreate();
                });
            } catch (Exception e) {
                String safeMessage = e.getMessage() == null ? "Authorization failed." : e.getMessage();
                new Handler(Looper.getMainLooper()).post(() -> showAuthError(safeMessage));
            }
        });
    }

    private void showAuthError(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.cooper_github_connection_title)
                .setMessage(getString(R.string.cooper_github_signin_failed, message))
                .setPositiveButton(R.string.common_word_ok, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        authExecutor.shutdown();
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
        Snackbar.make(content, getString(R.string.cooper_github_not_ready_message, feature), Snackbar.LENGTH_LONG).show();
    }
}
