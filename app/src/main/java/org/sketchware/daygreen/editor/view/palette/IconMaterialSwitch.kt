package org.sketchware.daygreen.editor.view.palette

import android.content.Context
import android.view.ViewGroup
import com.besome.sketch.beans.ViewBean
import com.besome.sketch.editor.view.palette.IconBase
import mod.agus.jcoderz.beans.ViewBeans
import org.sketchware.daygreen.editor.view.Material3View
import pro.sketchware.R

class IconMaterialSwitch(context: Context) : IconBase(context), Material3View {

    init {
        setWidgetImage(R.drawable.ic_mtrl_toggle)
        setWidgetName("MaterialSwitch")
    }

    override fun getBean(): ViewBean {
        return ViewBean().apply {
            type = ViewBeans.VIEW_TYPE_WIDGET_MATERIALSWITCH
            layout.paddingLeft = 8
            layout.paddingTop = 8
            layout.paddingRight = 8
            layout.paddingBottom = 8
            text.text = "MaterialSwitch"
            convert = "com.google.android.material.materialswitch.MaterialSwitch"
        }
    }
}