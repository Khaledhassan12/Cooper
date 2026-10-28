package extensions.anbui.daydream.activity.project.settings

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Environment
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.besome.sketch.editor.manage.library.LibraryCategoryView
import com.besome.sketch.editor.manage.library.LibraryItemView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import extensions.anbui.daydream.settings.DRSettings
import pro.sketchware.R
import pro.sketchware.databinding.ActivityDaydreamUniversalSettingsBinding
import java.io.File
import java.util.ArrayList

class DayDreamUniversalSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDaydreamUniversalSettingsBinding

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityDaydreamUniversalSettingsBinding.inflate(layoutInflater)
        setContentView(binding.getRoot())
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        initialize()

        if (SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                finish()
            }
        } else {
            onBackPressedDispatcher.addCallback(this) {
                finish()
            }
        }
    }


    fun initialize() {
        val preferences = ArrayList<LibraryCategoryView>()
        val universalCategory = LibraryCategoryView(this)
        universalCategory.setTitle(null)
        preferences.add(universalCategory)

        val backupPref = createSwitchPreference(R.drawable.restore_page_24px, "Backup tool", "Use Cooper's new backup tool instead of the old one.")
        backupPref.sw_enable.visibility = View.VISIBLE
        backupPref.sw_enable.isClickable = true
        DRSettings.getUseBackupTool(this) { backupPref.sw_enable.isChecked = it }
        backupPref.sw_enable.setOnCheckedChangeListener { _, isChecked -> DRSettings.setUseBackupTool(this, isChecked) }
        backupPref.setOnClickListener { backupPref.sw_enable.toggle() }
        universalCategory.addLibraryItem(backupPref, true)

        val cleanPref = createSwitchPreference(R.drawable.cleaning_services_24px, "Auto clean up after building", "Temporary files will be cleaned up after the build is complete.")
        cleanPref.sw_enable.visibility = View.VISIBLE
        cleanPref.sw_enable.isClickable = true
        DRSettings.getAutoCleanUpAfterBuild(this) { cleanPref.sw_enable.isChecked = it }
        cleanPref.sw_enable.setOnCheckedChangeListener { _, isChecked -> DRSettings.setAutoCleanUpAfterBuild(this, isChecked) }
        cleanPref.setOnClickListener { cleanPref.sw_enable.toggle() }
        universalCategory.addLibraryItem(cleanPref, false)

        preferences.forEach { binding.lnAllOptions.addView(it) }
    }

    private fun createSwitchPreference(icon: Int, title: String, desc: String): LibraryItemView {
        val preference = LibraryItemView(this)
        preference.setHideEnabled()
        preference.icon.setImageResource(icon)
        preference.title.text = title
        preference.description.text = desc
        return preference
    }
}