package extensions.anbui.daydream.project;

import android.util.Log;

import androidx.annotation.Nullable;

import com.besome.sketch.beans.ProjectFileBean;
import a.a.a.lC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import extensions.anbui.daydream.configs.Configs;
import extensions.anbui.daydream.json.JsonUtils;

public class ProjectData {

    public static String TAG = Configs.universalTAG + "ProjectData";

    public static final int TEMPLATE_NON_ANDROIDX = 0;
    public static final int TEMPLATE_ANDROIDX = 1;
    public static final int TEMPLATE_ANDROIDX_TOOLBAR_FAB = 2;
    public static final int TEMPLATE_ANDROIDX_DRAWER_TOOLBAR_FAB = 3;

    public static void setDataForFirstTimeProjectCreation(String projectID, boolean enableViewBinding, boolean minsdk24) {
        setDataForFirstTimeProjectCreation(projectID, enableViewBinding, minsdk24, TEMPLATE_ANDROIDX);
    }

    public static void setDataForFirstTimeProjectCreation(String projectID, boolean enableViewBinding, boolean minsdk24, int template) {
        DRProjectTracker.startNow(projectID);
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                ProjectBuildConfigs.setDataForFirstTimeProjectCreation(projectID);
                ProjectConfigs.setDataForFirstTimeProjectCreation(projectID, enableViewBinding, minsdk24);
                applyProjectTemplate(projectID, template);
                Log.i(TAG, "setDataForFirstTimeProjectCreation: Done (template=" + template + ")");
            } catch (InterruptedException e) {
                Log.e(TAG, "setDataForFirstTimeProjectCreation: ", e);
            }
        }).start();
    }

    private static void applyProjectTemplate(String projectID, int template) {
        if (template == TEMPLATE_NON_ANDROIDX || template == TEMPLATE_ANDROIDX) {
            return;
        }
        try {
            int activityOptions = 0;
            if (template == TEMPLATE_ANDROIDX_TOOLBAR_FAB) {
                activityOptions = ProjectFileBean.OPTION_ACTIVITY_TOOLBAR | ProjectFileBean.OPTION_ACTIVITY_FAB;
            } else if (template == TEMPLATE_ANDROIDX_DRAWER_TOOLBAR_FAB) {
                activityOptions = ProjectFileBean.OPTION_ACTIVITY_TOOLBAR
                        | ProjectFileBean.OPTION_ACTIVITY_DRAWER
                        | ProjectFileBean.OPTION_ACTIVITY_FAB;
            }
            List<HashMap<String, Object>> activities = lC.e(projectID);
            if (activities != null && !activities.isEmpty()) {
                HashMap<String, Object> mainActivity = activities.get(0);
                mainActivity.put("options", activityOptions);
                lC.f(projectID, activities);
                Log.i(TAG, "applyProjectTemplate: set options=" + activityOptions);
            }
        } catch (Exception e) {
            Log.e(TAG, "applyProjectTemplate: ", e);
        }
    }

    public static Map<String, Object> readFirstLineDataWithDataTypeToMap(String dataType, String data) {
        Log.i(TAG, "readFirstLineDataWithDataTypeToMap: " + dataType + " " + data);
        return JsonUtils.covertoMap(readFirstLineDataWithDataType(dataType, data));
    }

    @Nullable
    public static String readFirstLineDataWithDataType(String dataType, String data) {
        Log.i(TAG, "readFirstLineDataWithDataType: " + dataType + " " + data);
        //Find the location of type
        int compatIndex = data.indexOf(dataType);
        if (compatIndex == -1) return null;

        //Find the nearest { after @compat
        int jsonStart = data.indexOf("{", compatIndex);
        int braceCount = 0;
        int jsonEnd = -1;

        //Find the correct } at the end of JSON
        for (int i = jsonStart; i < data.length(); i++) {
            char c = data.charAt(i);
            if (c == '{') braceCount++;
            else if (c == '}') braceCount--;

            if (braceCount == 0) {
                jsonEnd = i;
                break;
            }
        }

        if (jsonStart == -1 || jsonEnd == -1) return null;

        return data.substring(jsonStart, jsonEnd + 1);
    }

    public static Map<String, Object> readFullDataWithDataTypeToMap(String dataType, String data) {
        Log.i(TAG, "readFullDataWithDataTypeToMap: " + dataType + " " + data);
        return JsonUtils.covertoMap(readFullDataWithDataType(dataType, data));
    }

    @Nullable
    public static String readFullDataWithDataType(String dataType, String data) {
        Log.i(TAG, "readFullDataWithDataType: " + dataType + " " + data);
        //Find the starting position of the activity
        int startIndex = data.indexOf("@" + dataType);
        if (startIndex == -1) return null;

        //Find the end position (when encountering a new activity or running out of data)
        int nextActivityIndex = data.indexOf("@", startIndex + dataType.length());
        if (nextActivityIndex == -1) {
            nextActivityIndex = data.length(); //No new activity, get all
        }

        //Cut the entire data block of the activity
        return data.substring(startIndex, nextActivityIndex).trim();
    }

    public static String collectAllBlocks(String blockName, String data) {
        StringBuilder result = new StringBuilder();
        try {
            List<String> blocks = extractBlocks(blockName, data);
            for (String block : blocks) {
                result.append(block).append("\n");
            }
        } catch (Exception e) {
            Log.e(TAG, "readFullBlocksDataWithDataType: ", e);
        }
        return result.toString();
    }


    public static List<String> extractBlocks(String blockName, String data) {
        List<String> results = new ArrayList<>();
        if (data == null || data.isEmpty()) return results;

        String[] lines = data.split("\\r?\\n");
        StringBuilder currentBlock = new StringBuilder();
        boolean capturing = false;
        String headerPrefix = "@" + blockName; //Example: "@main".

        for (String line : lines) {
            String trimmed = line.trim();

            //If a new header is encountered (a line starting with '@').
            if (trimmed.startsWith("@")) {
                //If you are capturing the target block, save the old block first.
                if (capturing && currentBlock.length() > 0) {
                    results.add(currentBlock.toString().trim());
                    currentBlock.setLength(0);
                }
                //Check if this header is a target.
                if (trimmed.startsWith(headerPrefix)) {
                    capturing = true;
                    currentBlock.append(line).append("\n"); //Save header.
                } else {
                    //Other header -> no capture, ignore header.
                    capturing = false;
                }
            } else {
                //If in target block then add line.
                if (capturing) {
                    currentBlock.append(line).append("\n");
                }
                //If not capturing then skip the line.
            }
        }

        //Save last block if capturing.
        if (capturing && currentBlock.length() > 0) {
            results.add(currentBlock.toString().trim());
        }

        return results;
    }


    public static String collectAllLines(String keyWord, String data) {
        StringBuilder result = new StringBuilder();
        try {
            List<String> cdiskBlocks = extractLine(keyWord, data);
            for (String block : cdiskBlocks) {
                result.append(block).append("\n");
            }

        } catch (Exception e) {
            Log.e(TAG, "readFullBlocksDataWithDataType: ", e);
        }
        return result.toString();
    }

    public static List<String> extractLine(String keyWord, String data) {
        List<String> results = new ArrayList<>();

        try {
            StringBuilder currentBlock = new StringBuilder();
            String[] lines = data.split("\\r?\\n");

            for (String line : lines) {
                //If the line contains the keyword.
                if (line.contains(keyWord)) {
                    //If there is an old block, save it.
                    if (currentBlock.length() > 0) {
                        results.add(currentBlock.toString().trim());
                        currentBlock.setLength(0);
                    }
                    currentBlock.append(line).append("\n");
                }
            }

            //Add block last
            if (currentBlock.length() > 0) {
                results.add(currentBlock.toString().trim());
            }

        } catch (Exception e) {
            Log.e("TAG", "extractBlocks: ", e);
        }

        return results;
    }
}