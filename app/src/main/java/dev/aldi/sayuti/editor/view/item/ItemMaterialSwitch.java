package dev.aldi.sayuti.editor.view.item;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.ItemView;
import com.google.android.material.materialswitch.MaterialSwitch;

public class ItemMaterialSwitch extends MaterialSwitch implements ItemView {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect rect = new Rect();
    private ViewBean viewBean;
    private boolean hasSelection;
    private boolean hasFixed;

    public ItemMaterialSwitch(Context context) {
        super(context);
        paint.setColor(0x9599d5d0);
        setText("MaterialSwitch");
        setChecked(true);
    }

    @Override public ViewBean getBean() { return viewBean; }
    @Override public void setBean(ViewBean viewBean) { this.viewBean = viewBean; }
    @Override public boolean getFixed() { return hasFixed; }
    @Override public void setFixed(boolean fixed) { hasFixed = fixed; }
    public boolean getSelection() { return hasSelection; }

    @Override public void setSelection(boolean selected) {
        hasSelection = selected;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        if (hasSelection) {
            rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            canvas.drawRect(rect, paint);
        }
        super.onDraw(canvas);
    }
}
