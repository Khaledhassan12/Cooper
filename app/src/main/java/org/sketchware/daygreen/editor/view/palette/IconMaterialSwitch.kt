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
        setWidgetImage(R.drawable.ic_mtrl_switch)
        setWidgetName("MaterialSwitch")
    }

    override fun getBean(): ViewBean {
        return ViewBean().apply {
            type = ViewBeans.VIEW_TYPE_WIDGET_MATERIALSWITCH
            layout.width = ViewGroup.LayoutParams.WRAP_CONTENT
            layout.height = ViewGroup.LayoutParams.WRAP_CONTENT
            convert = "com.google.android.material.materialswitch.MaterialSwitch"
        }
    }
}