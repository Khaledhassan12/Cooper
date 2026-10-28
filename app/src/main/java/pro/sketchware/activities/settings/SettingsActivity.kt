package pro.sketchware.activities.settings

import android.os.Bundle
import androidx.fragment.app.Fragment
import com.besome.sketch.lib.base.BaseAppCompatActivity
import pro.sketchware.databinding.ActivitySettingsBinding
import pro.sketchware.fragments.settings.appearance.SettingsAppearanceFragment
import pro.sketchware.fragments.settings.block.selector.BlockSelectorManagerFragment
import pro.sketchware.fragments.settings.events.EventsManagerFragment

class SettingsActivity : BaseAppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdgeNoContrast()
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fragmentTag = intent.getStringExtra(FRAGMENT_TAG_EXTRA)
        val fragment = when (fragmentTag) {
            SETTINGS_APPEARANCE_FRAGMENT -> SettingsAppearanceFragment()
            EVENTS_MANAGER_FRAGMENT -> EventsManagerFragment()
            BLOCK_SELECTOR_MANAGER_FRAGMENT -> BlockSelectorManagerFragment()
            else -> throw IllegalArgumentException("Unknown fragment tag: $fragmentTag")
        }

        supportFragmentManager.beginTransaction()
            .replace(binding.settingsFragmentContainer.id, fragment)
            .commit()
    }

    companion object {
        const val FRAGMENT_TAG_EXTRA = "fragment_tag"
        const val SETTINGS_APPEARANCE_FRAGMENT = "settings_appearance"
        const val EVENTS_MANAGER_FRAGMENT = "events_manager"
        const val BLOCK_SELECTOR_MANAGER_FRAGMENT = "block_selector_manager"
    }
}