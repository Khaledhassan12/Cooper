package pro.sketchware.activities.preview;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatDelegate;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.editor.view.ItemView;
import com.besome.sketch.editor.view.ViewPane;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;

import a.a.a.jC;
import a.a.a.mB;
import pro.sketchware.databinding.ActivityLayoutPreviewBinding;
import pro.sketchware.tools.ViewBeanParser;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.UI;

public class LayoutPreviewActivity extends BaseAppCompatActivity {

    private ViewPane pane;
    private String content;
    private String previewType;
    private String sc_id;
    private boolean isNightMode = false;
    private Drawable checkerboardDrawable;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        ActivityLayoutPreviewBinding binding = ActivityLayoutPreviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        MaterialToolbar toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        
        content = getIntent().getStringExtra("xml");
        sc_id = getIntent().getStringExtra("sc_id");
        previewType = getIntent().getStringExtra("type");
        if (previewType == null) previewType = "layout";
        
        String titlePrefix = "Preview";
        switch (previewType) {
            case "layout": titlePrefix = "Layout Preview"; break;
            case "drawable": titlePrefix = "Drawable Preview"; break;
            case "menu": titlePrefix = "Menu Preview"; break;
            case "svg": titlePrefix = "SVG Preview"; break;
        }

        getSupportActionBar().setTitle(titlePrefix);
        getSupportActionBar().setSubtitle(getIntent().getStringExtra("title"));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowTitleEnabled(true);
        toolbar.setNavigationOnClickListener(v -> {
            if (!mB.a()) {
                onBackPressed();
            }
        });

        binding.btnThemeToggle.setOnClickListener(v -> {
            isNightMode = !isNightMode;
            applyThemePreview(binding.phoneFrame);
        });

        pane = binding.pane;
        ImageView previewImage = binding.previewImage;
        MaterialToolbar previewMenuToolbar = binding.previewMenuToolbar;
        
        isNightMode = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        applyThemePreview(binding.phoneFrame);
        checkerboardDrawable = createCheckerboard();

        if (content == null) {
            SketchwareUtil.toastError("content is null");
            return;
        }

        try {
            if (previewType.equals("layout")) {
                pane.setVisibility(View.VISIBLE);
                previewImage.setVisibility(View.GONE);
                previewMenuToolbar.setVisibility(View.GONE);
                
                pane.initialize(sc_id, true);
                pane.updateRootLayout(sc_id, getIntent().getStringExtra("title"));
                pane.setVerticalScrollBarEnabled(true);
                pane.setResourceManager(jC.d(sc_id));
                UI.addSystemWindowInsetToPadding(pane, false, false, false, true);
                
                ViewBeanParser parser = new ViewBeanParser(content);
                loadViews(parser.parse());
                
            } else if (previewType.equals("drawable") || previewType.equals("svg")) {
                pane.setVisibility(View.GONE);
                previewImage.setVisibility(View.VISIBLE);
                previewMenuToolbar.setVisibility(View.GONE);
                previewImage.setBackground(checkerboardDrawable);
                
                File sourceFile = new File(getIntent().getStringExtra("content"));
                Drawable d = DrawablePreviewRenderer.renderFromContent(sc_id, sourceFile, content, isNightMode);
                if (d != null) {
                    previewImage.setImageDrawable(d);
                } else {
                    SketchwareUtil.toastError("Failed to render drawable/SVG");
                }
                
            } else if (previewType.equals("menu")) {
                pane.setVisibility(View.GONE);
                previewImage.setVisibility(View.GONE);
                previewMenuToolbar.setVisibility(View.VISIBLE);
                
                previewMenuToolbar.setTitle("Menu Preview");
                loadMenuPreview(previewMenuToolbar, content);
            }
        } catch (Exception e) {
            SketchwareUtil.toastError("Preview Error: " + e.getMessage());
        }
    }

    private void loadMenuPreview(MaterialToolbar tb, String xmlStr) {
        try {
            tb.getMenu().clear();
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(xmlStr));
            
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.getName().equals("item")) {
                    String title = "";
                    String idStr = "";
                    String iconStr = "";
                    String showAsAction = "never";
                    
                    for (int i = 0; i < parser.getAttributeCount(); i++) {
                        String name = parser.getAttributeName(i);
                        String value = parser.getAttributeValue(i);
                        if (name.equals("title")) title = value;
                        if (name.equals("id")) idStr = value;
                        if (name.equals("icon")) iconStr = value;
                        if (name.equals("showAsAction")) showAsAction = value;
                    }
                    
                    int menuId = idStr.hashCode() & 0xffff;
                    MenuItem item = tb.getMenu().add(Menu.NONE, menuId, Menu.NONE, title);
                    
                    if (!iconStr.isEmpty() && iconStr.startsWith("@drawable/")) {
                        String iconName = iconStr.replace("@drawable/", "");
                        Drawable d = DrawablePreviewRenderer.getDrawableFromProject(sc_id, iconName, 0, isNightMode);
                        if (d != null) item.setIcon(d);
                    }
                    
                    int actionEnum = MenuItem.SHOW_AS_ACTION_NEVER;
                    if (showAsAction.contains("always")) actionEnum |= MenuItem.SHOW_AS_ACTION_ALWAYS;
                    if (showAsAction.contains("ifRoom")) actionEnum |= MenuItem.SHOW_AS_ACTION_IF_ROOM;
                    if (showAsAction.contains("withText")) actionEnum |= MenuItem.SHOW_AS_ACTION_WITH_TEXT;
                    item.setShowAsAction(actionEnum);
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            SketchwareUtil.toastError("Menu parse error: " + e.getMessage());
        }
    }

    private void applyThemePreview(MaterialCardView phoneFrame) {
        if (isNightMode) {
            phoneFrame.setCardBackgroundColor(0xFF121212); // Dark Surface
        } else {
            phoneFrame.setCardBackgroundColor(0xFFFFFFFF); // Light Surface
        }
        
        // Update Drawable dynamically if it's currently showing
        if (previewType.equals("drawable") || previewType.equals("svg")) {
            try {
                File sourceFile = new File(getIntent().getStringExtra("content"));
                Drawable d = DrawablePreviewRenderer.renderFromContent(sc_id, sourceFile, content, isNightMode);
                if (d != null) {
                    ((ImageView) findViewById(R.id.preview_image)).setImageDrawable(d);
                }
            } catch (Exception ignored) {}
        }
    }

    private Drawable createCheckerboard() {
        Bitmap bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bitmap);
        Paint p = new Paint();
        p.setColor(0xFFCCCCCC);
        c.drawRect(0, 0, 16, 16, p);
        c.drawRect(16, 16, 32, 32, p);
        p.setColor(0xFFFFFFFF);
        c.drawRect(16, 0, 32, 16, p);
        c.drawRect(0, 16, 16, 32, p);
        BitmapDrawable checker = new BitmapDrawable(getResources(), bitmap);
        checker.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        return checker;
    }

    private ItemView loadView(ViewBean view) {
        var itemView = pane.createItemView(view);
        pane.addViewAndUpdateIndex(itemView);
        if (itemView instanceof ItemView sy) {
            sy.setFixed(true);
            return sy;
        }
        return null;
    }

    private ItemView loadViews(ArrayList<ViewBean> views) {
        ItemView itemView = null;
        for (ViewBean view : views) {
            if (views.indexOf(view) == 0) {
                view.parent = "root";
                view.parentType = 0;
                view.preParent = null;
                view.preParentType = -1;
                itemView = loadView(view);
            } else {
                loadView(view);
            }
        }
        return itemView;
    }
}