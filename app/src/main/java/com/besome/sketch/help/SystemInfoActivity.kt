package com.besome.sketch.help

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.besome.sketch.lib.base.BaseAppCompatActivity
import com.besome.sketch.lib.ui.PropertyOneLineItem
import com.besome.sketch.lib.ui.PropertyTwoLineItem
import a.a.a.GB
import a.a.a.mB
import mod.hey.studios.util.Helper
import pro.sketchware.R
import pro.sketchware.databinding.ActivitySystemInfoBinding

class SystemInfoActivity : BaseAppCompatActivity() {

    private lateinit var binding: ActivitySystemInfoBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdgeNoContrast()
        super.onCreate(savedInstanceState)
        binding = ActivitySystemInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this))

        addApiLevelInfo()
        addAndroidVersionNameInfo()
        addScreenResolutionInfo()
        addScreenDpiInfo()
        addModelNameInfo()
        addDeveloperOptionsShortcut()
    }

    private fun addInfo(key: Int, name: String, description: String) {
        val item = PropertyTwoLineItem(this)
        item.setKey(key)
        item.setName(name)
        item.setDesc(description)
        binding.content.addView(item)
    }

    private fun addAndroidVersionNameInfo() {
        addInfo(
            1,
            Helper.getResString(R.string.system_information_title_android_version),
            GB.b() + "(" + Build.VERSION.RELEASE + ")"
        )
    }

    private fun addApiLevelInfo() {
        addInfo(
            0,
            Helper.getResString(R.string.system_information_title_android_version),
            "API - " + Build.VERSION.SDK_INT
        )
    }

    private fun addDeveloperOptionsShortcut() {
        val item = PropertyOneLineItem(this)
        item.setKey(5)
        item.setName(Helper.getResString(R.string.system_information_developer_options))
        binding.content.addView(item)
        item.setOnClickListener {
            if (!mB.a()) {
                try {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                    // Device does not expose a developer-options settings screen.
                }
            }
        }
    }

    private fun addScreenDpiInfo() {
        val dpiXY = GB.b(this)
        addInfo(
            3,
            Helper.getResString(R.string.system_information_dpi),
            java.lang.String.valueOf(dpiXY[0])
        )
    }

    private fun addModelNameInfo() {
        addInfo(
            4,
            Helper.getResString(R.string.system_information_model_name),
            Build.MODEL
        )
    }

    private fun addScreenResolutionInfo() {
        val widthHeight = GB.c(this)
        addInfo(
            2,
            Helper.getResString(R.string.system_information_system_resolution),
            widthHeight[0].toString() + " x " + widthHeight[1]
        )
    }
}