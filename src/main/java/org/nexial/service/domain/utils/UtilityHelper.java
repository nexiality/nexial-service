package org.nexial.service.domain.utils;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;

import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.FileUtil;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import static org.nexial.service.domain.utils.Constants.*;

public class UtilityHelper {

    public static JsonObject createJson(String jsonString) {
        Gson gson = new Gson();
        JsonObject json = gson.fromJson(jsonString, JsonObject.class);
        String name = json.get(NAME).getAsString();
        name = (name.indexOf('.') > 0) ? name.substring(name.indexOf('.') + 1) : name;
        String dateValue = name.substring(0, 4) + "/" + name.substring(4, 6) + "/" + name.substring(6, 8) + " " +
                           name.substring(9, 11) + ":" + name.substring(11, 13) + ":" + name.substring(13, 15);
        JsonObject planJson = new JsonObject();
        planJson.addProperty(NAME, name);
        planJson.add(START_TIME, json.get(START_TIME));
        planJson.add(END_TIME, json.get(END_TIME));
        planJson.add(FAIL_COUNT, json.get(FAIL_COUNT));
        planJson.add(PASS_COUNT, json.get(PASS_COUNT));
        planJson.add(TOTAL_STEPS, json.get(TOTAL_STEPS));
        planJson.add(REFERENCE_DATA, json.get(REFERENCE_DATA));
        JsonObject dateJson = new JsonObject();
        dateJson.add(PLAN, new Gson().toJsonTree(planJson));
        dateJson.add(SCRIPT_RESULTS, json.get(NESTED_EXECUTIONS));
        JsonObject finalJson = new JsonObject();
        finalJson.add(dateValue, new Gson().toJsonTree(dateJson));
        return finalJson;
    }

    public static void getProjectList(Set<String> subProjectSet, HashMap<String, Set<String>> projectMap, Matcher m) {
        String project = StringUtils.substringBefore(m.group(1), ".");
        String subProject = StringUtils.substringAfter(m.group(1), ".");
        subProjectSet.add(subProject);
        if (StringUtils.isNotEmpty(project) && !projectMap.containsKey(project)) {
            projectMap.put(project, subProjectSet);
        } else {
            Set<String> set = new HashSet<>(projectMap.get(project));
            set.addAll(subProjectSet);
            projectMap.put(project, set);
            subProjectSet.clear();
        }
    }

    public static void uploadFileOnLocalPath(JsonObject fromJson, String path) {
        File filePath = new File(path);
        try {
            FileUtil.createNewFile(filePath, fromJson.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

