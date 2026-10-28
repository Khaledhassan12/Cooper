package com.besome.sketch.tools

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import com.besome.sketch.lib.base.BaseAppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import a.a.a.GB
import pro.sketchware.R
import pro.sketchware.utility.SketchwareUtil

class CollectErrorActivity : BaseAppCompatActivity() {

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val error = intent.getStringExtra("error")
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.common_error_an_error_occurred)
            .setMessage(
                "An error occurred while running Sketchware Pro. " +
                    "Do you want to report this error log so that we can fix it? " +
                    "No personal information will be included."
            )
            .setPositiveButton("Copy", null)
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setNeutralButton("Show error", null)
            .setCancelable(false)
            .show()

        val messageView = requireNotNull(dialog.findViewById<TextView>(android.R.id.message)) {
            "The error-report dialog must contain its message view."
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            messageView.setTextIsSelectable(true)
            messageView.text = error
        }
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val info = try {
                packageManager.getPackageInfo(packageName, 0)
            } catch (exception: PackageManager.NameNotFoundException) {
                messageView.setTextIsSelectable(true)
                messageView.text =
                    "Somehow couldn't get package info. Stack trace:\n" +
                        Log.getStackTraceString(exception)
                return@setOnClickListener
            }

            val applicationInfo = requireNotNull(info.applicationInfo) {
                "Package information did not include ApplicationInfo."
            }
            val fileSizeInBytes = File(applicationInfo.sourceDir).length()
            val deviceInfo = "Sketchware Pro " + info.versionName + " (" + info.versionCode + ")\n" +
                "base.apk size: " + Formatter.formatFileSize(this, fileSizeInBytes) +
                " (" + fileSizeInBytes + " B)\n" +
                "Locale: " + GB.g(applicationContext) + "\n" +
                "SDK version: " + Build.VERSION.SDK_INT + "\n" +
                "Brand: " + Build.BRAND + "\n" +
                "Manufacturer: " + Build.MANUFACTURER + "\n" +
                "Model: " + Build.MODEL

            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newPlainText(
                    "error",
                    deviceInfo + "\n\n" + "\u0060\u0060\u0060" + "\n" + error + "\n" + "\u0060\u0060\u0060"
                )
            )
            runOnUiThread { SketchwareUtil.toast("Copied", Toast.LENGTH_LONG) }
        }
    }
}