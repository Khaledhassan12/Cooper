package org.sketchware.daygreen.editor.view.item

import a.a.a.wB
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.besome.sketch.beans.ViewBean
import com.besome.sketch.editor.view.ItemView
import com.google.android.material.materialswitch.MaterialSwitch

class ItemMaterialSwitch(context: Context) : MaterialSwitch(context), ItemView {

    private val dip = wB.a(context, 1.0f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x9599d5d0.toInt()
    }
    private val rect = Rect()
    private var viewBean: ViewBean? = null
    private var hasSelection = false
    private var hasFixed = false

    init {
        text = "MaterialSwitch"
        isChecked = true
    }

    override fun getBean(): ViewBean? = viewBean

    override fun setBean(viewBean: ViewBean) {
        this.viewBean = viewBean
    }

    override fun getFixed(): Boolean = hasFixed

    override fun setFixed(z: Boolean) {
        hasFixed = z
    }

    fun getSelection(): Boolean = hasSelection

    override fun setSelection(z: Boolean) {
        hasSelection = z
        invalidate()
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        super.setPadding(
            (left * dip).toInt(),
            (top * dip).toInt(),
            (right * dip).toInt(),
            (bottom * dip).toInt()
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (hasSelection) {
            rect.set(0, 0, measuredWidth, measuredHeight)
            canvas.drawRect(rect, paint)
        }
        super.onDraw(canvas)
    }
}