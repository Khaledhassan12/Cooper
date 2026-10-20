package pro.sketchware.utility;

import static pro.sketchware.utility.ThemeUtils.isDarkThemeEnabled;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Arrays;
import pro.sketchware.utility.FileUtil;

import extensions.fora2323.daygreen.keyword.IDCKeyword;
import extensions.fora2323.daygreen.keyword.IDCppKeyword;
import extensions.fora2323.daygreen.keyword.IDCssKeyword;
import extensions.fora2323.daygreen.keyword.IDHtmlKeyword;
import extensions.fora2323.daygreen.keyword.IDJsKeyword;
import extensions.fora2323.daygreen.keyword.IDXmlKeyword;

import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula;
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub;

import mod.jbk.code.CodeEditorColorSchemes;
import mod.jbk.code.CodeEditorLanguages;
import pro.sketchware.R;
import pro.sketchware.editor.SketchwareCodeEditor;

public class EditorUtils {
    EditorUtils() {
    }

    @NonNull
    public static EditorColorScheme getMaterialStyledScheme(CodeEditor editor) {
        return getMaterialStyledScheme(editor, true);
    }

    @NonNull
    public static EditorColorScheme getMaterialStyledScheme(CodeEditor editor, boolean fullOverride) {
        var scheme = editor.getColorScheme();
        var primary = MaterialColors.getColor(editor, R.attr.colorPrimary);
        var surface = MaterialColors.getColor(editor, R.attr.colorSurface);
        var surfaceContainer = MaterialColors.getColor(editor, R.attr.colorSurfaceContainer);
        var surfaceContainerLow = MaterialColors.getColor(editor, R.attr.colorSurfaceContainerLow);
        var surfaceContainerHighest = MaterialColors.getColor(editor, R.attr.colorSurfaceContainerHighest);
        var onSurface = MaterialColors.getColor(editor, R.attr.colorOnSurface);
        var onSurfaceVariant = MaterialColors.getColor(editor, R.attr.colorOnSurfaceVariant);

        if (fullOverride) {
            scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, surface);
            // Only override normal text color for non-TextMate schemes
            // to avoid breaking syntax highlighting colors
            if (!(scheme instanceof TextMateColorScheme)) {
                scheme.setColor(EditorColorScheme.TEXT_NORMAL, onSurface);
            }
            scheme.setColor(EditorColorScheme.CURRENT_LINE, surfaceContainerLow);
            scheme.setColor(EditorColorScheme.LINE_NUMBER_PANEL, surfaceContainer);
            scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, surfaceContainer);
        }

        // Always style these to maintain IDE feel
        scheme.setColor(EditorColorScheme.LINE_DIVIDER, surfaceContainerHighest);
        scheme.setColor(EditorColorScheme.SELECTION_INSERT, onSurfaceVariant);
        scheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND, onSurface);
        scheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BACKGROUND, surfaceContainerHighest);

        // Completion window styling - matches IDE panels
        scheme.setColor(EditorColorScheme.COMPLETION_WND_BACKGROUND, surfaceContainer);
        scheme.setColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY, onSurface);
        scheme.setColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY, onSurfaceVariant);
        scheme.setColor(EditorColorScheme.COMPLETION_WND_CORNER, primary);

        return scheme;
    }

    @NonNull
    public static Typeface getTypeface(Context context) {
        return Typeface.createFromAsset(context.getAssets(), "fonts/jetbrainsmono-regular.ttf");
    }

    public static void loadJavaConfig(CodeEditor editor) {
        Language language = CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_JAVA);
        if (language instanceof TextMateLanguage tm) {
            ArrayList<String> fullKeywords = new ArrayList<>();
            if (editor instanceof SketchwareCodeEditor) {
                String scId = ((SketchwareCodeEditor) editor).getScId();
                if (scId != null && !scId.isEmpty()) {
                    File genDir = new File(FileUtil.getExternalStorageDir() + "/.sketchware/mysc/" + scId + "/gen/");

                    List<File> rJavaFiles = new ArrayList<>();

                    File mainR = new File(genDir, "R.java");
                    if (mainR.exists()) rJavaFiles.add(mainR);

                    if (genDir.exists() && genDir.isDirectory()) {
                        File[] subDirs = genDir.listFiles(File::isDirectory);
                        if (subDirs != null) {
                            for (File subDir : subDirs) {
                                File libR = new File(subDir, "R.java");
                                if (libR.exists()) rJavaFiles.add(libR);
                            }
                        }
                    }

                    for (File rJavaFile : rJavaFiles) {
                        try {
                            String rContent = FileUtil.readFile(rJavaFile.getAbsolutePath());
                            // Ambil nama kelas internal (id, layout, string, color, dll)
                            Matcher classMatcher = Pattern.compile("public\\s+static\\s+final\\s+class\\s+([a-zA-Z0-9_]+)\\s*\\{").matcher(rContent);
                            ArrayList<String> subClasses = new ArrayList<>();
                            while (classMatcher.find()) {
                                subClasses.add(classMatcher.group(1));
                            }

                            for (String subClass : subClasses) {
                                // Cari semua field int di dalam class internal tersebut
                                Matcher fieldMatcher = Pattern.compile("public\\s+static\\s+class\\s+" + subClass + "\\s*\\{([^\\}]+)\\}").matcher(rContent);
                                if (fieldMatcher.find()) {
                                    String classContent = fieldMatcher.group(1);
                                    Matcher m = Pattern.compile("public\\s+static\\s+final\\s+int\\s+([a-zA-Z0-9_]+)\\s*=").matcher(classContent);
                                    while (m.find()) {
                                        String fieldName = m.group(1);
                                        // Suntikkan variasi autocomplete lengkap agar muncul saat diketik setelah titik
                                        String key1 = "R." + subClass + "." + fieldName;
                                        String key2 = subClass + "." + fieldName;
                                        if (!fullKeywords.contains(key1)) fullKeywords.add(key1);
                                        if (!fullKeywords.contains(key2)) fullKeywords.add(key2);
                                        if (!fullKeywords.contains(fieldName)) fullKeywords.add(fieldName);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e("EditorUtils", "Failed to parse R.java cache: " + rJavaFile.getPath(), e);
                        }
                    }
                }
            }
            tm.setCompleterKeywords(fullKeywords.toArray(new String[0]));
        }
        loadConfigByLanguage(editor, language, true);
    }

    public static void loadKotlinConfig(CodeEditor editor) {
        Language language = CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_KOTLIN);
        if (language instanceof TextMateLanguage tm) {
            ArrayList<String> fullKeywords = new ArrayList<>();
            if (editor instanceof SketchwareCodeEditor) {
                String scId = ((SketchwareCodeEditor) editor).getScId();
                if (scId != null && !scId.isEmpty()) {
                    File genDir = new File(FileUtil.getExternalStorageDir() + "/.sketchware/mysc/" + scId + "/gen/");

                    List<File> rJavaFiles = new ArrayList<>();

                    File mainR = new File(genDir, "R.java");
                    if (mainR.exists()) rJavaFiles.add(mainR);

                    if (genDir.exists() && genDir.isDirectory()) {
                        File[] subDirs = genDir.listFiles(File::isDirectory);
                        if (subDirs != null) {
                            for (File subDir : subDirs) {
                                File libR = new File(subDir, "R.java");
                                if (libR.exists()) rJavaFiles.add(libR);
                            }
                        }
                    }

                    for (File rJavaFile : rJavaFiles) {
                        try {
                            String rContent = FileUtil.readFile(rJavaFile.getAbsolutePath());
                            // Ambil nama kelas internal (id, layout, string, color, dll)
                            Matcher classMatcher = Pattern.compile("public\\s+static\\s+final\\s+class\\s+([a-zA-Z0-9_]+)\\s*\\{").matcher(rContent);
                            ArrayList<String> subClasses = new ArrayList<>();
                            while (classMatcher.find()) {
                                subClasses.add(classMatcher.group(1));
                            }

                            for (String subClass : subClasses) {
                                // Cari semua field int di dalam class internal tersebut
                                Matcher fieldMatcher = Pattern.compile("public\\s+static\\s+class\\s+" + subClass + "\\s*\\{([^\\}]+)\\}").matcher(rContent);
                                if (fieldMatcher.find()) {
                                    String classContent = fieldMatcher.group(1);
                                    Matcher m = Pattern.compile("public\\s+static\\s+final\\s+int\\s+([a-zA-Z0-9_]+)\\s*=").matcher(classContent);
                                    while (m.find()) {
                                        String fieldName = m.group(1);
                                        // Suntikkan variasi autocomplete lengkap agar muncul saat diketik setelah titik
                                        String key1 = "R." + subClass + "." + fieldName;
                                        String key2 = subClass + "." + fieldName;
                                        if (!fullKeywords.contains(key1)) fullKeywords.add(key1);
                                        if (!fullKeywords.contains(key2)) fullKeywords.add(key2);
                                        if (!fullKeywords.contains(fieldName)) fullKeywords.add(fieldName);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e("EditorUtils", "Failed to parse R.java cache: " + rJavaFile.getPath(), e);
                        }
                    }
                }
            }
            tm.setCompleterKeywords(fullKeywords.toArray(new String[0]));
        }
        loadConfigByLanguage(editor, language, true);
    }

    /** Kompatibilitas lama: pakai keyword gabungan (semua kategori). */
    public static void loadXmlConfig(CodeEditor editor) {
        loadXmlConfig(editor, null);
    }

    /**
     * @param pathOrFileName path lengkap atau nama file yang lagi dibuka,
     *                        dipakai buat milih keyword autocomplete yang sesuai
     *                        (layout / values-colors / values-strings / styles / attrs / manifest / drawable).
     *                        Boleh null kalau gak tahu jenis filenya (bakal pakai keyword gabungan).
     */
    public static void loadXmlConfig(CodeEditor editor, String pathOrFileName) {
        Language language = CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_XML);
        if (language instanceof TextMateLanguage tm) {
            ArrayList<String> keywords = new ArrayList<>(Arrays.asList(IDXmlKeyword.forFile(pathOrFileName)));
            
            if (editor instanceof SketchwareCodeEditor) {
                String scId = ((SketchwareCodeEditor) editor).getScId();
                if (scId != null && !scId.isEmpty()) {
                    String basePath = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + scId + "/files/resource";
                    
                    keywords.addAll(extractXmlResourceNames(basePath + "/values/colors.xml", "color", "@color/"));
                    keywords.addAll(extractXmlResourceNames(basePath + "/values/strings.xml", "string", "@string/"));
                    keywords.addAll(extractXmlResourceNames(basePath + "/values/dimens.xml", "dimen", "@dimen/"));
                    keywords.addAll(extractXmlResourceNames(basePath + "/values/styles.xml", "style", "@style/"));
                    
                    keywords.addAll(extractDrawableNames(basePath + "/drawable/"));
                    
                    String layoutDir = basePath + "/layout/";
                    ArrayList<String> layoutFiles = new ArrayList<>();
                    FileUtil.listDir(layoutDir, layoutFiles);
                    for (String lf : layoutFiles) {
                        if (lf.endsWith(".xml")) {
                            try {
                                String content = FileUtil.readFile(lf);
                                Matcher m = Pattern.compile("android:id\\s*=\\s*\"@\\+id/([^\"]+)\"").matcher(content);
                                while (m.find()) {
                                    String id = m.group(1);
                                    String format1 = "@id/" + id;
                                    String format2 = "@+id/" + id;
                                    if (!keywords.contains(format1)) keywords.add(format1);
                                    if (!keywords.contains(format2)) keywords.add(format2);
                                }
                            } catch (Exception e) {
                                Log.e("EditorUtils", "Failed to parse layout ids from " + lf, e);
                            }
                        }
                    }
                }
            }
            tm.setCompleterKeywords(keywords.toArray(new String[0]));
        }
        loadConfigByLanguage(editor, language, true);
    }

    private static ArrayList<String> extractXmlResourceNames(String filePath, String tagName, String prefix) {
        ArrayList<String> result = new ArrayList<>();
        if (!FileUtil.isExistFile(filePath)) return result;
        try {
            String content = FileUtil.readFile(filePath);
            Matcher m = Pattern.compile("<" + tagName + "[^>]+name\\s*=\\s*\"([^\"]+)\"").matcher(content);
            while (m.find()) {
                result.add(prefix + m.group(1));
            }
        } catch (Exception e) {
            Log.e("EditorUtils", "Failed to parse " + filePath, e);
        }
        return result;
    }

    private static ArrayList<String> extractDrawableNames(String drawableDir) {
        ArrayList<String> result = new ArrayList<>();
        ArrayList<String> files = new ArrayList<>();
        FileUtil.listDir(drawableDir, files);
        for (String f : files) {
            String name = new File(f).getName();
            int dot = name.lastIndexOf('.');
            if (dot > 0) name = name.substring(0, dot);
            result.add("@drawable/" + name);
        }
        return result;
    }

    public static void loadHtmlConfig(CodeEditor editor) {
        Language language = CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_HTML);
        if (language instanceof TextMateLanguage tm) {
            tm.setCompleterKeywords(IDHtmlKeyword.KEYWORDS);
        }
        loadConfigByLanguage(editor, language, true);
    }

    public static void loadCssConfig(CodeEditor editor) {
        Language language = CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_CSS);
        if (language instanceof TextMateLanguage tm) {
            tm.setCompleterKeywords(IDCssKeyword.KEYWORDS);
        }
        loadConfigByLanguage(editor, language, true);
    }

    public static void loadJsConfig(CodeEditor editor) {
        Language language = CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_JS);
        if (language instanceof TextMateLanguage tm) {
            tm.setCompleterKeywords(IDJsKeyword.KEYWORDS);
        }
        loadConfigByLanguage(editor, language, true);
    }

    public static void loadCppConfig(CodeEditor editor) {
        Language language = CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_CPP);
        if (language instanceof TextMateLanguage tm) {
            tm.setCompleterKeywords(IDCppKeyword.KEYWORDS);
        }
        loadConfigByLanguage(editor, language, true);
    }

    public static void loadCConfig(CodeEditor editor) {
        Language language = CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_C);
        if (language instanceof TextMateLanguage tm) {
            tm.setCompleterKeywords(IDCKeyword.KEYWORDS);
        }
        loadConfigByLanguage(editor, language, true);
    }

    private static void loadConfigByLanguage(CodeEditor editor, Language language, boolean isTextMate) {
        editor.setEditorLanguage(language);
        boolean isDark = isDarkThemeEnabled(editor.getContext());

        if (isTextMate) {
            String theme = isDark ? CodeEditorColorSchemes.THEME_GITHUB_DARK : CodeEditorColorSchemes.THEME_GITHUB_LIGHT;
            editor.setColorScheme(CodeEditorColorSchemes.loadTextMateColorScheme(theme));
        } else {
            editor.setColorScheme(isDark ? new SchemeDarcula() : new SchemeGitHub());
        }

        getMaterialStyledScheme(editor, true);
        editor.setPinLineNumber(true);
        editor.rerunAnalysis();
    }
}