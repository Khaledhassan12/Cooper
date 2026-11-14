package org.sketchware.daygreen.editor.view.palette;

import android.content.Context;

import com.besome.sketch.beans.LayoutBean;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.palette.IconBase;

import mod.agus.jcoderz.beans.ViewBeans;
import pro.sketchware.R;

public class IconFrameLayout extends IconBase {

    public IconFrameLayout(Context context) {
        super(context);
        initialize();
    }

    private void initialize() {
        setWidgetImage(R.drawable.ic_mtrl_view_relative);
        setWidgetName("FrameLayout");
    }

    @Override
    public ViewBean getBean() {
        ViewBean viewBean = new ViewBean();
        viewBean.type = ViewBeans.VIEW_TYPE_LAYOUT_FRAMELAYOUT;
        LayoutBean layoutBean = viewBean.layout;
        layoutBean.width = -1;
        layoutBean.paddingLeft = 8;
        layoutBean.paddingTop = 8;
        layoutBean.paddingRight = 8;
        layoutBean.paddingBottom = 8;
        viewBean.convert = "FrameLayout";
        return viewBean;
    }
}
