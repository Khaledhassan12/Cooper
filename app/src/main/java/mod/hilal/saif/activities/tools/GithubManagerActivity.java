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
        if (new GitCredentialStore(this).hasCredential()) {
            addActionCard(getString(R.string.cooper_github_disconnect), getString(R.string.cooper_github_disconnect_confirm),
                    v -> confirmDisconnectGitHub());
        }

        addSectionTitle(getString(R.string.cooper_github_repositories));
        addActionCard(getString(R.string.cooper_github_browse_repositories), getString(R.string.cooper_github_browse_repositories_description),
                v -> loadRepositories());
        addActionCard(getString(R.string.cooper_github_link_project), getString(R.string.cooper_github_link_project_description),
                v -> loadProjectSelection());
        addActionCard(getString(R.string.cooper_github_create_repository), getString(R.string.cooper_github_create_repository_description),
                v -> showCreateRepositoryDialog());

        addSectionTitle(getString(R.string.cooper_github_pull_requests));
        addActionCard(getString(R.string.cooper_github_create_pull_request),
                "Create a Pull Request with explicit review and remote read-back verification.",
                v -> showCreatePullRequestDialog());

        addSectionTitle(getString(R.string.cooper_github_local_git));
        addInfoCard(getString(R.string.cooper_github_local_status), getString(R.string.cooper_github_not_initialized),
                getString(R.string.cooper_github_local_status_description));
        addActionCard(getString(R.string.cooper_github_inspect_changes), getString(R.string.cooper_github_inspect_changes_description),
                v -> inspectLocalGit());
        addActionCard(getString(R.string.cooper_github_commit_local), getString(R.string.cooper_github_commit_local_description),
                v -> inspectLocalGit());
        addActionCard(getString(R.string.cooper_github_fetch_remote), "Fetch remote refs without changing project files.",
                v -> runRemoteGitOperation("FETCH"));
        addActionCard(getString(R.string.cooper_github_push_remote), "Push the current committed branch after explicit approval and a secret scan.",
                v -> runRemoteGitOperation("PUSH"));
        addActionCard(getString(R.string.cooper_github_pull_remote), "Apply remote changes only when the local worktree is clean and you approve the operation.",
                v -> runRemoteGitOperation("PULL"));

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

    private void loadProjectSelection() {
        authExecutor.execute(() -> {
            try {
                java.util.ArrayList<java.util.HashMap<String, Object>> projects = a.a.a.lC.a();
                java.util.ArrayList<String> labels = new java.util.ArrayList<>();
                java.util.ArrayList<String> ids = new java.util.ArrayList<>();
                for (java.util.HashMap<String, Object> project : projects) {
                    String id = String.valueOf(project.get("sc_id"));
                    if (!id.matches("[0-9]{1,12}")) continue;
                    String workspace = String.valueOf(project.get("my_ws_name"));
                    String appName = String.valueOf(project.get("my_app_name"));
                    labels.add(workspace + " — " + appName + "  (ID " + id + ")");
                    ids.add(id);
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (labels.isEmpty()) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.cooper_github_project_picker_title)
                                .setMessage(R.string.cooper_github_project_picker_empty)
                                .setPositiveButton(R.string.common_word_ok, null).show();
                        return;
                    }
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.cooper_github_project_picker_title)
                            .setItems(labels.toArray(new String[0]), (dialog, which) -> bindProject(ids.get(which)))
                            .setNegativeButton(R.string.common_word_cancel, null).show();
                });
            } catch (Exception e) {
                showProjectBindingError(e);
            }
        });
    }

    private void bindProject(String scId) {
        authExecutor.execute(() -> {
            try {
                GitProjectBindingStore.Binding binding = new GitProjectBindingStore(getApplicationContext()).bind(scId);
                new Handler(Looper.getMainLooper()).post(() ->
                        Snackbar.make(content, getString(R.string.cooper_github_project_bound,
                                binding.workspaceName, binding.scId), Snackbar.LENGTH_LONG).show());
            } catch (Exception e) {
                showProjectBindingError(e);
            }
        });
    }

    private void inspectLocalGit() {
        authExecutor.execute(() -> {
            try {
                GitProjectBindingStore.Binding binding =
                        new GitProjectBindingStore(getApplicationContext()).getBoundProject();
                if (binding == null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            new MaterialAlertDialogBuilder(this)
                                    .setTitle(R.string.cooper_github_local_status_title)
                                    .setMessage(R.string.cooper_github_project_not_bound)
                                    .setPositiveButton(R.string.cooper_github_select_project,
                                            (dialog, which) -> loadProjectSelection())
                                    .setNegativeButton(R.string.common_word_cancel, null).show());
                    return;
                }
                LocalGitBackend.Snapshot snapshot = new LocalGitBackend().inspect(binding.projectRoot);
                new Handler(Looper.getMainLooper()).post(() -> showLocalStatus(binding, snapshot));
            } catch (Exception e) {
                showLocalGitError(e);
            }
        });
    }

    private void showLocalStatus(GitProjectBindingStore.Binding binding, LocalGitBackend.Snapshot snapshot) {
        if (!snapshot.initialized) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.cooper_github_local_status_title)
                    .setMessage(getString(R.string.cooper_github_local_status_detail,
                            binding.workspaceName, binding.projectRoot.getAbsolutePath(),
                            "—", "—", 0, 0, 0, 0, 0, 0))
                    .setPositiveButton(R.string.cooper_github_initialize_git, (dialog, which) ->
                            new MaterialAlertDialogBuilder(this)
                                    .setTitle(R.string.cooper_github_initialize_git)
                                    .setMessage(getString(R.string.cooper_github_initialize_git_confirm,
                                            binding.workspaceName))
                                    .setNegativeButton(R.string.common_word_cancel, null)
                                    .setPositiveButton(R.string.cooper_github_initialize_git,
                                            (confirm, pressed) -> initializeLocalGit(binding))
                                    .show())
                    .setNegativeButton(R.string.common_word_cancel, null).show();
            return;
        }
        String detail = getString(R.string.cooper_github_local_status_detail,
                binding.workspaceName, binding.projectRoot.getAbsolutePath(),
                snapshot.currentBranch, shortCommit(snapshot.headCommit),
                snapshot.staged.size(), snapshot.modified.size(), snapshot.added.size(),
                snapshot.removed.size(), snapshot.untracked.size(), snapshot.conflicting.size());
        StringBuilder paths = new StringBuilder(detail);
        appendPaths(paths, "Staged", snapshot.staged);
        appendPaths(paths, "Modified", snapshot.modified);
        appendPaths(paths, "Added", snapshot.added);
        appendPaths(paths, "Removed", snapshot.removed);
        appendPaths(paths, "Untracked", snapshot.untracked);
        appendPaths(paths, "Conflicts", snapshot.conflicting);
        MaterialAlertDialogBuilder statusDialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.cooper_github_local_status_title)
                .setMessage(paths.toString());
        boolean hasChanges = !snapshot.staged.isEmpty() || !snapshot.modified.isEmpty()
                || !snapshot.added.isEmpty() || !snapshot.removed.isEmpty() || !snapshot.untracked.isEmpty();
        if (!snapshot.conflicting.isEmpty()) {
            statusDialog.setPositiveButton(R.string.common_word_ok, null);
            statusDialog.setMessage(paths + "\n\n" + getString(R.string.cooper_github_conflicts_block_commit));
        } else if (hasChanges) {
            statusDialog.setPositiveButton(R.string.cooper_github_commit_local,
                    (dialog, which) -> showCommitDialog(binding, snapshot));
        } else {
            statusDialog.setPositiveButton(R.string.common_word_ok, null);
        }
        statusDialog.setNegativeButton(R.string.common_word_cancel, null).show();
    }

    private void showCommitDialog(GitProjectBindingStore.Binding binding, LocalGitBackend.Snapshot snapshot) {
        if (!snapshot.conflicting.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.cooper_github_commit_review_title)
                    .setMessage(R.string.cooper_github_conflicts_block_commit)
                    .setPositiveButton(R.string.common_word_ok, null).show();
            return;
        }
        java.util.TreeSet<String> paths = new java.util.TreeSet<>();
        paths.addAll(snapshot.staged);
        paths.addAll(snapshot.modified);
        paths.addAll(snapshot.added);
        paths.addAll(snapshot.removed);
        paths.addAll(snapshot.untracked);
        if (paths.isEmpty()) {
            Snackbar.make(content, R.string.cooper_github_no_changes, Snackbar.LENGTH_LONG).show();
            return;
        }

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(4));
        EditText message = new EditText(this);
        message.setHint(R.string.cooper_github_commit_message_hint);
        message.setSingleLine(true);
        form.addView(message, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        EditText authorName = new EditText(this);
        authorName.setHint(R.string.cooper_github_author_name_hint);
        authorName.setSingleLine(true);
        form.addView(authorName, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        EditText authorEmail = new EditText(this);
        authorEmail.setHint(R.string.cooper_github_author_email_hint);
        authorEmail.setSingleLine(true);
        authorEmail.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        form.addView(authorEmail, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView pathScroll = new ScrollView(this);
        LinearLayout pathList = new LinearLayout(this);
        pathList.setOrientation(LinearLayout.VERTICAL);
        java.util.ArrayList<CheckBox> checkBoxes = new java.util.ArrayList<>();
        java.util.HashSet<String> alreadyStaged = new java.util.HashSet<>();
        alreadyStaged.addAll(snapshot.staged);
        alreadyStaged.addAll(snapshot.added);
        alreadyStaged.addAll(snapshot.removed);
        for (String path : paths) {
            CheckBox checkBox = new CheckBox(this);
            checkBox.setText(path);
            boolean staged = alreadyStaged.contains(path);
            checkBox.setChecked(staged);
            checkBox.setEnabled(!staged);
            checkBoxes.add(checkBox);
            pathList.addView(checkBox);
        }
        pathScroll.addView(pathList);
        form.addView(pathScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.min(dp(260), dp(48) * Math.max(1, paths.size()))));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.cooper_github_commit_review_title)
                .setView(form)
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.cooper_github_commit_local, (dialog, which) -> {
                    String commitMessage = message.getText().toString().trim();
                    String name = authorName.getText().toString().trim();
                    String email = authorEmail.getText().toString().trim();
                    java.util.ArrayList<String> selected = new java.util.ArrayList<>();
                    int index = 0;
                    for (String path : paths) {
                        if (checkBoxes.get(index++).isChecked()) selected.add(path);
                    }
                    if (commitMessage.isEmpty() || name.isEmpty() || email.isEmpty() || selected.isEmpty()) {
                        Snackbar.make(content, R.string.cooper_github_commit_message_hint, Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    String reviewedPaths = android.text.TextUtils.join("\n", selected);
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.cooper_github_commit_review_title)
                            .setMessage(getString(R.string.cooper_github_commit_review_body, selected.size())
                                    + "\\n\\n" + reviewedPaths)
                            .setNegativeButton(R.string.common_word_cancel, null)
                            .setPositiveButton(R.string.cooper_github_commit_local,
                                    (confirm, pressed) -> performLocalCommit(binding, selected, commitMessage, name, email))
                            .show();
                }).show();
    }

    private void performLocalCommit(GitProjectBindingStore.Binding binding, java.util.List<String> paths,
                                    String message, String authorName, String authorEmail) {
        authExecutor.execute(() -> {
            GitOperationStore store = new GitOperationStore(getApplicationContext());
            String operationId = java.util.UUID.randomUUID().toString();
            long now = System.currentTimeMillis();
            GitOperationStateMachine.State state = GitOperationStateMachine.State.CREATED;
            try {
                store.upsert(new GitOperationStore.Record(operationId, "LOCAL_COMMIT", binding.scId, "",
                        "", state, now, now, "Commit plan created; no credentials or file contents recorded."));
                state = GitOperationStateMachine.transition(state, GitOperationStateMachine.State.INSPECTING);
                persistOperationState(store, operationId, binding, state, now, "Revalidating project and worktree.");
                GitProjectBindingStore.Binding current = new GitProjectBindingStore(getApplicationContext()).getBoundProject();
                if (current == null || !current.scId.equals(binding.scId)
                        || !current.projectRoot.getCanonicalPath().equals(binding.projectRoot.getCanonicalPath())) {
                    throw new IllegalStateException("The bound Cooper project changed after the commit plan was prepared.");
                }
                LocalGitBackend backend = new LocalGitBackend();
                LocalGitBackend.Snapshot currentSnapshot = backend.inspect(current.projectRoot);
                if (!currentSnapshot.initialized || !currentSnapshot.conflicting.isEmpty()) {
                    throw new IllegalStateException("The Git repository is unavailable or contains unresolved conflicts.");
                }
                state = GitOperationStateMachine.transition(state, GitOperationStateMachine.State.PLANNED);
                persistOperationState(store, operationId, binding, state, now, "Selected file plan validated.");
                state = GitOperationStateMachine.transition(state, GitOperationStateMachine.State.AWAITING_APPROVAL);
                persistOperationState(store, operationId, binding, state, now, "Waiting for the explicit confirmation recorded by the UI.");
                state = GitOperationStateMachine.transition(state, GitOperationStateMachine.State.EXECUTING);
                persistOperationState(store, operationId, binding, state, now, "Staging only the selected paths and creating a local commit.");
                LocalGitBackend.CommitOutcome outcome = backend.commitSelected(current.projectRoot, paths,
                        message, authorName, authorEmail, true);
                state = GitOperationStateMachine.transition(state, GitOperationStateMachine.State.VERIFYING);
                persistOperationState(store, operationId, binding, state, now, "Reading back the local commit.");
                LocalGitBackend.Snapshot verified = backend.inspect(current.projectRoot);
                if (!verified.initialized || !outcome.commitId.equals(verified.headCommit)) {
                    state = GitOperationStateMachine.State.RECOVERY_REQUIRED;
                    persistOperationState(store, operationId, binding, state, now, "Commit result could not be verified; inspect repository before retrying.");
                    throw new IllegalStateException("Commit was created but read-back verification failed. Do not retry blindly.");
                }
                state = GitOperationStateMachine.transition(state, GitOperationStateMachine.State.SUCCEEDED);
                persistOperationState(store, operationId, binding, state, now, "Verified local commit " + outcome.commitId);
                new Handler(Looper.getMainLooper()).post(() ->
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.cooper_github_commit_local)
                                .setMessage(getString(R.string.cooper_github_commit_success, shortCommit(outcome.commitId))
                                        + "\\n\\nRemote sync has not been performed.")
                                .setPositiveButton(R.string.common_word_ok, null).show());
            } catch (Exception e) {
                try {
                    if (state == GitOperationStateMachine.State.EXECUTING || state == GitOperationStateMachine.State.VERIFYING) {
                        state = GitOperationStateMachine.State.RECOVERY_REQUIRED;
                        persistOperationState(store, operationId, binding, state, now,
                                "Commit attempt ended without a verified success; inspect local Git state before retrying.");
                    } else if (state != GitOperationStateMachine.State.SUCCEEDED) {
                        state = GitOperationStateMachine.State.FAILED;
                        persistOperationState(store, operationId, binding, state, now, "Commit operation failed before verified success.");
                    }
                } catch (Exception ignored) { }
                String safeMessage = e.getMessage() == null ? "Local commit failed." : e.getMessage();
                new Handler(Looper.getMainLooper()).post(() ->
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.cooper_github_commit_local)
                                .setMessage(getString(R.string.cooper_github_commit_failed, safeMessage))
                                .setPositiveButton(R.string.common_word_ok, null).show());
            }
        });
    }

    private void persistOperationState(GitOperationStore store, String id,
                                       GitProjectBindingStore.Binding binding,
                                       GitOperationStateMachine.State state, long createdAt,
                                       String diagnostic) throws java.io.IOException {
        store.upsert(new GitOperationStore.Record(id, "LOCAL_COMMIT", binding.scId, "",
                "", state, createdAt, System.currentTimeMillis(), diagnostic));
    }

    private void initializeLocalGit(GitProjectBindingStore.Binding binding) {
        authExecutor.execute(() -> {
            try {
                new LocalGitBackend().initialize(binding.projectRoot, true);
                LocalGitBackend.Snapshot verified = new LocalGitBackend().inspect(binding.projectRoot);
                if (!verified.initialized) throw new IllegalStateException("Git initialization read-back failed.");
                if (!binding.remoteUrl().isEmpty()) {
                    new LocalGitBackend().configureRemote(binding.projectRoot, binding.remoteUrl(), true);
                }
                new Handler(Looper.getMainLooper()).post(() -> {
                    Snackbar.make(content, R.string.cooper_github_initialize_git_success, Snackbar.LENGTH_LONG).show();
                    showLocalStatus(binding, verified);
                });
            } catch (Exception e) {
                showLocalGitError(e);
            }
        });
    }

    private void showProjectBindingError(Exception error) {
        String message = error.getMessage() == null ? "Project binding failed." : error.getMessage();
        new Handler(Looper.getMainLooper()).post(() ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.cooper_github_project_picker_title)
                        .setMessage(getString(R.string.cooper_github_project_binding_failed, message))
                        .setPositiveButton(R.string.common_word_ok, null).show());
    }

    private void showLocalGitError(Exception error) {
        String message = error.getMessage() == null ? "Local Git operation failed." : error.getMessage();
        new Handler(Looper.getMainLooper()).post(() ->
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.cooper_github_local_status_title)
                        .setMessage(getString(R.string.cooper_github_local_git_failed, message))
                        .setPositiveButton(R.string.common_word_ok, null).show());
    }

    private static String shortCommit(String commit) {
        if (commit == null || commit.isEmpty()) return "—";
        return commit.length() > 12 ? commit.substring(0, 12) : commit;
    }

    private static void appendPaths(StringBuilder builder, String label, java.util.List<String> paths) {
        if (paths.isEmpty()) return;
        builder.append("\n\n").append(label).append(":");
        for (String path : paths) builder.append("\n• ").append(path);
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
            startGitHubAuthorization();
            return;
        }
        authExecutor.execute(() -> {
            try {
                org.json.JSONArray repositories = apiClient().listRepositories(1, 50);
                java.util.ArrayList<String> labels = new java.util.ArrayList<>();
                java.util.ArrayList<String> urls = new java.util.ArrayList<>();
                java.util.ArrayList<String> fullNames = new java.util.ArrayList<>();
                for (int i = 0; i < repositories.length(); i++) {
                    org.json.JSONObject repository = repositories.getJSONObject(i);
                    String fullName = repository.optString("full_name", "");
                    if (fullName.isEmpty()) continue;
                    labels.add(fullName + (repository.optBoolean("private") ? "  •  Private" : "  •  Public"));
                    urls.add(repository.optString("html_url", ""));
                    fullNames.add(fullName);
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
                            .setItems(items, (dialog, which) ->
                                    showRepositoryActions(fullNames.get(which), urls.get(which)))
                            .setNegativeButton(R.string.common_word_cancel, null).show();
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

    private void confirmDisconnectGitHub() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.cooper_github_disconnect)
                .setMessage(R.string.cooper_github_disconnect_confirm)
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.cooper_github_disconnect, (dialog, which) -> {
                    new GitCredentialStore(getApplicationContext()).clear();
                    Snackbar.make(content, R.string.cooper_github_disconnected, Snackbar.LENGTH_LONG).show();
                    recreate();
                }).show();
    }

    private void showCreatePullRequestDialog() {
        if (!new GitCredentialStore(this).hasCredential()) {
            startGitHubAuthorization();
            return;
        }
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(24), dp(8), dp(24), dp(4));
        EditText owner = field(form, R.string.cooper_github_pr_owner_hint, true);
        EditText repository = field(form, R.string.cooper_github_pr_repository_hint, true);
        EditText head = field(form, R.string.cooper_github_pr_head_hint, true);
        EditText base = field(form, R.string.cooper_github_pr_base_hint, true);
        EditText title = field(form, R.string.cooper_github_pr_title_hint, true);
        EditText body = field(form, R.string.cooper_github_pr_body_hint, false);
        body.setMinLines(2);
        body.setMaxLines(4);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.cooper_github_create_pull_request)
                .setView(form)
                .setNegativeButton(R.string.common_word_cancel, null)
                .setPositiveButton(R.string.cooper_github_create_pull_request, (dialog, which) -> {
                    String repoOwner = owner.getText().toString().trim();
                    String repoName = repository.getText().toString().trim();
                    String source = head.getText().toString().trim();
                    String target = base.getText().toString().trim();
                    String prTitle = title.getText().toString().trim();
                    String prBody = body.getText().toString();
                    if (repoOwner.isEmpty() || repoName.isEmpty() || source.isEmpty()
                            || target.isEmpty() || prTitle.isEmpty() || source.equals(target)) {
                        Snackbar.make(content, R.string.cooper_github_create_pull_request, Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.cooper_github_create_pull_request)
                            .setMessage(getString(R.string.cooper_github_pr_review, source, target, repoOwner, repoName)
                                    + "\n\n" + prTitle + "\n\n" + prBody)
                            .setNegativeButton(R.string.common_word_cancel, null)
                            .setPositiveButton(R.string.cooper_github_create_pull_request,
                                    (confirm, pressed) -> createPullRequest(repoOwner, repoName, prTitle, prBody, source, target))
                            .show();
                }).show();
    }

    private EditText field(LinearLayout form, int hint, boolean singleLine) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setSingleLine(singleLine);
        form.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return field;
    }

    private void createPullRequest(String owner, String repository, String title,
                                   String body, String head, String base) {
        authExecutor.execute(() -> {
            try {
                org.json.JSONObject created = apiClient().createPullRequest(
                        owner, repository, title, body, head, base, true);
                int number = created.optInt("number", 0);
                if (number < 1) throw new IllegalStateException("GitHub did not return a valid Pull Request number.");
                org.json.JSONObject verified = apiClient().getPullRequest(owner, repository, number);
                if (verified.optInt("number", 0) != number
                        || !head.equals(verified.optJSONObject("head") == null ? "" :
                        verified.optJSONObject("head").optString("ref", ""))
                        || !base.equals(verified.optJSONObject("base") == null ? "" :
                        verified.optJSONObject("base").optString("ref", ""))) {
                    throw new IllegalStateException("Pull Request read-back did not match the requested branches.");
                }
                String url = verified.optString("html_url", "");
                new Handler(Looper.getMainLooper()).post(() ->
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.cooper_github_create_pull_request)
                                .setMessage(getString(R.string.cooper_github_pr_created, number) + "\n" + url)
                                .setPositiveButton(R.string.cooper_github_open_settings, (dialog, which) -> {
                                    if (url.startsWith("https://github.com/")) {
                                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                                    }
                                })
                                .setNegativeButton(R.string.common_word_ok, null).show());
            } catch (Exception e) {
                String message = e.getMessage() == null ? "Pull Request operation failed." : e.getMessage();
                new Handler(Looper.getMainLooper()).post(() ->
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.cooper_github_create_pull_request)
                                .setMessage(getString(R.string.cooper_github_pr_failed, message))
                                .setPositiveButton(R.string.common_word_ok, null).show());
            }
        });
    }

    private void showRepositoryActions(String fullName, String url) {
        String[] options = new String[] {
                getString(R.string.cooper_github_open_settings),
                getString(R.string.cooper_github_link_remote)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(fullName)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        if (url.startsWith("https://github.com/")) {
                            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        }
                    } else {
                        String[] parts = fullName.split("/", 2);
                        if (parts.length == 2) linkRemoteToBoundProject(parts[0], parts[1]);
                    }
                })
                .setNegativeButton(R.string.common_word_cancel, null).show();
    }

    private void linkRemoteToBoundProject(String owner, String repository) {
        authExecutor.execute(() -> {
            try {
                GitProjectBindingStore store = new GitProjectBindingStore(getApplicationContext());
                GitProjectBindingStore.Binding binding = store.getBoundProject();
                if (binding == null) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            new MaterialAlertDialogBuilder(this)
                                    .setTitle(R.string.cooper_github_link_remote)
                                    .setMessage(R.string.cooper_github_project_not_bound)
                                    .setPositiveButton(R.string.cooper_github_select_project,
                                            (dialog, which) -> loadProjectSelection())
                                    .setNegativeButton(R.string.common_word_cancel, null).show());
                    return;
                }
                new Handler(Looper.getMainLooper()).post(() ->
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.cooper_github_link_remote)
                                .setMessage(getString(R.string.cooper_github_link_remote_confirm,
                                        owner, repository, binding.workspaceName))
                                .setNegativeButton(R.string.common_word_cancel, null)
                                .setPositiveButton(R.string.cooper_github_link_remote,
                                        (dialog, which) -> persistRemoteBinding(owner, repository, binding))
                                .show());
            } catch (Exception e) {
                showProjectBindingError(e);
            }
        });
    }

    private void persistRemoteBinding(String owner, String repository, GitProjectBindingStore.Binding binding) {
        authExecutor.execute(() -> {
            try {
                GitProjectBindingStore store = new GitProjectBindingStore(getApplicationContext());
                store.linkRemote(owner, repository);
                LocalGitBackend backend = new LocalGitBackend();
                LocalGitBackend.Snapshot snapshot = backend.inspect(binding.projectRoot);
                if (snapshot.initialized) {
                    backend.configureRemote(binding.projectRoot,
                            "https://github.com/" + owner + "/" + repository + ".git", true);
                }
                new Handler(Looper.getMainLooper()).post(() ->
                        Snackbar.make(content, getString(R.string.cooper_github_remote_linked,
                                owner + "/" + repository, binding.workspaceName), Snackbar.LENGTH_LONG).show());
            } catch (Exception e) {
                showLocalGitError(e);
            }
        });
    }

    private void runRemoteGitOperation(String operation) {
        authExecutor.execute(() -> {
            try {
                GitProjectBindingStore.Binding binding =
                        new GitProjectBindingStore(getApplicationContext()).getBoundProject();
                if (binding == null) throw new IllegalStateException("Select a Cooper project first.");
                if (binding.remoteUrl().isEmpty()) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            new MaterialAlertDialogBuilder(this)
                                    .setTitle(R.string.cooper_github_link_remote)
                                    .setMessage(R.string.cooper_github_remote_not_linked)
                                    .setPositiveButton(R.string.cooper_github_browse_repositories,
                                            (dialog, which) -> loadRepositories())
                                    .setNegativeButton(R.string.common_word_cancel, null).show());
                    return;
                }
                LocalGitBackend backend = new LocalGitBackend();
                LocalGitBackend.Snapshot snapshot = backend.inspect(binding.projectRoot);
                if (!snapshot.initialized) throw new IllegalStateException("Initialize local Git before remote synchronization.");
                if (operation.equals("FETCH")) {
                    String token = new GitCredentialStore(getApplicationContext()).readAccessToken();
                    backend.configureRemote(binding.projectRoot, binding.remoteUrl(), true);
                    backend.fetch(binding.projectRoot, token);
                    new Handler(Looper.getMainLooper()).post(() ->
                            Snackbar.make(content, R.string.cooper_github_fetch_success, Snackbar.LENGTH_LONG).show());
                    return;
                }
                if (operation.equals("PUSH")) {
                    if (!snapshot.conflicting.isEmpty() || !snapshot.modified.isEmpty() || !snapshot.added.isEmpty()
                            || !snapshot.removed.isEmpty() || !snapshot.untracked.isEmpty() || !snapshot.staged.isEmpty()) {
                        throw new IllegalStateException("Push is blocked until all local changes are reviewed and committed.");
                    }
                    String token = new GitCredentialStore(getApplicationContext()).readAccessToken();
                    new Handler(Looper.getMainLooper()).post(() ->
                            new MaterialAlertDialogBuilder(this)
                                    .setTitle(R.string.cooper_github_push_remote)
                                    .setMessage(getString(R.string.cooper_github_push_confirm,
                                            snapshot.currentBranch, shortCommit(snapshot.headCommit),
                                            binding.remoteOwner + "/" + binding.remoteRepository))
                                    .setNegativeButton(R.string.common_word_cancel, null)
                                    .setPositiveButton(R.string.cooper_github_push_remote,
                                            (dialog, which) -> performRemotePush(binding, snapshot, token))
                                    .show());
                    return;
                }
                if (operation.equals("PULL")) {
                    if (!snapshot.conflicting.isEmpty() || !snapshot.modified.isEmpty() || !snapshot.added.isEmpty()
                            || !snapshot.removed.isEmpty() || !snapshot.untracked.isEmpty() || !snapshot.staged.isEmpty()) {
                        throw new IllegalStateException("Pull is blocked until all local changes are reviewed and committed.");
                    }
                    String token = new GitCredentialStore(getApplicationContext()).readAccessToken();
                    new Handler(Looper.getMainLooper()).post(() ->
                            new MaterialAlertDialogBuilder(this)
                                    .setTitle(R.string.cooper_github_pull_remote)
                                    .setMessage(getString(R.string.cooper_github_pull_confirm,
                                            snapshot.currentBranch, binding.remoteOwner + "/" + binding.remoteRepository))
                                    .setNegativeButton(R.string.common_word_cancel, null)
                                    .setPositiveButton(R.string.cooper_github_pull_remote,
                                            (dialog, which) -> performRemotePull(binding, snapshot, token))
                                    .show());
                }
            } catch (Exception e) {
                showLocalGitError(e);
            }
        });
    }

    private void performRemotePush(GitProjectBindingStore.Binding binding,
                                   LocalGitBackend.Snapshot planned, String token) {
        authExecutor.execute(() -> {
            try {
                LocalGitBackend backend = new LocalGitBackend();
                backend.configureRemote(binding.projectRoot, binding.remoteUrl(), true);
                String expected = backend.push(binding.projectRoot, token, true);
                org.json.JSONObject remoteBranch = apiClient().getBranch(
                        binding.remoteOwner, binding.remoteRepository, planned.currentBranch);
                org.json.JSONObject commit = remoteBranch.optJSONObject("commit");
                String remoteSha = commit == null ? "" : commit.optString("sha", "");
                if (!expected.equals(remoteSha)) {
                    throw new IllegalStateException("Push result is not yet verified on GitHub. Check the remote branch before retrying.");
                }
                new Handler(Looper.getMainLooper()).post(() ->
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.cooper_github_push_remote)
                                .setMessage(getString(R.string.cooper_github_push_success, shortCommit(expected)))
                                .setPositiveButton(R.string.common_word_ok, null).show());
            } catch (Exception e) {
                showLocalGitError(e);
            }
        });
    }

    private void performRemotePull(GitProjectBindingStore.Binding binding,
                                   LocalGitBackend.Snapshot planned, String token) {
        authExecutor.execute(() -> {
            try {
                LocalGitBackend backend = new LocalGitBackend();
                backend.configureRemote(binding.projectRoot, binding.remoteUrl(), true);
                String expected = backend.pull(binding.projectRoot, token, true);
                LocalGitBackend.Snapshot verified = backend.inspect(binding.projectRoot);
                if (!expected.equals(verified.headCommit)) {
                    throw new IllegalStateException("Pull result could not be verified against local HEAD.");
                }
                new Handler(Looper.getMainLooper()).post(() ->
                        new MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.cooper_github_pull_remote)
                                .setMessage(getString(R.string.cooper_github_pull_success, shortCommit(expected)))
                                .setPositiveButton(R.string.common_word_ok, null).show());
            } catch (Exception e) {
                showLocalGitError(e);
            }
        });
    }

    private void showCreateRepositoryDialog() {
        if (!new GitCredentialStore(this).hasCredential()) {
            startGitHubAuthorization();
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
