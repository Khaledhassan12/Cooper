package pro.sketchware.activities.preview;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Picture;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.PictureDrawable;
import android.util.Log;

import com.bobur.androidsvg.SVG;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;

import a.a.a.wq;
import mod.bobur.VectorDrawableParser;
import pro.sketchware.SketchApplication;
import pro.sketchware.activities.resourceseditor.components.utils.ColorsEditorManager;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.PropertiesUtil;

public class DrawablePreviewRenderer {

    private static final String TAG = "DrawablePreviewRenderer";

    public static Drawable renderFromContent(String sc_id, File file, String content, boolean isNightMode) {
        if (file.getName().endsWith(".svg")) {
            try {
                SVG svgObj = SVG.getFromString(content);
                Picture picture = svgObj.renderToPicture();
                return new PictureDrawable(picture);
            } catch (Exception e) {
                Log.e(TAG, "Failed to render SVG", e);
                return null;
            }
        }
        
        if (file.getName().endsWith(".xml")) {
            if (content.contains("<vector")) {
                try {
                    VectorDrawableParser parser = new VectorDrawableParser(content);
                    String svg = parser.toSvg();
                    SVG svgObj = SVG.getFromString(svg);
                    return new PictureDrawable(svgObj.renderToPicture());
                } catch (Exception e) {
                    Log.e(TAG, "Failed to render VectorDrawable", e);
                    return null;
                }
            } else if (content.contains("<shape")) {
                return parseSimpleShape(sc_id, content, isNightMode);
            }
        }
        
        if (file.getName().endsWith(".png") || file.getName().endsWith(".jpg") || file.getName().endsWith(".webp")) {
            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap != null) {
                return new BitmapDrawable(SketchApplication.getContext().getResources(), bitmap);
            }
        }
        
        return null;
    }

    public static Drawable getDrawableFromProject(String sc_id, String name, int depth, boolean isNightMode) {
        if (depth > 5 || name == null || name.isEmpty()) return null;
        
        File resDir = new File(wq.b(sc_id) + "/files/resource/");
        if (!resDir.exists() || !resDir.isDirectory()) return null;

        ArrayList<File> candidates = new ArrayList<>();
        File[] children = resDir.listFiles();
        if (children != null) {
            for (File subDir : children) {
                if (subDir.isDirectory() && subDir.getName().startsWith("drawable")) {
                    candidates.add(subDir);
                }
            }
        }
        
        candidates.sort((f1, f2) -> {
            String n1 = f1.getName();
            String n2 = f2.getName();
            if (isNightMode) {
                if (n1.contains("-night") && !n2.contains("-night")) return -1;
                if (!n1.contains("-night") && n2.contains("-night")) return 1;
            }
            return n2.compareTo(n1); 
        });

        String[] extensions = {".xml", ".png", ".jpg", ".webp", ".9.png", ".svg"};
        File targetFile = null;
        
        for (File dir : candidates) {
            for (String ext : extensions) {
                File f = new File(dir, name + ext);
                if (f.exists()) {
                    targetFile = f;
                    break;
                }
            }
            if (targetFile != null) break;
        }

        if (targetFile != null) {
            String content = FileUtil.readFile(targetFile.getAbsolutePath());
            return renderFromContent(sc_id, targetFile, content, isNightMode);
        }
        return null;
    }
    
    private static Drawable parseSimpleShape(String sc_id, String xml, boolean isNightMode) {
        GradientDrawable gd = new GradientDrawable();
        ColorsEditorManager colors = new ColorsEditorManager(sc_id);
        
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(xml));
            
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if (tag.equals("shape")) {
                        String shapeStr = getAttr(parser, "shape");
                        if ("oval".equals(shapeStr)) gd.setShape(GradientDrawable.OVAL);
                        else if ("line".equals(shapeStr)) gd.setShape(GradientDrawable.LINE);
                        else if ("ring".equals(shapeStr)) gd.setShape(GradientDrawable.RING);
                        else gd.setShape(GradientDrawable.RECTANGLE);
                    } else if (tag.equals("solid")) {
                        String colorStr = getAttr(parser, "color");
                        if (!colorStr.isEmpty()) {
                            gd.setColor(PropertiesUtil.parseColor(colors.getColorValue(SketchApplication.getContext(), colorStr, 3, isNightMode)));
                        }
                    } else if (tag.equals("stroke")) {
                        String colorStr = getAttr(parser, "color");
                        String widthStr = getAttr(parser, "width");
                        int color = Color.TRANSPARENT;
                        int width = 0;
                        if (!colorStr.isEmpty()) color = PropertiesUtil.parseColor(colors.getColorValue(SketchApplication.getContext(), colorStr, 3, isNightMode));
                        if (!widthStr.isEmpty()) width = parseDimen(widthStr);
                        gd.setStroke(width, color);
                    } else if (tag.equals("corners")) {
                        String radStr = getAttr(parser, "radius");
                        if (!radStr.isEmpty()) {
                            gd.setCornerRadius(parseDimen(radStr));
                        }
                    }
                }
                eventType = parser.next();
            }
            return gd;
        } catch (Exception e) {
            Log.e(TAG, "Failed parsing basic shape", e);
            GradientDrawable fallback = new GradientDrawable();
            fallback.setColor(Color.LTGRAY);
            return fallback;
        }
    }
    
    private static String getAttr(XmlPullParser parser, String name) {
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            if (parser.getAttributeName(i).equals(name)) return parser.getAttributeValue(i);
        }
        return "";
    }
    
    private static int parseDimen(String val) {
        try {
            return (int) (Float.parseFloat(val.replaceAll("[^0-9.]", "")) * SketchApplication.getContext().getResources().getDisplayMetrics().density);
        } catch (Exception e) {
            return 0;
        }
    }
}