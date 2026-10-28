package pro.sketchware.activities.editor.view

import android.os.Bundle
import com.besome.sketch.lib.base.BaseAppCompatActivity
import mod.hey.studios.code.SrcCodeEditor
import mod.hey.studios.util.Helper
import a.a.a.Lx
import pro.sketchware.databinding.ActivityCodeViewerBinding
import pro.sketchware.utility.EditorUtils
import pro.sketchware.utility.UI

class CodeViewerActivity : BaseAppCompatActivity() {

    private lateinit var binding: ActivityCodeViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdgeNoContrast()
        super.onCreate(savedInstanceState)

        binding = ActivityCodeViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val code = intent.getStringExtra("code")
        val scheme = intent.getStringExtra("scheme")
        val scId = intent.getStringExtra("sc_id")

        binding.toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this))
        binding.toolbar.subtitle = scId

        binding.editor.setScId(scId)
        binding.editor.setTypefaceText(EditorUtils.getTypeface(this))
        binding.editor.setTextSize(14)
        binding.editor.setText(Lx.j(code, false))
        binding.editor.setEditable(false)
        binding.editor.setWordwrap(false)
        loadColorScheme(scheme)

        UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false)
        UI.addSystemWindowInsetToMargin(binding.editor, true, false, true, true)
    }

    private fun loadColorScheme(scheme: String?) {
        if (scheme == SCHEME_XML) {
            EditorUtils.loadXmlConfig(binding.editor)
        } else {
            EditorUtils.loadJavaConfig(binding.editor)
        }
        SrcCodeEditor.loadCESettings(this, binding.editor, "act", true)
    }

    companion object {
        const val SCHEME_XML = "xml"
        const val SCHEME_JAVA = "java"
    }
}