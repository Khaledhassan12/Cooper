package dev.aldi.sayuti.editor.view.item;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.Gravity;

import androidx.appcompat.widget.AppCompatTextView;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.ItemView;

import a.a.a.wB;

public class ItemViewPager2 extends AppCompatTextView implements ItemView {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect rect = new Rect();
    private final float paddingFactor;
    private ViewBean viewBean;
    private boolean hasSelection;
    private boolean hasFixed;

    public ItemViewPager2(Context context) {
        super(context);
        paddingFactor = wB.a(context, 1.0f);
        paint.setColor(0x9599d5d0);
        setDrawingCacheEnabled(true);
        setTypeface(null, Typeface.BOLD);
        setText("ViewPager2");
        setGravity(Gravity.CENTER);
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

    @Override public void setPadding(int left, int top, int right, int bottom) {
        super.setPadding((int) (left * paddingFactor), (int) (top * paddingFactor),
                (int) (right * paddingFactor), (int) (bottom * paddingFactor));
    }
}
