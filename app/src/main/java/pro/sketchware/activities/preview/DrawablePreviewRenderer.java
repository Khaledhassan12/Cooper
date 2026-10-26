package pro.sketchware.activities.preview;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Picture;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.PictureDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;

import androidx.appcompat.content.res.AppCompatResources;

import com.bobur.androidsvg.SVG;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import a.a.a.wq;
import mod.bobur.VectorDrawableParser;
import pro.sketchware.SketchApplication;
import pro.sketchware.activities.resourceseditor.components.utils.ColorsEditorManager;
import pro.sketchware.activities.resourceseditor.components.utils.DimensEditorManager;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.PropertiesUtil;

public class DrawablePreviewRenderer {

    private static final String TAG = "DrawablePreviewRenderer";
    private static final int MAX_DEPTH = 5;

    public static Drawable renderFromContent(String sc_id, File file, String content, boolean isNightMode) {
        return renderFromContent(sc_id, file, content, isNightMode, 0);
    }

    private static Drawable renderFromContent(String sc_id, File file, String content, boolean isNightMode, int depth) {
        if (content == null) content = "";

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
            Node root = null;
            try {
                root = parseDom(content);
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse drawable XML", e);
            }

            boolean isVector = root != null ? "vector".equals(root.tag) : content.contains("<vector");
            if (isVector) {
                try {
                    VectorDrawableParser parser = new VectorDrawableParser(content);
                    String svg = parser.toSvg();
                    SVG svgObj = SVG.getFromString(svg);
                    return new PictureDrawable(svgObj.renderToPicture());
                } catch (Exception e) {
                    Log.e(TAG, "Failed to render VectorDrawable", e);
                    return null;
                }
            }

            if (root != null) {
                try {
                    return new Builder(sc_id, isNightMode).build(root, depth);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to render drawable <" + root.tag + ">", e);
                    GradientDrawable fallback = new GradientDrawable();
                    fallback.setColor(Color.LTGRAY);
                    return fallback;
                }
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
        if (depth > MAX_DEPTH || name == null || name.isEmpty()) return null;

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
            return renderFromContent(sc_id, targetFile, content, isNightMode, depth);
        }
        return null;
    }

    /* ---------------------------------------------------------------------------------------- */
    /* Minimal XML tree, so nested drawables (ripple > item > shape, layer-list, ...) can be read */
    /* ---------------------------------------------------------------------------------------- */

    private static class Node {
        final String tag;
        final HashMap<String, String> attrs = new HashMap<>();
        final ArrayList<Node> children = new ArrayList<>();

        Node(String tag) {
            this.tag = tag;
        }

        String attr(String name) {
            String value = attrs.get(name);
            return value == null ? "" : value.trim();
        }

        boolean has(String name) {
            return !attr(name).isEmpty();
        }
    }

    private static Node parseDom(String xml) throws Exception {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser parser = factory.newPullParser();
        parser.setInput(new StringReader(xml));

        ArrayList<Node> stack = new ArrayList<>();
        Node root = null;
        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                Node node = new Node(parser.getName());
                for (int i = 0; i < parser.getAttributeCount(); i++) {
                    node.attrs.put(parser.getAttributeName(i), parser.getAttributeValue(i));
                }
                if (stack.isEmpty()) {
                    if (root == null) root = node;
                } else {
                    stack.get(stack.size() - 1).children.add(node);
                }
                stack.add(node);
            } else if (event == XmlPullParser.END_TAG) {
                if (!stack.isEmpty()) stack.remove(stack.size() - 1);
            }
            event = parser.next();
        }
        return root;
    }

    /* ---------------------------------------------------------------------------------------- */
    /* Drawable builder: shape, ripple, selector, layer-list, inset                              */
    /* ---------------------------------------------------------------------------------------- */

    private static class Builder {
        private final String scId;
        private final boolean night;
        private final ColorsEditorManager colors;
        private ArrayList<HashMap<String, Object>> dimens;

        Builder(String scId, boolean night) {
            this.scId = scId;
            this.night = night;
            this.colors = new ColorsEditorManager(scId);
        }

        Drawable build(Node node, int depth) {
            if (node == null || depth > MAX_DEPTH) return null;
            return switch (node.tag) {
                case "shape" -> buildShape(node);
                case "ripple" -> buildRipple(node, depth);
                case "selector" -> buildSelector(node, depth);
                case "layer-list" -> buildLayerList(node, depth);
                case "inset" -> buildInset(node, depth);
                case "item" -> buildItem(node, depth);
                default -> null;
            };
        }

        /* ---- shape ---- */

        private Drawable buildShape(Node node) {
            GradientDrawable gd = new GradientDrawable();
            String shapeStr = node.attr("shape");
            if ("oval".equals(shapeStr)) gd.setShape(GradientDrawable.OVAL);
            else if ("line".equals(shapeStr)) gd.setShape(GradientDrawable.LINE);
            else if ("ring".equals(shapeStr)) gd.setShape(GradientDrawable.RING);
            else gd.setShape(GradientDrawable.RECTANGLE);

            Node gradient = null;
            for (Node child : node.children) {
                switch (child.tag) {
                    case "solid" -> {
                        if (child.has("color")) {
                            gd.setColor(color(child.attr("color"), Color.TRANSPARENT));
                        }
                    }
                    case "stroke" -> {
                        int width = (int) dimen(child.attr("width"), 0);
                        int strokeColor = color(child.attr("color"), Color.TRANSPARENT);
                        float dashWidth = dimen(child.attr("dashWidth"), 0);
                        float dashGap = dimen(child.attr("dashGap"), 0);
                        if (dashWidth > 0) {
                            gd.setStroke(width, strokeColor, dashWidth, dashGap);
                        } else {
                            gd.setStroke(width, strokeColor);
                        }
                    }
                    case "corners" -> applyCorners(gd, child);
                    case "size" -> {
                        int w = (int) dimen(child.attr("width"), -1);
                        int h = (int) dimen(child.attr("height"), -1);
                        gd.setSize(w, h);
                    }
                    case "gradient" -> gradient = child;
                    default -> {
                    }
                }
            }
            // Like on a real device, a gradient wins over a solid color.
            if (gradient != null) applyGradient(gd, gradient);
            return gd;
        }

        private void applyCorners(GradientDrawable gd, Node corners) {
            float radius = dimen(corners.attr("radius"), 0);
            float tl = corners.has("topLeftRadius") ? dimen(corners.attr("topLeftRadius"), radius) : radius;
            float tr = corners.has("topRightRadius") ? dimen(corners.attr("topRightRadius"), radius) : radius;
            float bl = corners.has("bottomLeftRadius") ? dimen(corners.attr("bottomLeftRadius"), radius) : radius;
            float br = corners.has("bottomRightRadius") ? dimen(corners.attr("bottomRightRadius"), radius) : radius;
            if (tl == radius && tr == radius && bl == radius && br == radius) {
                gd.setCornerRadius(radius);
            } else {
                gd.setCornerRadii(new float[]{tl, tl, tr, tr, br, br, bl, bl});
            }
        }

        private void applyGradient(GradientDrawable gd, Node gradient) {
            int start = color(gradient.attr("startColor"), Color.TRANSPARENT);
            int end = color(gradient.attr("endColor"), Color.TRANSPARENT);
            int[] gradientColors;
            if (gradient.has("centerColor")) {
                gradientColors = new int[]{start, color(gradient.attr("centerColor"), Color.TRANSPARENT), end};
            } else {
                gradientColors = new int[]{start, end};
            }

            String type = gradient.attr("type");
            if ("radial".equals(type)) {
                gd.setGradientType(GradientDrawable.RADIAL_GRADIENT);
                float radius = dimen(gradient.attr("gradientRadius"), 0);
                gd.setGradientRadius(radius > 0 ? radius : dimen("100dp", 100));
            } else if ("sweep".equals(type)) {
                gd.setGradientType(GradientDrawable.SWEEP_GRADIENT);
            } else {
                gd.setGradientType(GradientDrawable.LINEAR_GRADIENT);
                gd.setOrientation(orientationFromAngle(gradient.attr("angle")));
            }
            gd.setColors(gradientColors);
        }

        private GradientDrawable.Orientation orientationFromAngle(String angleStr) {
            int angle = 0;
            try {
                if (!angleStr.isEmpty()) angle = Integer.parseInt(angleStr);
            } catch (NumberFormatException ignored) {
            }
            angle = ((angle % 360) + 360) % 360;
            return switch (angle / 45) {
                case 1 -> GradientDrawable.Orientation.BL_TR;
                case 2 -> GradientDrawable.Orientation.BOTTOM_TOP;
                case 3 -> GradientDrawable.Orientation.BR_TL;
                case 4 -> GradientDrawable.Orientation.RIGHT_LEFT;
                case 5 -> GradientDrawable.Orientation.TR_BL;
                case 6 -> GradientDrawable.Orientation.TOP_BOTTOM;
                case 7 -> GradientDrawable.Orientation.TL_BR;
                default -> GradientDrawable.Orientation.LEFT_RIGHT;
            };
        }

        /* ---- ripple ---- */

        private Drawable buildRipple(Node node, int depth) {
            int rippleColor = color(node.attr("color"), 0x33000000);
            ArrayList<Drawable> content = new ArrayList<>();
            Drawable mask = null;

            for (Node child : node.children) {
                if (!child.tag.equals("item")) continue;
                Drawable d = buildItem(child, depth);
                if (d == null) continue;
                if (child.attr("id").endsWith("/mask")) {
                    mask = d;
                } else {
                    content.add(d);
                }
            }

            Drawable contentDrawable = null;
            if (content.size() == 1) {
                contentDrawable = content.get(0);
            } else if (content.size() > 1) {
                contentDrawable = new LayerDrawable(content.toArray(new Drawable[0]));
            }
            return new RippleDrawable(ColorStateList.valueOf(rippleColor), contentDrawable, mask);
        }

        /* ---- selector ---- */

        private Drawable buildSelector(Node node, int depth) {
            StateListDrawable stateList = new StateListDrawable();
            boolean hasState = false;
            for (Node child : node.children) {
                if (!child.tag.equals("item")) continue;
                Drawable d = buildItem(child, depth);
                if (d == null) continue;
                stateList.addState(statesOf(child), d);
                hasState = true;
            }
            return hasState ? stateList : null;
        }

        private int[] statesOf(Node item) {
            ArrayList<Integer> states = new ArrayList<>();
            for (Map.Entry<String, String> entry : item.attrs.entrySet()) {
                int id = stateId(entry.getKey());
                if (id == 0) continue;
                boolean on = Boolean.parseBoolean(entry.getValue().trim());
                states.add(on ? id : -id);
            }
            int[] result = new int[states.size()];
            for (int i = 0; i < result.length; i++) result[i] = states.get(i);
            return result;
        }

        private int stateId(String name) {
            return switch (name) {
                case "state_pressed" -> android.R.attr.state_pressed;
                case "state_focused" -> android.R.attr.state_focused;
                case "state_selected" -> android.R.attr.state_selected;
                case "state_checked" -> android.R.attr.state_checked;
                case "state_checkable" -> android.R.attr.state_checkable;
                case "state_enabled" -> android.R.attr.state_enabled;
                case "state_activated" -> android.R.attr.state_activated;
                case "state_hovered" -> android.R.attr.state_hovered;
                case "state_window_focused" -> android.R.attr.state_window_focused;
                default -> 0;
            };
        }

        /* ---- layer-list ---- */

        private Drawable buildLayerList(Node node, int depth) {
            ArrayList<Drawable> layers = new ArrayList<>();
            ArrayList<Node> layerNodes = new ArrayList<>();
            for (Node child : node.children) {
                if (!child.tag.equals("item")) continue;
                Drawable d = buildItem(child, depth);
                if (d == null) continue;
                layers.add(d);
                layerNodes.add(child);
            }
            if (layers.isEmpty()) return null;

            LayerDrawable layerDrawable = new LayerDrawable(layers.toArray(new Drawable[0]));
            for (int i = 0; i < layerNodes.size(); i++) {
                Node item = layerNodes.get(i);
                int left = (int) dimen(item.has("left") ? item.attr("left") : item.attr("start"), 0);
                int top = (int) dimen(item.attr("top"), 0);
                int right = (int) dimen(item.has("right") ? item.attr("right") : item.attr("end"), 0);
                int bottom = (int) dimen(item.attr("bottom"), 0);
                layerDrawable.setLayerInset(i, left, top, right, bottom);
                if (item.has("width")) layerDrawable.setLayerWidth(i, (int) dimen(item.attr("width"), -1));
                if (item.has("height")) layerDrawable.setLayerHeight(i, (int) dimen(item.attr("height"), -1));
                if (item.has("gravity")) layerDrawable.setLayerGravity(i, parseGravity(item.attr("gravity")));
            }
            return layerDrawable;
        }

        private int parseGravity(String value) {
            int gravity = Gravity.NO_GRAVITY;
            for (String part : value.split("\\|")) {
                switch (part.trim()) {
                    case "top" -> gravity |= Gravity.TOP;
                    case "bottom" -> gravity |= Gravity.BOTTOM;
                    case "left" -> gravity |= Gravity.LEFT;
                    case "right" -> gravity |= Gravity.RIGHT;
                    case "start" -> gravity |= Gravity.START;
                    case "end" -> gravity |= Gravity.END;
                    case "center" -> gravity |= Gravity.CENTER;
                    case "center_vertical" -> gravity |= Gravity.CENTER_VERTICAL;
                    case "center_horizontal" -> gravity |= Gravity.CENTER_HORIZONTAL;
                    case "fill" -> gravity |= Gravity.FILL;
                    case "fill_vertical" -> gravity |= Gravity.FILL_VERTICAL;
                    case "fill_horizontal" -> gravity |= Gravity.FILL_HORIZONTAL;
                    default -> {
                    }
                }
            }
            return gravity;
        }

        /* ---- inset ---- */

        private Drawable buildInset(Node node, int depth) {
            Drawable inner = buildItem(node, depth);
            if (inner == null) return null;
            float all = dimen(node.attr("inset"), 0);
            int left = (int) (node.has("insetLeft") ? dimen(node.attr("insetLeft"), all) : all);
            int top = (int) (node.has("insetTop") ? dimen(node.attr("insetTop"), all) : all);
            int right = (int) (node.has("insetRight") ? dimen(node.attr("insetRight"), all) : all);
            int bottom = (int) (node.has("insetBottom") ? dimen(node.attr("insetBottom"), all) : all);
            return new InsetDrawable(inner, left, top, right, bottom);
        }

        /* ---- item: either android:drawable="@drawable/x" or a nested drawable ---- */

        private Drawable buildItem(Node item, int depth) {
            String ref = item.attr("drawable");
            if (!ref.isEmpty()) return resolveDrawableRef(ref, depth);
            for (Node child : item.children) {
                Drawable d = build(child, depth);
                if (d != null) return d;
            }
            return null;
        }

        private Drawable resolveDrawableRef(String ref, int depth) {
            if (ref.startsWith("#") || ref.startsWith("@color/") || ref.startsWith("?")) {
                return new ColorDrawable(color(ref, Color.TRANSPARENT));
            }
            String name;
            if (ref.startsWith("@drawable/")) {
                name = ref.substring("@drawable/".length());
            } else if (ref.startsWith("@")) {
                return null;
            } else {
                name = ref;
            }

            Drawable fromProject = getDrawableFromProject(scId, name, depth + 1, night);
            if (fromProject != null) return fromProject;

            try {
                Context context = SketchApplication.getContext();
                int id = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
                if (id != 0) return AppCompatResources.getDrawable(context, id);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load built-in drawable " + name, e);
            }
            return null;
        }

        /* ---- value helpers ---- */

        private int color(String raw, int fallback) {
            if (raw == null || raw.isEmpty()) return fallback;
            try {
                return PropertiesUtil.parseColor(colors.getColorValue(SketchApplication.getContext(), raw, 3, night));
            } catch (Exception e) {
                return fallback;
            }
        }

        private float dimen(String raw, float fallback) {
            if (raw == null || raw.trim().isEmpty()) return fallback;
            String value = raw.trim();
            if (value.startsWith("@dimen/")) {
                value = lookupDimen(value);
                if (value == null) return fallback;
                value = value.trim();
            }
            try {
                String number = value.replaceAll("[^0-9.\\-]", "");
                if (number.isEmpty()) return fallback;
                float amount = Float.parseFloat(number);
                String unit = value.toLowerCase().replaceAll("[0-9.\\-\\s]", "");
                DisplayMetrics metrics = SketchApplication.getContext().getResources().getDisplayMetrics();
                return switch (unit) {
                    case "px" -> amount;
                    case "sp" -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, amount, metrics);
                    default -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, amount, metrics);
                };
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        private String lookupDimen(String ref) {
            if (dimens == null) {
                dimens = new ArrayList<>();
                try {
                    String path = wq.b(scId) + "/files/resource/values/dimens.xml";
                    new DimensEditorManager().convertXmlDimensToListMap(FileUtil.readFileIfExist(path), dimens);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to read dimens.xml", e);
                }
            }
            for (HashMap<String, Object> map : dimens) {
                Object key = map.get("key");
                Object value = map.get("value");
                if (key != null && value != null && ref.equals("@dimen/" + key.toString().trim())) {
                    return value.toString();
                }
            }
            return null;
        }
    }
}