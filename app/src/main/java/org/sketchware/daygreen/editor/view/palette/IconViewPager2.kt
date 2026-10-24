package org.sketchware.daygreen.editor.view.palette

import android.content.Context
import android.view.ViewGroup
import com.besome.sketch.beans.ViewBean
import com.besome.sketch.editor.view.AndroidxOrMaterialView
import com.besome.sketch.editor.view.palette.IconBase
import mod.agus.jcoderz.beans.ViewBeans
import pro.sketchware.R

class IconViewPager2(context: Context) : IconBase(context), AndroidxOrMaterialView {

    init {
        setWidgetImage(R.drawable.ic_mtrl_viewpager)
        setWidgetName("ViewPager2")
    }

    override fun getBean(): ViewBean {
        return ViewBean().apply {
            type = ViewBeans.VIEW_TYPE_LAYOUT_VIEWPAGER2
            layout.width = ViewGroup.LayoutParams.MATCH_PARENT
            layout.height = ViewGroup.LayoutParams.MATCH_PARENT
            layout.paddingLeft = 8
            layout.paddingTop = 8
            layout.paddingRight = 8
            layout.paddingBottom = 8
            text.text = name
            convert = "androidx.viewpager2.widget.ViewPager2"
        }
    }
}