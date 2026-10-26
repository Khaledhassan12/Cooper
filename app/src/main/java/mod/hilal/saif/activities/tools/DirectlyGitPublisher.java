package mod.hilal.saif.activities.tools;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.eclipse.jgit.api.Git;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Directly Git publishing pipeline. Every network call and filesystem operation is
 * synchronous by design and MUST be invoked on a worker thread.
 */
public final class DirectlyGitPublisher {
    public interface ProgressListener {
        void onPhase(String phase);
    }

    public static final class Plan {
        public final GitProjectBindingStore.Binding binding;
        public final String repositoryName;
        public final String description;
        public final String readmeMode;
        public final String readmeContent;
        public final List<Uri> images;
        public final List<String> imageExtensions;
        public final List<String> files;
        public final int excludedCount;
        private final String owner;
        private final String authorName;
        private final String authorEmail;
        private final boolean initialized;
        private final String existingHead;

        private Plan(GitProjectBindingStore.Binding binding, String repositoryName, String description,
                     String readmeMode, String readmeContent, List<Uri> images, List<String> imageExtensions, List<String> files,
                     int excludedCount, String owner, String authorName, String authorEmail,
                     boolean initialized, String existingHead) {
            this.binding = binding;
            this.repositoryName = repositoryName;
            this.description = description;
            this.readmeMode = readmeMode;
            this.readmeContent = readmeContent;
            this.images = Collections.unmodifiableList(new ArrayList<>(images));
            this.imageExtensions = Collections.unmodifiableList(new ArrayList<>(imageExtensions));
            this.files = Collections.unmodifiableList(new ArrayList<>(files));
            this.excludedCount = excludedCount;
            this.owner = owner;
            this.authorName = authorName;
            this.authorEmail = authorEmail;
            this.initialized = initialized;
            this.existingHead = existingHead;
        }

        public String getOwner() { return owner; }
        public String getSummary() {
            StringBuilder value = new StringBuilder();
            value.append("Project: ").append(binding.workspaceName).append(" (ID ").append(binding.scId).append(")\n")
                    .append("Repository: ").append(owner).append("/").append(repositoryName).append("\n")
                    .append("README mode: ").append(readmeMode).append("\n")
                    .append("Images: ").append(images.size()).append("\n")
                    .append("Included files: ").append(files.size()).append("\n")
                    .append("Excluded generated/cache files: ").append(excludedCount).append("\n\n");
            int limit = Math.min(files.size(), 160);
            for (int i = 0; i < limit; i++) value.append("• ").append(files.get(i)).append("\n");
            if (files.size() > limit) value.append("… and ").append(files.size() - limit).append(" more file(s).\n");
            return value.toString();
        }
    }

    public static final class Result {
        public final String repositoryUrl;
        public final String commitSha;
        public final String branch;
        public Result(String repositoryUrl, String commitSha, String branch) {
            this.repositoryUrl = repositoryUrl;
            this.commitSha = commitSha;
            this.branch = branch;
        }
    }

    public static final class PublishException extends Exception {
        public final boolean remoteCreated;
        public final String repositoryUrl;
        public PublishException(String message, boolean remoteCreated, String repositoryUrl, Throwable cause) {
            super(message, cause);
            this.remoteCreated = remoteCreated;
            this.repositoryUrl = repositoryUrl == null ? "" : repositoryUrl;
        }
    }

    private final Context context;
    private final String clientId;
    private final GitProjectBindingStore bindings;
    private final GitHubApiClient api;
    private final GitHubAuthManager auth;
    private final LocalGitBackend backend;
    private final SharedPreferences published;

    public DirectlyGitPublisher(Context context, String clientId) {
        if (context == null) throw new IllegalArgumentException("Context is required.");
        this.context = context.getApplicationContext();
        this.clientId = clientId == null ? "" : clientId.trim();
        this.bindings = new GitProjectBindingStore(this.context);
        this.auth = new GitHubAuthManager(this.context);
        this.api = new GitHubApiClient(auth, this.clientId);
        this.backend = new LocalGitBackend();
        this.published = this.context.getSharedPreferences("cooper_directly_git_publications", Context.MODE_PRIVATE);
    }

    public Plan prepare(GitProjectBindingStore.Binding selected, String name, String description,
                        String readmeMode, String readmeContent, List<Uri> images) throws Exception {
        if (selected == null) throw new IllegalArgumentException("Select a Cooper project first.");
        if (name == null || !name.matches("[A-Za-z0-9_.-]{1,100}") || name.equals(".") || name.equals("..")
                || name.startsWith(".") || name.endsWith(".") || name.toLowerCase(Locale.ROOT).endsWith(".git")) {
            throw new IllegalArgumentException("Enter a valid GitHub repository name (1–100 characters).");
        }
        String safeDescription = description == null ? "" : description.trim();
        if (safeDescription.length() > 350) throw new IllegalArgumentException("Repository descriptions are limited to 350 characters.");
        if (!"EMPTY".equals(readmeMode) && !"MARKDOWN".equals(readmeMode) && !"TEXT".equals(readmeMode)) {
            throw new IllegalArgumentException("Choose README or Empty before publishing.");
        }
        if (images == null || images.size() > 6) throw new IllegalArgumentException("Select no more than six images.");
        if (!new GitCredentialStore(context).hasCredential()) throw new IllegalStateException("Connect GitHub before publishing.");
        String token = auth.getUsableAccessToken(clientId);
        if (token == null || token.trim().isEmpty()) throw new IllegalStateException("GitHub credentials are no longer valid. Reconnect and retry.");
        JSONObject account = api.getAuthenticatedAccount();
        String owner = account.optString("login", "").trim();
        if (owner.isEmpty()) throw new IllegalStateException("GitHub did not return the authenticated account identity.");
        GitProjectBindingStore.Binding current = bindings.getBoundProject();
        if (current == null || !selected.scId.equals(current.scId)
                || !selected.projectRoot.getCanonicalPath().equals(current.projectRoot.getCanonicalPath())) {
            throw new IllegalStateException("The selected Cooper project changed. Select it again before publishing.");
        }
        if (!current.remoteUrl().isEmpty()) throw new IllegalStateException("This project already has a remote repository linked. Use GitHub Manager to inspect it.");
        if (published.contains("project_" + current.scId)) {
            throw new IllegalStateException("This Cooper project has already been published by Directly Git. Use the existing repository instead of creating a duplicate.");
        }
        ensureRepositoryDoesNotExist(owner, name);
        LocalGitBackend.Snapshot snapshot = backend.inspect(current.projectRoot);
        if (snapshot.initialized && (!snapshot.staged.isEmpty() || !snapshot.modified.isEmpty()
                || !snapshot.added.isEmpty() || !snapshot.removed.isEmpty()
                || !snapshot.untracked.isEmpty() || !snapshot.conflicting.isEmpty())) {
            throw new IllegalStateException("The local Git worktree is not clean. Review or commit existing changes in GitHub Manager before Directly Git.");
        }
        if (snapshot.initialized) {
            File gitDir = new File(current.projectRoot, ".git");
            if (gitDir.isDirectory()) {
                try (Git git = Git.open(current.projectRoot)) {
                    String origin = git.getRepository().getConfig().getString("remote", "origin", "url");
                    if (origin != null && !origin.trim().isEmpty()) {
                        throw new IllegalStateException("An origin remote already exists. Directly Git will not replace it.");
                    }
                }
            }
        }
        ArrayList<String> files = new ArrayList<>();
        int excluded = collectManifest(current.projectRoot, current.projectRoot, files, new long[]{0L});
        if (!"EMPTY".equals(readmeMode)) {
            if (new File(current.projectRoot, "README.md").exists() || new File(current.projectRoot, "readme.md").exists()) {
                throw new IllegalStateException("A README already exists in this project. Directly Git will not overwrite it.");
            }
            files.add("README.md");
        }
        int imageIndex = 0;
        ArrayList<String> imageExtensions = new ArrayList<>();
        for (Uri uri : images) {
            imageIndex++;
            String ext = validateImageUri(uri);
            imageExtensions.add(ext);
            String path = String.format(Locale.ROOT, "directly-git-assets/image-%02d.%s", imageIndex, ext);
            if (new File(current.projectRoot, path).exists()) throw new IllegalStateException("Image destination already exists: " + path);
            files.add(path);
        }
        Collections.sort(files);
        if (files.isEmpty()) throw new IllegalStateException("No publishable project files were found.");
        if (files.size() > 5000) throw new IllegalStateException("The project contains too many files for a safe guided publish (limit 5,000).");
        long userId = account.optLong("id", 0L);
        String authorName = account.optString("name", "").trim();
        if (authorName.isEmpty()) authorName = owner;
        String authorEmail = account.optString("email", "").trim();
        if (authorEmail.isEmpty() && userId > 0) authorEmail = userId + "+" + owner + "@users.noreply.github.com";
        if (authorEmail.isEmpty()) throw new IllegalStateException("GitHub did not provide an author identity. Add a verified email to your GitHub profile and retry.");
        return new Plan(current, name, safeDescription, readmeMode, readmeContent == null ? "" : readmeContent,
                images, imageExtensions, files, excluded, owner, authorName, authorEmail, snapshot.initialized, snapshot.headCommit);
    }

    public Result publish(Plan plan, AtomicBoolean cancelled, ProgressListener progress) throws Exception {
        if (plan == null) throw new IllegalArgumentException("A reviewed publishing plan is required.");
        boolean remoteCreated = false;
        String repositoryUrl = "https://github.com/" + plan.owner + "/" + plan.repositoryName;
        ArrayList<File> generated = new ArrayList<>();
        long startedAt = System.currentTimeMillis();
        try {
            phase(progress, "Revalidating project and GitHub credentials");
            checkCancelled(cancelled);
            GitProjectBindingStore.Binding current = bindings.getBoundProject();
            if (current == null || !plan.binding.scId.equals(current.scId)
                    || !plan.binding.projectRoot.getCanonicalPath().equals(current.projectRoot.getCanonicalPath())) {
                throw new IllegalStateException("The selected project changed after review.");
            }
            String token = auth.getUsableAccessToken(clientId);
            JSONObject account = api.getAuthenticatedAccount();
            if (!plan.owner.equals(account.optString("login", ""))) throw new IllegalStateException("The authenticated GitHub account changed after review.");
            ensureRepositoryDoesNotExist(plan.owner, plan.repositoryName);
            checkCancelled(cancelled);

            phase(progress, "Preparing README and selected images");
            if (!"EMPTY".equals(plan.readmeMode)) {
                File readme = new File(current.projectRoot, "README.md");
                if (readme.exists()) throw new IllegalStateException("README.md appeared after review; refusing to overwrite it.");
                String content = buildReadme(plan);
                writeNewFile(readme, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                generated.add(readme);
            }
            File assets = new File(current.projectRoot, "directly-git-assets");
            if (!plan.images.isEmpty() && !assets.exists() && !assets.mkdirs()) throw new IOException("Could not create the image directory.");
            for (int i = 0; i < plan.images.size(); i++) {
                checkCancelled(cancelled);
                Uri uri = plan.images.get(i);
                String ext = validateImageUri(uri);
                File target = new File(assets, String.format(Locale.ROOT, "image-%02d.%s", i + 1, ext));
                if (target.exists()) throw new IllegalStateException("Image destination appeared after review.");
                copyBounded(uri, target, 8L * 1024L * 1024L);
                generated.add(target);
            }

            phase(progress, "Building and validating the reviewed file manifest");
            ArrayList<String> manifest = new ArrayList<>();
            collectManifest(current.projectRoot, current.projectRoot, manifest, new long[]{0L});
            Collections.sort(manifest);
            if (!manifest.containsAll(plan.files)) throw new IllegalStateException("The project file set changed after review. Review the project again.");
            for (String path : manifest) scanFile(current.projectRoot, path);
            checkCancelled(cancelled);

            phase(progress, "Preparing the local commit");
            LocalGitBackend.Snapshot before = backend.inspect(current.projectRoot);
            if (!before.initialized) backend.initialize(current.projectRoot, true);
            LocalGitBackend.Snapshot snapshot = backend.inspect(current.projectRoot);
            if (!snapshot.staged.isEmpty() || !snapshot.modified.isEmpty() || !snapshot.added.isEmpty()
                    || !snapshot.removed.isEmpty() || !snapshot.conflicting.isEmpty()) {
                throw new IllegalStateException("The local worktree changed during publishing. No remote repository was created.");
            }
            ArrayList<String> changed = new ArrayList<>(snapshot.untracked);
            if (!changed.isEmpty()) {
                LocalGitBackend.CommitOutcome outcome = backend.commitSelected(current.projectRoot, changed,
                        "Publish " + plan.repositoryName + " from Cooper", plan.authorName, plan.authorEmail, true);
                LocalGitBackend.Snapshot verified = backend.inspect(current.projectRoot);
                if (!outcome.commitId.equals(verified.headCommit)) throw new IllegalStateException("Local commit read-back verification failed.");
            }
            LocalGitBackend.Snapshot local = backend.inspect(current.projectRoot);
            if (local.headCommit == null || local.headCommit.trim().isEmpty()) throw new IllegalStateException("No local commit exists to publish.");
            String expectedSha = local.headCommit;

            phase(progress, "Creating a new GitHub repository");
            checkCancelled(cancelled);
            JSONObject remote;
            try {
                remote = api.createRepository(plan.repositoryName, plan.description, true, true);
            } catch (Exception createError) {
                // A timeout is ambiguous. Look up the repository before any retry; never retry creation blindly.
                try {
                    JSONObject lookup = api.getRepository(plan.owner, plan.repositoryName);
                    boolean createdRecently = lookup.optString("created_at", "").compareTo(
                            new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(new java.util.Date(startedAt))) >= 0;
                    if (!createdRecently || !plan.owner.equals(lookup.optJSONObject("owner") == null ? "" : lookup.optJSONObject("owner").optString("login", ""))
                            || !plan.description.equals(lookup.optString("description", ""))) {
                        throw new PublishException("A repository with this name exists, but Cooper could not prove it was created by this session. No push was attempted.", false, "", createError);
                    }
                    remote = lookup;
                } catch (GitHubApiClient.ApiException lookupError) {
                    throw createError;
                }
            }
            String returnedOwner = remote.optJSONObject("owner") == null ? "" : remote.optJSONObject("owner").optString("login", "");
            if (!plan.owner.equals(returnedOwner) || !plan.repositoryName.equals(remote.optString("name", ""))) {
                throw new IllegalStateException("GitHub returned a repository identity different from the reviewed target.");
            }
            remoteCreated = true;
            repositoryUrl = remote.optString("html_url", repositoryUrl);
            if (!repositoryUrl.startsWith("https://github.com/" + plan.owner + "/" + plan.repositoryName)) {
                throw new IllegalStateException("GitHub returned an untrusted repository URL.");
            }
            if (cancelled != null && cancelled.get()) throw new PublishException("Publishing was stopped after the remote repository was created. The repository was retained; no push was attempted.", true, repositoryUrl, null);

            phase(progress, "Configuring the new remote");
            backend.configureRemote(current.projectRoot, "https://github.com/" + plan.owner + "/" + plan.repositoryName + ".git", true);
            phase(progress, "Pushing the reviewed branch and commit");
            String pushedSha = backend.push(current.projectRoot, token, true);
            if (!expectedSha.equals(pushedSha)) throw new IllegalStateException("The push result did not match the reviewed local commit.");

            phase(progress, "Verifying the remote branch and commit");
            JSONObject branch = api.getBranch(plan.owner, plan.repositoryName, local.currentBranch);
            JSONObject commit = branch.optJSONObject("commit");
            String remoteSha = commit == null ? "" : commit.optString("sha", "");
            if (!expectedSha.equals(remoteSha)) throw new IllegalStateException("GitHub's remote branch SHA does not match the local commit. Publication remains unverified.");

            phase(progress, "Saving the verified project association");
            if (!published.edit().putString("project_" + plan.binding.scId, plan.owner + "/" + plan.repositoryName)
                    .putString("commit_" + plan.binding.scId, expectedSha).commit()) {
                throw new IllegalStateException("Remote commit was verified, but Cooper could not persist the publication record.");
            }
            bindings.linkRemote(plan.owner, plan.repositoryName);
            return new Result(repositoryUrl, expectedSha, local.currentBranch);
        } catch (Exception e) {
            if (!remoteCreated) {
                for (File file : generated) {
                    try { if (file.isFile()) file.delete(); } catch (Exception ignored) { }
                }
            }
            if (e instanceof PublishException) throw e;
            throw new PublishException(e.getMessage() == null ? "Directly Git publishing failed; verify local and remote state before retrying." : e.getMessage(),
                    remoteCreated, repositoryUrl, e);
        }
    }

    private void ensureRepositoryDoesNotExist(String owner, String name) throws Exception {
        try {
            api.getRepository(owner, name);
            throw new IllegalStateException("That GitHub repository already exists or is accessible. Directly Git never overwrites existing repositories.");
        } catch (GitHubApiClient.ApiException e) {
            if (!"NOT_FOUND".equals(e.safeCode)) throw e;
        }
    }

    private int collectManifest(File root, File directory, List<String> files, long[] totalBytes) throws Exception {
        File[] children = directory.listFiles();
        if (children == null) throw new IOException("Cooper could not read the selected project directory.");
        java.util.Arrays.sort(children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        int excluded = 0;
        for (File child : children) {
            String path = relative(root, child);
            if (GitSafetyGuard.isExcludedByDefault(path)) { excluded++; continue; }
            File canonical = child.getCanonicalFile();
            String rootPath = root.getCanonicalPath();
            if (!canonical.getPath().startsWith(rootPath + File.separator)) throw new IOException("A project path resolves outside the selected project.");
            if (child.isDirectory()) {
                excluded += collectManifest(root, child, files, totalBytes);
            } else if (child.isFile()) {
                if (GitSafetyGuard.isSensitivePath(path)) throw new IllegalStateException("Sensitive file blocked from publication: " + path);
                if (child.length() > 10L * 1024L * 1024L) throw new IllegalStateException("File exceeds the 10 MB guided-publish limit: " + path);
                totalBytes[0] += child.length();
                if (totalBytes[0] > 50L * 1024L * 1024L) throw new IllegalStateException("The project exceeds the 50 MB guided-publish limit.");
                scanFile(root, path);
                files.add(path);
            }
            if (files.size() > 5000) throw new IllegalStateException("The project exceeds the 5,000-file guided-publish limit.");
        }
        return excluded;
    }

    private void scanFile(File root, String path) throws Exception {
        if (GitSafetyGuard.isSensitivePath(path)) throw new IllegalStateException("Sensitive file blocked from publication: " + path);
        File file = new File(root, path);
        if (!file.isFile() || !isTextPath(path)) return;
        if (file.length() > 2L * 1024L * 1024L) throw new IllegalStateException("Text file is too large to scan safely: " + path);
        byte[] bytes;
        try (InputStream input = new FileInputStream(file)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > 2 * 1024 * 1024) throw new IOException("Text file exceeds scanner limit.");
            }
            bytes = out.toByteArray();
        }
        String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        List<GitSecretScanner.Finding> findings = GitSecretScanner.scan(path, text);
        if (!findings.isEmpty()) {
            StringBuilder rules = new StringBuilder();
            for (GitSecretScanner.Finding finding : findings) {
                if (rules.length() > 0) rules.append(", ");
                rules.append(finding.rule);
            }
            throw new IllegalStateException("Secret/sensitive-content review required for " + path + " (" + rules + "). Remove or review this file in GitHub Manager before publishing.");
        }
    }

    private static boolean isTextPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".java") || lower.endsWith(".kt") || lower.endsWith(".xml")
                || lower.endsWith(".json") || lower.endsWith(".gradle") || lower.endsWith(".properties")
                || lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".yml")
                || lower.endsWith(".yaml") || lower.endsWith(".html") || lower.endsWith(".css")
                || lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".sh")
                || lower.endsWith(".pro") || lower.endsWith(".cfg") || lower.endsWith(".toml")
                || lower.endsWith(".ini") || lower.endsWith(".sql") || lower.endsWith(".ktx");
    }

    private String validateImageUri(Uri uri) throws Exception {
        if (uri == null) throw new IllegalArgumentException("A selected image is unavailable.");
        String type = context.getContentResolver().getType(uri);
        if (type == null) throw new IllegalArgumentException("Cooper could not determine a selected image type.");
        String lower = type.toLowerCase(Locale.ROOT);
        String ext;
        if ("image/jpeg".equals(lower)) ext = "jpg";
        else if ("image/png".equals(lower)) ext = "png";
        else if ("image/webp".equals(lower)) ext = "webp";
        else if ("image/gif".equals(lower)) ext = "gif";
        else throw new IllegalArgumentException("Use JPEG, PNG, WebP, or GIF images.");
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null || input.read() < 0) throw new IOException("Selected image is unreadable.");
        }
        return ext;
    }

    private void copyBounded(Uri uri, File target, long maxBytes) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(target, false)) {
            if (input == null) throw new IOException("Selected image could not be opened.");
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) throw new IOException("Each selected image must be 8 MB or smaller.");
                output.write(buffer, 0, read);
            }
            if (total == 0) throw new IOException("Selected image is empty.");
        } catch (Exception e) {
            if (target.exists()) target.delete();
            throw e;
        }
    }

    private static String buildReadme(Plan plan) {
        String content = plan.readmeContent == null ? "" : plan.readmeContent.trim();
        if (content.isEmpty()) content = plan.binding.workspaceName;
        StringBuilder result = new StringBuilder(content).append("\n");
        if (!plan.images.isEmpty()) {
            result.append("\n").append("## Project images").append("\n\n");
            for (int i = 0; i < plan.images.size(); i++) {
                String ext = plan.imageExtensions.get(i);
                result.append("![Project image ").append(i + 1).append("](directly-git-assets/image-")
                        .append(String.format(Locale.ROOT, "%02d", i + 1)).append(".").append(ext).append(")\n");
            }
        }
        return result.toString();
    }

    private static void writeNewFile(File file, byte[] bytes) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Could not create README parent directory.");
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            output.write(bytes);
            output.flush();
        }
    }

    private static String relative(File root, File file) throws IOException {
        String rootPath = root.getCanonicalPath();
        String path = file.getCanonicalPath();
        if (!path.startsWith(rootPath + File.separator)) throw new IOException("Path is outside the project root.");
        return path.substring(rootPath.length() + 1).replace(File.separatorChar, '/');
    }

    private static void checkCancelled(AtomicBoolean cancelled) throws PublishException {
        if (cancelled != null && cancelled.get()) throw new PublishException("Publishing was stopped before the next operation. Any already-created remote repository was retained.", false, "", null);
    }

    private static void phase(ProgressListener listener, String value) {
        if (listener != null) listener.onPhase(value);
    }
}
