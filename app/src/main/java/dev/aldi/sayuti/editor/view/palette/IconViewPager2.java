package dev.aldi.sayuti.editor.view.palette;

import android.content.Context;
import android.view.ViewGroup;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.AndroidxOrMaterialView;
import com.besome.sketch.editor.view.palette.IconBase;

import mod.agus.jcoderz.beans.ViewBeans;
import pro.sketchware.R;

public class IconViewPager2 extends IconBase implements AndroidxOrMaterialView {
    public IconViewPager2(Context context) {
        super(context);
        setWidgetImage(R.drawable.ic_mtrl_viewpager);
        setWidgetName("ViewPager2");
    }

    @Override
    public ViewBean getBean() {
        ViewBean bean = new ViewBean();
        bean.type = ViewBeans.VIEW_TYPE_LAYOUT_VIEWPAGER2;
        bean.layout.width = ViewGroup.LayoutParams.MATCH_PARENT;
        bean.layout.height = ViewGroup.LayoutParams.MATCH_PARENT;
        bean.layout.paddingLeft = 8;
        bean.layout.paddingTop = 8;
        bean.layout.paddingRight = 8;
        bean.layout.paddingBottom = 8;
        bean.text.text = getName();
        bean.convert = "androidx.viewpager2.widget.ViewPager2";
        return bean;
    }
}
