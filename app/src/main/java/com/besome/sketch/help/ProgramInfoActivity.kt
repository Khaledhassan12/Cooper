package com.besome.sketch.help

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import com.besome.sketch.lib.base.BaseAppCompatActivity
import com.besome.sketch.lib.ui.PropertyOneLineItem
import com.besome.sketch.lib.ui.PropertyTwoLineItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import a.a.a.GB
import a.a.a.bB
import a.a.a.mB
import a.a.a.wB
import mod.hey.studios.util.Helper
import pro.sketchware.R
import pro.sketchware.databinding.ProgramInfoBinding

class ProgramInfoActivity : BaseAppCompatActivity() {

    private lateinit var binding: ProgramInfoBinding

    private fun addTwoLineItem(key: Int, name: Int, description: Int) {
        addTwoLineItem(key, Helper.getResString(name), Helper.getResString(description))
    }

    private fun addTwoLineItem(
        key: Int,
        name: Int,
        description: Int,
        hideDivider: Boolean
    ) {
        addTwoLineItem(key, Helper.getResString(name), Helper.getResString(description), hideDivider)
    }

    private fun addTwoLineItem(key: Int, name: String, description: String) {
        addTwoLineItem(key, name, description, false)
    }

    private fun addTwoLineItem(
        key: Int,
        name: String,
        description: String,
        hideDivider: Boolean
    ) {
        val item = PropertyTwoLineItem(this)
        item.setKey(key)
        item.setName(name)
        item.setDesc(description)
        item.setHideDivider(hideDivider)
        binding.content.addView(item)
        item.setOnClickListener(this::handleItem)
    }

    private fun addSingleLineItem(key: Int, name: Int) {
        addSingleLineItem(key, Helper.getResString(name))
    }

    private fun addSingleLineItem(key: Int, name: Int, hideDivider: Boolean) {
        addSingleLineItem(key, Helper.getResString(name), hideDivider)
    }

    private fun addSingleLineItem(key: Int, name: String) {
        addSingleLineItem(key, name, false)
    }

    private fun addSingleLineItem(key: Int, name: String, hideDivider: Boolean) {
        val item = PropertyOneLineItem(this)
        item.setKey(key)
        item.setName(name)
        item.setHideDivider(hideDivider)
        binding.content.addView(item)
        if (key == ITEM_SYSTEM_INFORMATION || key == ITEM_OPEN_SOURCE_LICENSES) {
            item.setOnClickListener(this::handleItem)
        }
    }

    private fun resetDialog(@Suppress("UNUSED_PARAMETER") view: View) {
        val dialog = MaterialAlertDialogBuilder(this)
        dialog.setTitle(Helper.getResString(R.string.program_information_reset_system_title))
        dialog.setIcon(R.drawable.rollback_96)
        val rootView = wB.a(this, R.layout.all_init_popup)
        val radioGroup = rootView.findViewById<RadioGroup>(R.id.rg_type)
        rootView.findViewById<RadioButton>(R.id.rb_all).text =
            Helper.getResString(R.string.program_information_reset_system_title_all_settings_data)
        rootView.findViewById<RadioButton>(R.id.rb_only_config).text =
            Helper.getResString(R.string.program_information_reset_system_title_all_settings)
        dialog.setView(rootView)
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_yes)) { dialogInterface, _ ->
            if (!mB.a()) {
                val buttonId = radioGroup.checkedRadioButtonId
                val resetOnlySettings = buttonId != R.id.rb_all
                dialogInterface.dismiss()
                setResult(RESULT_OK, intent.putExtra("onlyConfig", resetOnlySettings))
                finish()
            }
        }
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null)
        dialog.show()
    }

    private fun handleItem(view: View) {
        if (mB.a()) return

        when (view) {
            is PropertyOneLineItem -> when (view.key) {
                ITEM_SYSTEM_INFORMATION -> toSystemInfoActivity()
                ITEM_OPEN_SOURCE_LICENSES -> {
                    if (!GB.h(applicationContext)) {
                        bB.a(
                            applicationContext,
                            Helper.getResString(R.string.common_message_check_network),
                            bB.TOAST_NORMAL
                        ).show()
                    } else {
                        toLicenseActivity()
                    }
                }
            }

            is PropertyTwoLineItem -> when (view.key) {
                ITEM_DOCS_LOG -> openUrl(Helper.getResString(R.string.link_docs_url))
                ITEM_SUGGEST_IDEAS -> openUrl(Helper.getResString(R.string.link_ideas_url))
                ITEM_TELEGRAM -> openUrl(Helper.getResString(R.string.link_telegram_invite))
                ITEM_DISCORD -> openUrl(Helper.getResString(R.string.link_discord_invite))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ProgramInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this))
        binding.appVersion.setText(GB.e(applicationContext))
        binding.btnReset.setOnClickListener(this::resetDialog)
        binding.btnUpgrade.setOnClickListener {
            val upgradeIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse(Helper.getResString(R.string.link_github_release))
            )
            startActivity(upgradeIntent)
        }

        addTwoLineItem(ITEM_DOCS_LOG, R.string.program_information_title_docs, R.string.link_docs_url)
        addTwoLineItem(
            ITEM_SUGGEST_IDEAS,
            R.string.program_information_title_suggest_ideas,
            R.string.link_ideas_url
        )
        addSingleLineItem(ITEM_SOCIAL_NETWORK, R.string.title_community)
        addTwoLineItem(ITEM_DISCORD, R.string.title_discord_community, R.string.link_discord_invite)
        addTwoLineItem(ITEM_TELEGRAM, R.string.title_telegram_community, R.string.link_telegram_invite)
        addSingleLineItem(ITEM_SYSTEM_INFORMATION, R.string.program_information_title_system_information)
        addSingleLineItem(ITEM_OPEN_SOURCE_LICENSES, R.string.program_information_title_open_source_license, true)
    }

    private fun toLicenseActivity() {
        val licenseIntent = Intent(applicationContext, LicenseActivity::class.java)
        licenseIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(licenseIntent)
    }

    private fun toSystemInfoActivity() {
        val systemInfoIntent = Intent(applicationContext, SystemInfoActivity::class.java)
        systemInfoIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(systemInfoIntent)
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private companion object {
        const val ITEM_SYSTEM_INFORMATION = 1
        const val ITEM_DOCS_LOG = 4
        const val ITEM_SOCIAL_NETWORK = 5
        const val ITEM_DISCORD = 6
        const val ITEM_TELEGRAM = 8
        const val ITEM_OPEN_SOURCE_LICENSES = 15
        const val ITEM_SUGGEST_IDEAS = 17
    }
}