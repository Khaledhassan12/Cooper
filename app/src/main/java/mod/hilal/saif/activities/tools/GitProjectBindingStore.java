package mod.hilal.saif.activities.tools;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import a.a.a.lC;
import a.a.a.wq;

/**
 * Cooper Sketchware project binding. Project IDs are resolved through Cooper's own project
 * metadata and path utility instead of trusting a display name or an arbitrary filesystem path.
 */
public final class GitProjectBindingStore {
    private static final String PREFS = "cooper_github_project_binding";
    private static final String KEY_SC_ID = "sc_id";
    private static final String KEY_WORKSPACE_NAME = "workspace_name";

    public static final class Binding {
        public final String scId;
        public final String workspaceName;
        public final String appName;
        public final File projectRoot;

        private Binding(String scId, String workspaceName, String appName, File projectRoot) {
            this.scId = scId;
            this.workspaceName = workspaceName;
            this.appName = appName;
            this.projectRoot = projectRoot;
        }
    }

    private final SharedPreferences preferences;

    public GitProjectBindingStore(Context context) {
        if (context == null) throw new IllegalArgumentException("Context is required.");
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public Binding bind(String scId) throws Exception {
        if (blank(scId) || !scId.matches("[0-9]{1,12}")) {
            throw new IllegalArgumentException("A valid Cooper project ID is required.");
        }
        HashMap<String, Object> metadata = lC.b(scId);
        if (metadata == null || !scId.equals(String.valueOf(metadata.get("sc_id")))) {
            throw new IllegalStateException("Cooper could not verify the selected project metadata.");
        }
        File root = new File(wq.b(scId)).getCanonicalFile();
        File metadataFile = new File(wq.c(scId), "project").getCanonicalFile();
        if (!root.isDirectory() || !metadataFile.isFile()) {
            throw new IllegalStateException("The selected Cooper project directory does not match its project metadata.");
        }
        String workspace = safeValue(metadata.get("my_ws_name"), "Cooper project");
        boolean saved = preferences.edit().putString(KEY_SC_ID, scId)
                .putString(KEY_WORKSPACE_NAME, workspace).commit();
        if (!saved) throw new IllegalStateException("Cooper could not save the project binding.");
        return new Binding(scId, workspace, safeValue(metadata.get("my_app_name"), ""), root);
    }

    public Binding getBoundProject() throws Exception {
        String scId = preferences.getString(KEY_SC_ID, null);
        if (blank(scId)) return null;
        HashMap<String, Object> metadata = lC.b(scId);
        if (metadata == null || !scId.equals(String.valueOf(metadata.get("sc_id")))) {
            throw new IllegalStateException("The bound Cooper project is no longer available or its identity changed.");
        }
        File root = new File(wq.b(scId)).getCanonicalFile();
        File metadataFile = new File(wq.c(scId), "project").getCanonicalFile();
        if (!root.isDirectory() || !metadataFile.isFile()) {
            throw new IllegalStateException("The bound Cooper project directory could not be verified.");
        }
        return new Binding(scId,
                safeValue(metadata.get("my_ws_name"), preferences.getString(KEY_WORKSPACE_NAME, "Cooper project")),
                safeValue(metadata.get("my_app_name"), ""), root);
    }

    public void clearBinding() {
        preferences.edit().remove(KEY_SC_ID).remove(KEY_WORKSPACE_NAME).commit();
    }

    private static String safeValue(Object value, String fallback) {
        return value == null || String.valueOf(value).trim().isEmpty() ? fallback : String.valueOf(value);
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
