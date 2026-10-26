package dev.aldi.sayuti.editor.view.palette;

import android.content.Context;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.Material3View;
import com.besome.sketch.editor.view.palette.IconBase;

import mod.agus.jcoderz.beans.ViewBeans;
import pro.sketchware.R;

public class IconMaterialSwitch extends IconBase implements Material3View {
    public IconMaterialSwitch(Context context) {
        super(context);
        setWidgetImage(R.drawable.ic_mtrl_switch);
        setWidgetName("MaterialSwitch");
    }

    @Override
    public ViewBean getBean() {
        ViewBean bean = new ViewBean();
        bean.type = ViewBeans.VIEW_TYPE_WIDGET_MATERIALSWITCH;
        bean.layout.paddingLeft = 8;
        bean.layout.paddingTop = 8;
        bean.layout.paddingRight = 8;
        bean.layout.paddingBottom = 8;
        bean.text.text = "MaterialSwitch";
        bean.convert = "com.google.android.material.materialswitch.MaterialSwitch";
        return bean;
    }
}
