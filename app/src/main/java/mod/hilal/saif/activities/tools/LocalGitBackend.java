package mod.hilal.saif.activities.tools;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Cooper local Git backend using JGit; it never invokes a shell or system Git executable.
 * All operations are scoped to an explicitly supplied project root.
 *
 * Network operations are deliberately not implemented here; remote GitHub API/auth must
 * remain in a separate layer. Callers must invoke this backend off the Android main thread.
 */
public final class LocalGitBackend {
    public static final class Snapshot {
        public final boolean initialized;
        public final String currentBranch;
        public final String headCommit;
        public final List<String> staged;
        public final List<String> modified;
        public final List<String> added;
        public final List<String> removed;
        public final List<String> untracked;
        public final List<String> conflicting;

        private Snapshot(boolean initialized, String currentBranch, String headCommit,
                         Set<String> staged, Set<String> modified, Set<String> added,
                         Set<String> removed, Set<String> untracked, Set<String> conflicting) {
            this.initialized = initialized;
            this.currentBranch = currentBranch;
            this.headCommit = headCommit;
            this.staged = immutableSorted(staged);
            this.modified = immutableSorted(modified);
            this.added = immutableSorted(added);
            this.removed = immutableSorted(removed);
            this.untracked = immutableSorted(untracked);
            this.conflicting = immutableSorted(conflicting);
        }
    }

    public static final class CommitOutcome {
        public final String commitId;
        public final String message;
        public final List<String> committedPaths;

        private CommitOutcome(String commitId, String message, List<String> paths) {
            this.commitId = commitId;
            this.message = message;
            this.committedPaths = Collections.unmodifiableList(new ArrayList<>(paths));
        }
    }

    public static final class GitFailure extends Exception {
        public final String safeCode;
        GitFailure(String safeCode, String message, Throwable cause) {
            super(message, cause);
            this.safeCode = safeCode;
        }
        GitFailure(String safeCode, String message) { this(safeCode, message, null); }
    }

    public void initialize(File projectRoot, boolean explicitlyApproved) throws GitFailure {
        requireApproval(explicitlyApproved, "Initializing a local repository requires confirmation.");
        File root = canonicalRoot(projectRoot);
        if (new File(root, ".git").exists()) {
            throw new GitFailure("ALREADY_INITIALIZED", "A Git metadata directory already exists in this project.");
        }
        try (Git git = Git.init().setDirectory(root).call()) {
            if (git.getRepository().getDirectory() == null || !git.getRepository().getDirectory().isDirectory()) {
                throw new GitFailure("VERIFY_FAILED", "Git initialization could not be verified.");
            }
        } catch (GitAPIException e) {
            throw new GitFailure("GIT_INIT_FAILED", "Cooper could not initialize the local repository.", e);
        }
    }

    public Snapshot inspect(File projectRoot) throws GitFailure {
        File root = canonicalRoot(projectRoot);
        if (!new File(root, ".git").exists()) {
            return new Snapshot(false, "", "", Collections.emptySet(), Collections.emptySet(),
                    Collections.emptySet(), Collections.emptySet(), Collections.emptySet(), Collections.emptySet());
        }
        try (Git git = openRoot(root)) {
            Repository repository = git.getRepository();
            Status status = git.status().call();
            ObjectId head = repository.resolve("HEAD");
            return new Snapshot(true, repository.getBranch(), head == null ? "" : head.name(),
                    status.getChanged(), status.getModified(), status.getAdded(), status.getRemoved(),
                    status.getUntracked(), status.getConflicting());
        } catch (GitAPIException | IOException e) {
            throw new GitFailure("STATUS_FAILED", "Cooper could not inspect the local Git repository.", e);
        }
    }

    public void stage(File projectRoot, List<String> relativePaths, boolean explicitlyApproved) throws GitFailure {
        requireApproval(explicitlyApproved, "Staging files requires explicit user confirmation.");
        File root = canonicalRoot(projectRoot);
        validatePaths(root, relativePaths);
        if (relativePaths.isEmpty()) throw new GitFailure("EMPTY_PLAN", "Select at least one file to stage.");
        try (Git git = openRoot(root)) {
            for (String path : relativePaths) {
                File file = new File(root, path);
                if (file.exists() && file.isFile()) {
                    String source = readTextBounded(file, 2 * 1024 * 1024);
                    List<GitSecretScanner.Finding> findings = GitSecretScanner.scan(path, source);
                    if (GitSecretScanner.blocksUpload(findings)) {
                        throw new GitFailure("SENSITIVE_CONTENT", "A selected file was blocked by Cooper's secret preflight: " + path);
                    }
                }
                git.add().addFilepattern(path).call();
            }
        } catch (GitFailure e) {
            throw e;
        } catch (GitAPIException | IOException e) {
            throw new GitFailure("STAGE_FAILED", "Cooper could not stage the selected project files.", e);
        }
    }

    public void unstage(File projectRoot, List<String> relativePaths, boolean explicitlyApproved) throws GitFailure {
        requireApproval(explicitlyApproved, "Unstaging files requires explicit user confirmation.");
        File root = canonicalRoot(projectRoot);
        validatePaths(root, relativePaths);
        if (relativePaths.isEmpty()) throw new GitFailure("EMPTY_PLAN", "Select at least one file to unstage.");
        try (Git git = openRoot(root)) {
            for (String path : relativePaths) git.reset().addPath(path).call();
        } catch (GitAPIException e) {
            throw new GitFailure("UNSTAGE_FAILED", "Cooper could not unstage the selected files.", e);
        }
    }

    public CommitOutcome commit(File projectRoot, String message, String authorName, String authorEmail,
                                boolean explicitlyApproved) throws GitFailure {
        requireApproval(explicitlyApproved, "Creating a local commit requires explicit user confirmation.");
        if (message == null || message.trim().isEmpty()) throw new GitFailure("EMPTY_MESSAGE", "Enter a commit message.");
        if (blank(authorName) || blank(authorEmail) || authorEmail.indexOf('\n') >= 0) {
            throw new GitFailure("INVALID_AUTHOR", "A valid Git author name and email are required.");
        }
        File root = canonicalRoot(projectRoot);
        try (Git git = openRoot(root)) {
            Status before = git.status().call();
            Set<String> staged = new TreeSet<>();
            staged.addAll(before.getAdded());
            staged.addAll(before.getChanged());
            staged.addAll(before.getRemoved());
            if (staged.isEmpty()) throw new GitFailure("NOTHING_STAGED", "No staged changes are available to commit.");
            RevCommit commit = git.commit().setMessage(message.trim())
                    .setAuthor(authorName.trim(), authorEmail.trim()).call();
            if (commit == null || commit.getId() == null) {
                throw new GitFailure("VERIFY_FAILED", "The commit result did not contain a valid commit identifier.");
            }
            RevCommit readBack = git.getRepository().parseCommit(commit.getId());
            if (!readBack.getFullMessage().equals(message.trim())) {
                throw new GitFailure("VERIFY_FAILED", "The saved commit message did not match the requested message.");
            }
            List<String> committed = new ArrayList<>(staged);
            Collections.sort(committed);
            return new CommitOutcome(commit.getId().name(), readBack.getFullMessage(), committed);
        } catch (GitFailure e) {
            throw e;
        } catch (GitAPIException | IOException e) {
            throw new GitFailure("COMMIT_FAILED", "Cooper could not create or verify the local commit.", e);
        }
    }

    public List<String> listBranches(File projectRoot) throws GitFailure {
        File root = canonicalRoot(projectRoot);
        try (Git git = openRoot(root)) {
            List<String> names = new ArrayList<>();
            git.branchList().call().forEach(ref -> names.add(Repository.shortenRefName(ref.getName())));
            Collections.sort(names);
            return Collections.unmodifiableList(names);
        } catch (GitAPIException e) {
            throw new GitFailure("BRANCH_LIST_FAILED", "Cooper could not list local branches.", e);
        }
    }

    public void createBranch(File projectRoot, String branchName, boolean explicitlyApproved) throws GitFailure {
        requireApproval(explicitlyApproved, "Creating a local branch requires explicit user confirmation.");
        if (blank(branchName) || branchName.startsWith("-") || branchName.contains("..")
                || branchName.contains(" ") || branchName.contains("~") || branchName.contains("^")
                || branchName.contains(":") || branchName.contains("\\") || branchName.contains("@{")) {
            throw new GitFailure("INVALID_BRANCH", "The requested branch name is invalid.");
        }
        File root = canonicalRoot(projectRoot);
        try (Git git = openRoot(root)) {
            git.branchCreate().setName(branchName).call();
        } catch (GitAPIException e) {
            throw new GitFailure("BRANCH_CREATE_FAILED", "Cooper could not create the local branch.", e);
        }
    }

    public void checkout(File projectRoot, String branchName, boolean explicitlyApproved) throws GitFailure {
        requireApproval(explicitlyApproved, "Switching branches requires explicit user confirmation.");
        if (blank(branchName)) throw new GitFailure("INVALID_BRANCH", "A target branch is required.");
        File root = canonicalRoot(projectRoot);
        try (Git git = openRoot(root)) {
            Status status = git.status().call();
            if (!status.isClean()) {
                throw new GitFailure("WORKTREE_NOT_CLEAN", "Branch switching is blocked because local changes need to be reviewed first.");
            }
            git.checkout().setName(branchName).call();
            if (!branchName.equals(git.getRepository().getBranch())) {
                throw new GitFailure("VERIFY_FAILED", "The active branch did not match the requested branch after checkout.");
            }
        } catch (GitFailure e) {
            throw e;
        } catch (GitAPIException | IOException e) {
            throw new GitFailure("CHECKOUT_FAILED", "Cooper could not switch to the requested branch.", e);
        }
    }

    private static Git openRoot(File root) throws GitFailure {
        try {
            return Git.open(root);
        } catch (IOException e) {
            throw new GitFailure("NOT_A_REPOSITORY", "The selected Cooper project is not a valid Git repository.", e);
        }
    }

    private static File canonicalRoot(File root) throws GitFailure {
        if (root == null) throw new GitFailure("INVALID_ROOT", "A Cooper project root is required.");
        try {
            File canonical = root.getCanonicalFile();
            if (!canonical.exists() || !canonical.isDirectory()) {
                throw new GitFailure("INVALID_ROOT", "The selected Cooper project directory is unavailable.");
            }
            return canonical;
        } catch (IOException | SecurityException e) {
            throw new GitFailure("INVALID_ROOT", "The selected Cooper project path could not be verified.", e);
        }
    }

    private static void validatePaths(File root, List<String> paths) throws GitFailure {
        GitSafetyGuard.Validation validation = GitSafetyGuard.validatePaths(root, paths);
        if (!validation.isAllowed()) throw new GitFailure("PATH_REJECTED", validation.safeMessage);
        for (String path : validation.normalizedPaths) {
            if (GitSafetyGuard.isSensitivePath(path)) {
                throw new GitFailure("SENSITIVE_PATH", "Cooper blocks staging sensitive file paths by default: " + path);
            }
        }
    }

    private static String readTextBounded(File file, int maxBytes) throws IOException {
        if (file.length() > maxBytes) return "";
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                if (output.size() > maxBytes) return "";
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void requireApproval(boolean approved, String message) throws GitFailure {
        if (!approved) throw new GitFailure("APPROVAL_REQUIRED", message);
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    private static List<String> immutableSorted(Set<String> values) {
        List<String> result = new ArrayList<>(values);
        Collections.sort(result);
        return Collections.unmodifiableList(result);
    }
}
