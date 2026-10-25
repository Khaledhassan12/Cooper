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
import android.widget.EditText;
import android.widget.CheckBox;
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
                v -> loadRepositories());
        addActionCard(getString(R.string.cooper_github_link_project), getString(R.string.cooper_github_link_project_description),
                v -> showNotReady("Project binding"));
        addActionCard(getString(R.string.cooper_github_create_repository), getString(R.string.cooper_github_create_repository_description),
                v -> showCreateRepositoryDialog());

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
        if (new GitCredentialStore(this).hasCredential()) {
            verifyGitHubAccount();
            return;
        }
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

    private GitHubApiClient apiClient() {
        return new GitHubApiClient(new GitCredentialStore(getApplicationContext()));
    }

    private void verifyGitHubAccount() {
        authExecutor.execute(() -> {
            try {
                org.json.JSONObject account = apiClient().getAuthenticatedAccount();
                String login = account.optString("login", "GitHub user");
                new Handler(Looper.getMainLooper()).post(() ->
                        Snackbar.make(content, getString(R.string.cooper_github_account_verified, login),
                                Snackbar.LENGTH_LONG).show());
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Account verification failed." : e.getMessage();
                new Handler(Looper.getMainLooper()).post(() ->
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.cooper_github_connection_title)
                                .setMessage(getString(R.string.cooper_github_account_verify_failed, message))
                                .setPositiveButton(R.string.common_word_ok, null).show());
            }
        });
    }

    private void loadRepositories() {
        if (!new GitCredentialStore(this).hasCredential()) {
            showNotReady(getString(R.string.cooper_github_connect));
            return;
        }
        authExecutor.execute(() -> {
            try {
                org.json.JSONArray repositories = apiClient().listRepositories(1, 50);
                java.util.ArrayList<String> labels = new java.util.ArrayList<>();
                java.util.ArrayList<String> urls = new java.util.ArrayList<>();
                for (int i = 0; i < repositories.length(); i++) {
                    org.json.JSONObject repository = repositories.getJSONObject(i);
                    String fullName = repository.optString("full_name", "");
                    if (fullName.isEmpty()) continue;
                    labels.add(fullName + (repository.optBoolean("private") ? "  •  Private" : "  •  Public"));
                    urls.add(repository.optString("html_url", ""));
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (labels.isEmpty()) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.cooper_github_repository_list_title)
                                .setMessage(R.string.cooper_github_repository_list_empty)
                                .setPositiveButton(R.string.common_word_ok, null).show();
                        return;
                    }
                    String[] items = labels.toArray(new String[0]);
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.cooper_github_repository_list_title)
                            .setItems(items, (dialog, which) -> {
                                String url = urls.get(which);
                                if (url.startsWith("https://github.com/")) {
                                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                                }
                            }).setNegativeButton(R.string.common_word_cancel, null).show();
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Repository request failed." : e.getMessage();
                new Handler(Looper.getMainLooper()).post(() ->
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.cooper_github_repository_list_title)
                                .setMessage(getString(R.string.cooper_github_repository_list_failed, message))
                                .setPositiveButton(R.string.common_word_ok, null).show());
            }
        });
    }

    private void showCreateRepositoryDialog() {
        if (!new GitCredentialStore(this).hasCredential()) {
            showNotReady(getString(R.string.cooper_github_connect));
            return;
        }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), dp(4));
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint(R.string.cooper_github_repository_name_hint);
        form.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        EditText description = new EditText(this);
        description.setHint(R.string.cooper_github_repository_description_hint);
        description.setMinLines(1);
        description.setMaxLines(3);
        form.addView(description, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        CheckBox privateRepository = new CheckBox(this);
        privateRepository.setText(R.string.cooper_github_repository_private);
        form.addView(privateRepository);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.cooper_github_repository_create_title)
                .setView(form)
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.cooper_github_create_repository, (dialog, which) -> {
                    String repositoryName = name.getText().toString().trim();
                    String repositoryDescription = description.getText().toString().trim();
                    boolean isPrivate = privateRepository.isChecked();
                    if (repositoryName.isEmpty()) {
                        Snackbar.make(content, R.string.cooper_github_repository_name_hint, Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    String visibility = isPrivate ? "private" : "public";
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.cooper_github_repository_create_title)
                            .setMessage(getString(R.string.cooper_github_repository_create_review, repositoryName, visibility))
                            .setNegativeButton(R.string.common_word_cancel, null)
                            .setPositiveButton(R.string.cooper_github_create_repository,
                                    (confirmDialog, confirmWhich) -> createRepository(repositoryName, repositoryDescription, isPrivate))
                            .show();
                }).show();
    }

    private void createRepository(String name, String description, boolean isPrivate) {
        authExecutor.execute(() -> {
            try {
                org.json.JSONObject created = apiClient().createRepository(name, description, isPrivate, true);
                String fullName = created.optString("full_name", "");
                String owner = created.optJSONObject("owner") == null ? "" :
                        created.optJSONObject("owner").optString("login", "");
                String repositoryName = created.optString("name", "");
                if (owner.isEmpty() || repositoryName.isEmpty()) {
                    throw new IllegalStateException("GitHub did not return repository identity for verification.");
                }
                org.json.JSONObject verified = apiClient().getRepository(owner, repositoryName);
                if (!fullName.equals(verified.optString("full_name", ""))) {
                    throw new IllegalStateException("The repository read-back did not match the created repository.");
                }
                String url = verified.optString("html_url", "");
                new Handler(Looper.getMainLooper()).post(() -> {
                    Snackbar.make(content, getString(R.string.cooper_github_repository_create_success, fullName),
                            Snackbar.LENGTH_LONG).show();
                    if (url.startsWith("https://github.com/")) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.cooper_github_repository_create_title)
                                .setMessage(url)
                                .setPositiveButton(R.string.cooper_github_open_settings, (dialog, which) ->
                                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))))
                                .setNegativeButton(R.string.common_word_ok, null).show();
                    }
                });
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Repository creation failed." : e.getMessage();
                new Handler(Looper.getMainLooper()).post(() ->
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.cooper_github_repository_create_title)
                                .setMessage(getString(R.string.cooper_github_repository_list_failed, message))
                                .setPositiveButton(R.string.common_word_ok, null).show());
            }
        });
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
