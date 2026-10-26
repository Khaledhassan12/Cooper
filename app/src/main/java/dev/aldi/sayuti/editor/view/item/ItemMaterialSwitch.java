package dev.aldi.sayuti.editor.view.item;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import a.a.a.wB;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.ItemView;
import com.google.android.material.materialswitch.MaterialSwitch;

public class ItemMaterialSwitch extends MaterialSwitch implements ItemView {
    private final float dip;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect rect = new Rect();
    private ViewBean viewBean;
    private boolean hasSelection;
    private boolean hasFixed;

    public ItemMaterialSwitch(Context context) {
        super(context);
        dip = wB.a(context, 1.0f);
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

    @Override public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding(
                (int) (left * dip),
                (int) (top * dip),
                (int) (right * dip),
                (int) (bottom * dip)
        );
    }

    @Override protected void onDraw(Canvas canvas) {
        if (hasSelection) {
            rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
            canvas.drawRect(rect, paint);
        }
        super.onDraw(canvas);
    }
}
