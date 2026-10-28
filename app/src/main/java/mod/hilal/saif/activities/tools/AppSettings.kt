package mod.hilal.saif.activities.tools

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Pair
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.besome.sketch.editor.manage.library.LibraryCategoryView
import com.besome.sketch.editor.manage.library.LibraryItemView
import com.besome.sketch.lib.base.BaseAppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.aldi.sayuti.editor.manage.ManageLocalLibraryActivity
import dev.pranav.filepicker.FilePickerCallback
import dev.pranav.filepicker.FilePickerDialogFragment
import dev.pranav.filepicker.FilePickerOptions
import dev.pranav.filepicker.SelectionMode
import java.io.File
import mod.alucard.tn.apksigner.ApkSigner
import mod.hey.studios.code.SrcCodeEditor
import mod.hey.studios.util.Helper
import mod.khaled.logcat.LogReaderActivity
import pro.sketchware.R
import pro.sketchware.activities.editor.component.ManageCustomComponentActivity
import pro.sketchware.activities.settings.SettingsActivity
import pro.sketchware.databinding.ActivityAppSettingsBinding
import pro.sketchware.databinding.DialogSelectApkToSignBinding
import pro.sketchware.utility.FileUtil
import pro.sketchware.utility.SketchwareUtil

class AppSettings : BaseAppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdgeNoContrast()
        super.onCreate(savedInstanceState)

        val binding = ActivityAppSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val main = binding.main
        val mainLeft = main.paddingLeft
        val mainTop = main.paddingTop
        val mainRight = main.paddingRight
        val mainBottom = main.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(main) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(
                mainLeft + insets.left,
                mainTop,
                mainRight + insets.right,
                mainBottom
            )
            windowInsets
        }

        val contentScroll = binding.contentScroll
        val scrollLeft = contentScroll.paddingLeft
        val scrollTop = contentScroll.paddingTop
        val scrollRight = contentScroll.paddingRight
        val scrollBottom = contentScroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(contentScroll) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                scrollLeft,
                scrollTop,
                scrollRight,
                scrollBottom + insets.bottom
            )
            windowInsets
        }

        binding.topAppBar.title = "App Settings"
        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this))
        setupPreferences(binding.content)
    }

    private fun setupPreferences(content: ViewGroup) {
        val preferences = ArrayList<LibraryCategoryView>()

        val managersCategory = LibraryCategoryView(this).apply {
            setTitle("Managers")
        }
        preferences.add(managersCategory)

        managersCategory.addLibraryItem(
            createPreference(
                R.drawable.ic_mtrl_block,
                "Block manager",
                "Manage your own blocks to use in Logic Editor",
                ActivityLauncher(Intent(applicationContext, BlocksManager::class.java))
            ),
            true
        )
        managersCategory.addLibraryItem(
            createPreference(
                R.drawable.ic_mtrl_pull_down,
                "Block selector menu manager",
                "Manage your own block selector menus",
                openSettingsActivity(SettingsActivity.BLOCK_SELECTOR_MANAGER_FRAGMENT)
            ),
            true
        )
        managersCategory.addLibraryItem(
            createPreference(
                R.drawable.ic_mtrl_component,
                "Component manager",
                "Manage your own components",
                ActivityLauncher(Intent(applicationContext, ManageCustomComponentActivity::class.java))
            ),
            true
        )
        managersCategory.addLibraryItem(
            createPreference(
                R.drawable.ic_mtrl_list,
                "Event manager",
                "Manage your own events",
                openSettingsActivity(SettingsActivity.EVENTS_MANAGER_FRAGMENT)
            ),
            true
        )
        managersCategory.addLibraryItem(
            createPreference(
                R.drawable.ic_mtrl_box,
                "Local library manager",
                "Manage and download local libraries",
                ActivityLauncher(
                    Intent(applicationContext, ManageLocalLibraryActivity::class.java),
                    Pair("sc_id", "system")
                )
            ),
            true
        )
        managersCategory.addLibraryItem(
            createPreference(
                R.drawable.ic_mtrl_article,
                Helper.getResString(R.string.design_drawer_menu_title_logcat_reader),
                Helper.getResString(R.string.design_drawer_menu_subtitle_logcat_reader),
                ActivityLauncher(Intent(applicationContext, LogReaderActivity::class.java))
            ),
            true
        )
        managersCategory.addLibraryItem(
            createPreference(
                R.drawable.ic_mtrl_settings,
                "Build Tools",
                "Manage and download build tools",
                ActivityLauncher(Intent(applicationContext, BuildToolsActivity::class.java))
            ),
            true
        )
        managersCategory.addLibraryItem(
            createPreference(
                R.drawable.ic_github,
                getString(R.string.cooper_github_manager_title),
                getString(R.string.cooper_github_link_project_description),
                ActivityLauncher(Intent(applicationContext, GithubManagerActivity::class.java))
            ),
            true
        )
        managersCategory.addLibraryItem(
            createPreference(
                R.drawable.ic_mtrl_folder,
                "SDK Manager",
                "Manage and download SDK platforms",
                ActivityLauncher(Intent(applicationContext, SdkManagerActivity::class.java))
            ),
            false
        )

        val generalCategory = LibraryCategoryView(this).apply {
            setTitle("General")
        }
        preferences.add(generalCategory)

        generalCategory.addLibraryItem(
            createPreference(
                R.drawable.ic_mtrl_settings_applications,
                "App settings",
                "Change general app settings",
                ActivityLauncher(Intent(applicationContext, ConfigActivity::class.java))
            ),
            true
        )
        generalCategory.addLibraryItem(
            createPreference(
                R.drawable.ic_mtrl_folder,
                "Open working directory",
                "Open Sketchware Pro's directory and edit files in it",
                View.OnClickListener { openWorkingDirectory() }
            ),
            true
        )
        generalCategory.addLibraryItem(
            createPreference(
                R.drawable.ic_mtrl_apk_document,
                "Sign an APK file with testkey",
                "Sign an already existing APK file with testkey and signature schemes up to V4",
                View.OnClickListener { signApkFileDialog() }
            ),
            false
        )

        preferences.forEach { content.addView(it) }
    }

    private fun openSettingsActivity(fragmentTag: String): View.OnClickListener =
        View.OnClickListener { view ->
            val intent = Intent(view.context, SettingsActivity::class.java)
            intent.putExtra(SettingsActivity.FRAGMENT_TAG_EXTRA, fragmentTag)
            view.context.startActivity(intent)
        }

    private fun createPreference(
        icon: Int,
        title: String,
        desc: String,
        listener: View.OnClickListener
    ): LibraryItemView {
        return LibraryItemView(this).apply {
            enabled.visibility = View.GONE
            this.icon.setImageResource(icon)
            this.title.text = title
            description.text = desc
            setOnClickListener(listener)
        }
    }

    private fun openWorkingDirectory() {
        val options = FilePickerOptions().apply {
            selectionMode = SelectionMode.BOTH
            multipleSelection = true
            title = "Select an entry to modify"
            initialDirectory = requireNotNull(filesDir.parentFile).absolutePath
        }

        val callback = object : FilePickerCallback() {
            override fun onFilesSelected(files: List<File>) {
                if (files.isEmpty()) return

                val selectedFile = files.first()
                val isDirectory = selectedFile.isDirectory
                if (files.size > 1 || isDirectory) {
                    MaterialAlertDialogBuilder(this@AppSettings)
                        .setTitle("Select an action")
                        .setSingleChoiceItems(arrayOf("Delete"), -1) { actionDialog, _ ->
                            MaterialAlertDialogBuilder(this@AppSettings)
                                .setTitle("Delete " + (if (isDirectory) "folder" else "file") + "?")
                                .setMessage(
                                    "Are you sure you want to delete this " +
                                        (if (isDirectory) "folder" else "file") +
                                        " permanently? This cannot be undone."
                                )
                                .setPositiveButton(R.string.common_word_delete) { deleteDialog, _ ->
                                    files.forEach { file -> FileUtil.deleteFile(file.absolutePath) }
                                    deleteDialog.dismiss()
                                }
                                .setNegativeButton(R.string.common_word_cancel, null)
                                .show()
                            actionDialog.dismiss()
                        }
                        .show()
                } else {
                    MaterialAlertDialogBuilder(this@AppSettings)
                        .setTitle("Select an action")
                        .setSingleChoiceItems(arrayOf("Edit", "Delete"), -1) { actionDialog, which ->
                            when (which) {
                                0 -> {
                                    val intent = Intent(applicationContext, SrcCodeEditor::class.java)
                                    intent.putExtra("title", Uri.fromFile(selectedFile).lastPathSegment)
                                    intent.putExtra("content", selectedFile.absolutePath)
                                    intent.putExtra("xml", "")
                                    startActivity(intent)
                                }
                                1 -> MaterialAlertDialogBuilder(this@AppSettings)
                                    .setTitle("Delete file?")
                                    .setMessage(
                                        "Are you sure you want to delete this file permanently? " +
                                            "This cannot be undone."
                                    )
                                    .setPositiveButton(R.string.common_word_delete) { _, _ ->
                                        FileUtil.deleteFile(selectedFile.absolutePath)
                                    }
                                    .setNegativeButton(R.string.common_word_cancel, null)
                                    .show()
                            }
                            actionDialog.dismiss()
                        }
                        .show()
                }
            }

            override fun onFileSelected(file: File) {
                // This picker is configured for multiple selection; selected files are handled above.
            }
        }

        FilePickerDialogFragment(options, callback)
            .show(supportFragmentManager, "file_picker")
    }

    private fun signApkFileDialog() {
        var isApkSelected = false
        val apkPathBinding = DialogSelectApkToSignBinding.inflate(layoutInflater)
        val apkPathText = apkPathBinding.apkPathTxt

        apkPathBinding.selectFile.setOnClickListener {
            val options = FilePickerOptions().apply {
                extensions = arrayOf("apk")
            }
            val callback = object : FilePickerCallback() {
                override fun onFileSelected(file: File) {
                    isApkSelected = true
                    apkPathText.text = file.absolutePath
                }

                override fun onFilesSelected(files: List<File>) {
                    files.firstOrNull()?.let { file ->
                        isApkSelected = true
                        apkPathText.text = file.absolutePath
                    }
                }
            }
            FilePickerDialogFragment(options, callback)
                .show(supportFragmentManager, "file_picker")
        }

        val apkPathDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Sign APK with testkey")
            .setView(apkPathBinding.root)
            .setPositiveButton("Continue", null)
            .setNegativeButton(Helper.getResString(R.string.common_word_cancel), null)
            .setCancelable(false)
            .create()

        apkPathDialog.setOnShowListener {
            apkPathDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!isApkSelected) {
                    SketchwareUtil.toast("Please select an APK file to sign", Toast.LENGTH_SHORT)
                    com.besome.sketch.editor.view.ViewEditor.shakeView(apkPathBinding.selectFile)
                    return@setOnClickListener
                }

                val inputApkPath = Helper.getText(apkPathText)
                val outputApkFileName = Uri.fromFile(File(inputApkPath)).lastPathSegment
                if (outputApkFileName.isNullOrBlank()) {
                    SketchwareUtil.toast("Selected APK path is invalid", Toast.LENGTH_SHORT)
                    return@setOnClickListener
                }

                val outputApkPath = File(
                    Environment.getExternalStorageDirectory(),
                    "sketchware/signed_apk/" + outputApkFileName
                ).absolutePath

                if (File(outputApkPath).exists()) {
                    MaterialAlertDialogBuilder(this@AppSettings)
                        .setIcon(R.drawable.color_save_as_new_96)
                        .setTitle("File exists")
                        .setMessage(
                            "An APK named " + outputApkFileName +
                                " already exists at /sketchware/signed_apk/. Overwrite it?"
                        )
                        .setNegativeButton(Helper.getResString(R.string.common_word_cancel), null)
                        .setPositiveButton("Overwrite") { _, _ ->
                            apkPathDialog.dismiss()
                            signApkFileWithDialog(
                                inputApkPath,
                                outputApkPath,
                                true,
                                null,
                                null,
                                null,
                                null
                            )
                        }
                        .show()
                } else {
                    apkPathDialog.dismiss()
                    signApkFileWithDialog(
                        inputApkPath,
                        outputApkPath,
                        true,
                        null,
                        null,
                        null,
                        null
                    )
                }
            }
        }
        apkPathDialog.show()
    }

    private fun signApkFileWithDialog(
        inputApkPath: String,
        outputApkPath: String,
        useTestkey: Boolean,
        keyStorePath: String?,
        keyStorePassword: String?,
        keyStoreKeyAlias: String?,
        keyPassword: String?
    ) {
        val buildingRoot = layoutInflater.inflate(R.layout.build_progress_msg_box, null, false)
        val layoutQuiz = buildingRoot.findViewById<LinearLayout>(R.id.layout_quiz)
        val tvProgress = buildingRoot.findViewById<TextView>(R.id.tv_progress)
        val scrollView = ScrollView(this)
        val tvLog = TextView(this)
        scrollView.addView(tvLog)
        layoutQuiz.addView(scrollView)
        tvProgress.text = "Signing APK..."

        val buildingDialog = MaterialAlertDialogBuilder(this)
            .setView(buildingRoot)
            .create()
        buildingDialog.show()

        ApkSigner.LogCallback.errorCount.set(0)
        val signer = ApkSigner()
        val callback = ApkSigner.LogCallback { line ->
            runOnUiThread { tvLog.setText(Helper.getText(tvLog) + line) }
        }

        Thread {
            if (useTestkey) {
                signer.signWithTestKey(inputApkPath, outputApkPath, callback)
            } else {
                signer.signWithKeyStore(
                    inputApkPath,
                    outputApkPath,
                    requireNotNull(keyStorePath),
                    requireNotNull(keyStorePassword),
                    requireNotNull(keyStoreKeyAlias),
                    requireNotNull(keyPassword),
                    callback
                )
            }

            runOnUiThread {
                if (ApkSigner.LogCallback.errorCount.get() == 0) {
                    buildingDialog.dismiss()
                    SketchwareUtil.toast(
                        "Successfully saved signed APK to: /Internal storage/sketchware/signed_apk/" +
                            Uri.fromFile(File(outputApkPath)).lastPathSegment,
                        Toast.LENGTH_LONG
                    )
                } else {
                    tvProgress.text = "An error occurred. Check the log for more details."
                }
            }
        }.start()
    }

    private inner class ActivityLauncher(
        private val launchIntent: Intent,
        private val optionalExtra: Pair<String, String>? = null
    ) : View.OnClickListener {
        override fun onClick(view: View) {
            optionalExtra?.let { extra ->
                launchIntent.putExtra(extra.first, extra.second)
            }
            startActivity(launchIntent)
        }
    }
}