package org.sketchware.daygreen.preview

import com.besome.sketch.beans.ViewBean
import mod.agus.jcoderz.beans.ViewBeans

class PreviewWidgetHandler(
    override val viewType: Int,
    private val exactNames: Set<String> = emptySet(),
    private val suffixes: List<String> = emptyList()
) : PreviewViewHandler {

    override fun matchesExact(className: String): Boolean {
        return className in exactNames
    }

    override fun matchesSuffix(className: String): Boolean {
        return suffixes.any { suffix -> className.endsWith(suffix) }
    }

    override fun matches(className: String): Boolean {
        return matchesExact(className) || matchesSuffix(className)
    }

    companion object {
        @JvmStatic
        fun createDefaultHandlers(): List<PreviewWidgetHandler> = listOf(
            // 1. Switch
            PreviewWidgetHandler(
                viewType = ViewBean.VIEW_TYPE_WIDGET_SWITCH,
                exactNames = setOf("Switch", "SwitchCompat", "MaterialSwitch"),
                suffixes = listOf("Switch")
            ),
            // 2. CheckBox
            PreviewWidgetHandler(
                viewType = ViewBean.VIEW_TYPE_WIDGET_CHECKBOX,
                exactNames = setOf("CheckBox", "AppCompatCheckBox", "MaterialCheckBox"),
                suffixes = listOf("CheckBox")
            ),
            // 3. RadioButton
            PreviewWidgetHandler(
                viewType = ViewBeans.VIEW_TYPE_WIDGET_RADIOBUTTON,
                exactNames = setOf("RadioButton", "AppCompatRadioButton", "MaterialRadioButton"),
                suffixes = listOf("RadioButton")
            ),
            // 4. ImageButton & ImageView (sebelum TextView & Button)
            PreviewWidgetHandler(
                viewType = ViewBean.VIEW_TYPE_WIDGET_IMAGEVIEW,
                exactNames = setOf(
                    "ImageButton", "AppCompatImageButton",
                    "ImageView", "AppCompatImageView",
                    "ShapeableImageView", "CircleImageView"
                ),
                suffixes = listOf("ImageButton", "ImageView")
            ),
            // 5. EditText (sebelum TextView)
            PreviewWidgetHandler(
                viewType = ViewBean.VIEW_TYPE_WIDGET_EDITTEXT,
                exactNames = setOf("EditText", "AppCompatEditText", "TextInputEditText"),
                suffixes = listOf("EditText")
            ),
            // 6. SeekBar
            PreviewWidgetHandler(
                viewType = ViewBean.VIEW_TYPE_WIDGET_SEEKBAR,
                exactNames = setOf("SeekBar", "AppCompatSeekBar"),
                suffixes = listOf("SeekBar")
            ),
            // 7. ProgressBar
            PreviewWidgetHandler(
                viewType = ViewBean.VIEW_TYPE_WIDGET_PROGRESSBAR,
                exactNames = setOf("ProgressBar", "ContentLoadingProgressBar"),
                suffixes = listOf("ProgressBar", "ProgressIndicator")
            ),
            // 8. Spinner
            PreviewWidgetHandler(
                viewType = ViewBean.VIEW_TYPE_WIDGET_SPINNER,
                exactNames = setOf("Spinner", "AppCompatSpinner"),
                suffixes = listOf("Spinner")
            ),
            // 9. TextView
            PreviewWidgetHandler(
                viewType = ViewBean.VIEW_TYPE_WIDGET_TEXTVIEW,
                exactNames = setOf("TextView", "AppCompatTextView", "MaterialTextView"),
                suffixes = listOf("TextView")
            ),
            // 10. Button (setelah RadioButton, CheckBox, ImageButton)
            PreviewWidgetHandler(
                viewType = ViewBean.VIEW_TYPE_WIDGET_BUTTON,
                exactNames = setOf("Button", "AppCompatButton", "OutlinedButton"),
                suffixes = listOf("Button")
            ),
            // 11. WebView
            PreviewWidgetHandler(
                viewType = ViewBean.VIEW_TYPE_WIDGET_WEBVIEW,
                exactNames = setOf("WebView"),
                suffixes = listOf("WebView")
            ),
            // 12. ListView
            PreviewWidgetHandler(
                viewType = ViewBean.VIEW_TYPE_WIDGET_LISTVIEW,
                exactNames = setOf("ListView", "ExpandableListView"),
                suffixes = listOf("ListView")
            ),
            // 13. RecyclerView
            PreviewWidgetHandler(
                viewType = ViewBeans.VIEW_TYPE_WIDGET_RECYCLERVIEW,
                exactNames = setOf("RecyclerView"),
                suffixes = listOf("RecyclerView")
            )
        )
    }
}