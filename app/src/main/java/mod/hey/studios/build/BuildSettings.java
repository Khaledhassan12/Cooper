package mod.hey.studios.build;

import java.io.File;
import java.io.Serializable;

import mod.hey.studios.project.ProjectSettings;
import pro.sketchware.utility.CooperStorageMigration;

public class BuildSettings extends ProjectSettings implements Serializable {

    public static final String SETTING_ANDROID_JAR_PATH = "android_jar";
    public static final String SETTING_CLASSPATH = "classpath";
    public static final String SETTING_DEXER = "dexer";
    public static final String SETTING_JAVA_VERSION = "java_ver";
    public static final String SETTING_NO_HTTP_LEGACY = "no_http_legacy";
    public static final String SETTING_NO_WARNINGS = "no_warn";
    
    // ====== CUSTOM SETTINGS (BARU DITAMBAHKAN) ======
    public static final String SETTING_BUILD_MODE = "build_mode";
    public static final String BUILD_MODE_DEBUG = "Debug";
    public static final String BUILD_MODE_RELEASE = "Release";

    public static final String SETTING_DEBUG_KEYSTORE_PATH = "debug_keystore_path";
    public static final String SETTING_DEBUG_KEYSTORE_ALIAS = "debug_keystore_alias";
    public static final String SETTING_DEBUG_STORE_PASS = "debug_store_pass";
    public static final String SETTING_DEBUG_KEY_PASS = "debug_key_pass";

    public static final String SETTING_RELEASE_KEYSTORE_PATH = "release_keystore_path";
    public static final String SETTING_RELEASE_KEYSTORE_ALIAS = "release_keystore_alias";
    public static final String SETTING_RELEASE_STORE_PASS = "release_store_pass";
    public static final String SETTING_RELEASE_KEY_PASS = "release_key_pass";
    // ===============================================

    public static final String SETTING_ENABLE_LOGCAT = "enable_logcat";
    public static final String SETTING_CMAKE_ABIS = "cmake_abis";
    public static final String SETTING_CMAKE_ABI_ARM64_V8A = "cmake_abi_arm64_v8a";
    public static final String SETTING_CMAKE_ABI_ARMEABI_V7A = "cmake_abi_armeabi_v7a";
    public static final String SETTING_CMAKE_ABI_X86 = "cmake_abi_x86";
    public static final String SETTING_CMAKE_ABI_X86_64 = "cmake_abi_x86_64";

    public static final String ABI_ARM64_V8A = "arm64-v8a";
    public static final String ABI_ARMEABI_V7A = "armeabi-v7a";
    public static final String ABI_X86 = "x86";
    public static final String ABI_X86_64 = "x86_64";

    public static final String SETTING_DEXER_D8 = "D8";
    public static final String SETTING_DEXER_DX = "Dx";
    public static final String SETTING_JAVA_VERSION_1_7 = "1.7";
    public static final String SETTING_JAVA_VERSION_1_8 = "1.8";
    public static final String SETTING_JAVA_VERSION_11 = "11";
    public static final String SETTING_JAVA_VERSION_15 = "15";
    public static final String SETTING_JAVA_VERSION_16 = "16";
    public static final String SETTING_JAVA_VERSION_17 = "17";
    public static final String SETTING_JAVA_VERSION_20 = "20";

    public BuildSettings(String sc_id) {
        super(sc_id);
    }

    @Override
    public String getPath() {
        return new File(CooperStorageMigration.getRootDirectory(), "data/" + sc_id + "/build_config").getAbsolutePath();
    }
}