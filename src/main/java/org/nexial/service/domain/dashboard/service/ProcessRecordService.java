package org.nexial.service.domain.dashboard.service;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nexial.commons.utils.DateUtility;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.excel.ExcelArea;
import org.nexial.core.utils.JSONPath;
import org.nexial.core.utils.JsonUtils;
import org.nexial.service.domain.ApplicationProperties;
import org.nexial.service.domain.dashboard.IFileStorage;
import org.nexial.service.domain.dbconfig.SQLiteManager;
import org.nexial.service.domain.utils.LoggerUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.nexial.core.NexialConst.DEF_CHARSET;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.Project.NEXIAL_EXECUTION_TYPE_PLAN;
import static org.nexial.core.NexialConst.Project.NEXIAL_EXECUTION_TYPE_SCRIPT;
import static org.nexial.core.excel.ExcelConfig.*;
import static org.nexial.service.domain.utils.Constants.*;
import static org.nexial.service.domain.utils.Constants.Status.*;

@Service
public class ProcessRecordService {
    private final SQLiteManager sqLiteManager;
    private final ApplicationProperties properties;
    private final BeanFactory factory;

    protected ProcessRecordService(SQLiteManager sqLiteManager,
                                   ApplicationProperties properties, BeanFactory factory) {
        this.sqLiteManager = sqLiteManager;
        this.properties = properties;
        this.factory = factory;

    }

    @Async("threadPoolTaskExecutor")
    public CompletableFuture<Boolean> generateSummary(String projectName, String prefix) {
        try {
            sqLiteManager.updateData("SQL_INSERT_WORKERINFO",
                                     new Object[]{sqLiteManager.get(), projectName, prefix,
                                                  Thread.currentThread().getName()});
            List<Map<String, Object>> runIdList = sqLiteManager.selectForList("SQL_SELECT_RUNID_SCHEDULEINFO",
                                                                              new Object[]{projectName,
                                                                                           prefix,
                                                                                           RECEIVED});

            processExecutionDetailInfo(runIdList, projectName);
            List<Map<String, Object>> newRunIdList = sqLiteManager.selectForList("SQL_SELECT_RUNID_SCHEDULEINFO",
                                                                                 new Object[]{projectName,
                                                                                              prefix,
                                                                                              RECEIVED});
            if (!newRunIdList.isEmpty()) { processExecutionDetailInfo(newRunIdList, projectName); }
            if (!Thread.interrupted()) { ProcessSummaryOutput(projectName, prefix); }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return CompletableFuture.completedFuture(true);
    }

    private void ProcessSummaryOutput(String projectName, String prefix) {
            /*StopWatch watch = new StopWatch();
            watch.start();*/
        JsonObject projectOutputJsonObject = new JsonObject();
        JsonArray executionJsonArray = new JsonArray();
        List<Map<String, Object>> executionList = sqLiteManager.selectForList("SQL_SELECT_EXECUTION",
                                                                              new Object[]{projectName,
                                                                                           prefix});
        executionList.sort(Comparator.comparing(row -> StringUtils.substringAfter(row.get("name").toString(), ".")));
        executionList = executionList.subList(0, Math.min(executionList.size(), 90));
        LoggerUtils.info("/----------------------------------------------------------------\\");
        LoggerUtils.info(projectName + " Size is " + executionList.size());
        LoggerUtils.info("\\----------------------------------------------------------------/");

        for (Map<String, Object> exeObjectMap : executionList) {
            JsonObject execution = new JsonObject();
            execution.addProperty("name", (String) exeObjectMap.get("Name"));
            mapExecutionData(exeObjectMap, execution, "EXECUTION");
            JsonArray nestedScripts = new JsonArray();
            List<Map<String, Object>> executionScriptList = sqLiteManager.selectForList("SQL_SELECT_SCRIPT",
                                                                                        new Object[]{exeObjectMap.get(
                                                                                            "Id")});
            for (Map<String, Object> exeScriptObjectMap : executionScriptList) {
                JsonObject script = new JsonObject();
                script.addProperty("name", (String) exeScriptObjectMap.get("Name"));
                script.addProperty("scriptFile", (String) exeScriptObjectMap.get("ScriptURL"));
                mapExecutionData(exeScriptObjectMap, script, "SCRIPT");
                script.addProperty("executionLog", (String) exeObjectMap.get("LogFile"));
                JsonArray nestedIteration = new JsonArray();
                List<Map<String, Object>> executionIterationList = sqLiteManager.selectForList("SQL_SELECT_ITERATION",
                                                                                               new Object[]{
                                                                                                   exeScriptObjectMap.get(
                                                                                                       "Id")});
                for (Map<String, Object> exeIterationObjectMap : executionIterationList) {
                    JsonObject iteration = new JsonObject();
                    iteration.addProperty("name", (String) exeIterationObjectMap.get("Name"));
                    iteration.addProperty("sourceScript", (String) exeObjectMap.get("ScriptURL"));
                    iteration.addProperty("testScriptLink", (String) exeIterationObjectMap.get("TestScriptUrl"));
                    mapExecutionData(exeIterationObjectMap, iteration, "ITERATION");
                    iteration.addProperty("executionLog", (String) exeObjectMap.get("LogFile"));
                    nestedIteration.add(iteration);
                }
                script.add("nestedExecutions", nestedIteration);
                nestedScripts.add(script);
            }
            List<Map<String, Object>> executionMetaData = sqLiteManager.selectForList("SQL_SELECT_EXECUTIONMETA",
                                                                                      new Object[]{exeObjectMap.get("Id"),
                                                                                                   "EXECUTION"});
            JsonObject referenceData = new JsonObject();
            for (Map<String, Object> exeMetaDataObjectMap : executionMetaData) {
                referenceData.addProperty((String) exeMetaDataObjectMap.get("Key"),
                                          (String) exeMetaDataObjectMap.get("Value"));
            }
            execution.add("referenceData", referenceData);
            JsonObject execution1 = new JsonObject();
            execution1.add("plan", execution);
            execution1.add("scriptResults", nestedScripts);
            JsonObject finalExecution = new JsonObject();
            finalExecution.add(formatToDate((String) exeObjectMap.get("Name")), execution1);
            executionJsonArray.add(finalExecution);
        }
        projectOutputJsonObject.add(RESULTS, executionJsonArray);
        if (!StringUtils.isEmpty(prefix)) {
            prefix = "." + prefix;
        }
        String summaryPath = properties.getLocalExecutionSummaryPath();
        String path = summaryPath + CLOUD_AWS_SEPARATOR + projectName +
                      prefix + CLOUD_AWS_SEPARATOR + "summary_output.json";
        try {
            FileUtil.createNewFile(new File(path), projectOutputJsonObject.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        factory.getBean(properties.getStorageLocation(), IFileStorage.class).uploadSummary(new File(path),
                                                                                           projectName + prefix);
        String dateNow = new SimpleDateFormat(DATE_TIME_FORMAT).format(new Date());
        LoggerUtils.info("+++" + projectName + "upladed on server");
        sqLiteManager.updateData("SQL_UPDATE_SCHEDULEINFO_STATUS_COMPLETED",
                                 new Object[]{COMPLETED, dateNow, projectName,
                                              prefix, INPROGRESS});

    }

    private String formatToDate(String name) {
        List<String> groups = RegexUtils.collectGroups(name, TIMESTAMP_REGEX);
        return groups.get(0) + "/" + groups.get(1) + "/" + groups.get(2) + " " +
               groups.get(3) + ":" + groups.get(4) + ":" + groups.get(5);
    }

    private void mapExecutionData(Map<String, Object> exeObjectMap, JsonObject execution, String scopeType) {
        List<Map<String, Object>> executionDataList = sqLiteManager.selectForList("SQL_SELECT_EXECUTIONDATA",
                                                                                  new Object[]{exeObjectMap.get("Id"),
                                                                                               scopeType});
        for (Map<String, Object> exeDataObjectMap : executionDataList) {
            execution.addProperty("startTime", (Long) exeDataObjectMap.get("StartTime"));
            execution.addProperty("endTime", (Long) exeDataObjectMap.get("EndTime"));
            execution.addProperty("totalSteps", (Integer) exeDataObjectMap.get("TotalSteps"));
            execution.addProperty("passCount", (Integer) exeDataObjectMap.get("PassCount"));
            execution.addProperty("failCount", (Integer) exeDataObjectMap.get("FailCount"));
        }
    }

    private void processExecutionDetailInfo(List<Map<String, Object>> runIdList, String projectName) {
        // To make sure all records  status should be changed before another schedule call
        for (Map<String, Object> row : runIdList) {
            if (Thread.interrupted()) {
                LoggerUtils.info("*****Interrupted Thread******" + Thread.currentThread().getName());
                return;
            }
            String runId = (String) row.get("RunId");
            String outputPath = (String) sqLiteManager.selectForObject("SQL_SELECT_OUTPUTPATH_SCHEDULEINFO",
                                                                       new Object[]{runId, RECEIVED},
                                                                       String.class);
            String processingDateNow = new SimpleDateFormat(DATE_TIME_FORMAT).format(new Date());
            Object[] processingParams = {INPROGRESS, processingDateNow, runId};
            sqLiteManager.updateData("SQL_UPDATE_SCHEDULEINFO_STATUS_STAGE", processingParams);
            getExecutionDetailData(outputPath, projectName);
        }
    }

    private void getExecutionDetailData(String outputPath, String projectName) {
        outputPath = StringUtils.replace(outputPath, "\\", "/");
        String content = null;
        //Todo change path store full path
        try {
            content = FileUtils.readFileToString(new File(outputPath), DEF_CHARSET);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (content != null) {
            processJsonData(content, projectName);
        }
        factory.getBean(properties.getStorageLocation(), IFileStorage.class).deleteFolders(outputPath);
    }

    private void processJsonData(String content, String projectName) {
        LoggerUtils.info("--------" + projectName + "----------" + Thread.currentThread().getName());
        JSONObject execution = JsonUtils.toJSONObject(content);
        String executionId = sqLiteManager.get();
        String name = execution.getString("name");
        String prefix = StringUtils.equals(StringUtils.substringBefore(name, "."), name) ?
                        EMPTY :
                        StringUtils.substringBefore(name, ".");
        //Todo execution log file location check need to create manually or get from  script
        // String logFile = JSONPath.find(jsonData,"nestedExecutions[0].executionLog");
        String logFile = name + File.separator + "logs" + File.separator + "nexial-" + name + ".log";
        String executionType = (JSONPath.find(execution, "nestedExecutions[0].planName") == null) ?
                               NEXIAL_EXECUTION_TYPE_SCRIPT : NEXIAL_EXECUTION_TYPE_PLAN;

        Object[] params =
            {executionId, name, EMPTY, logFile, EMPTY, executionType, prefix, projectName};
        sqLiteManager.updateData("SQL_INSERT_EXECUTION", params);

        Object[] executionEnvParams = {sqLiteManager.get(),
                                       execution.get("runHost"),
                                       execution.get("runUser"),
                                       execution.get("runHostOs"),
                                       EMPTY,
                                       EMPTY,
                                       EMPTY,
                                       EMPTY,
                                       executionId};
        sqLiteManager.updateData("SQL_INSERT_EXECUTION_ENVIRONMENT", executionEnvParams);

        insertExecutionData(executionId, execution, execution.getString("executionLevel"));
        insertExecutionMetaData(executionId, execution);

        // todo consider asynchronous plan
        // adding script details
        String planId = EMPTY;
        String planName = EMPTY;
        int sequence = 1;
        int planSequence = 0;
        JSONArray scripts = execution.getJSONArray("nestedExecutions");
        for (int exec = 0; exec < scripts.length(); exec++) {
            String scriptId = sqLiteManager.get();
            JSONObject scriptObject = scripts.getJSONObject(exec);
            // to need to rewrite the logic
            if (scriptObject.has("planName")) {
                String planName1 = scriptObject.getString("planName");
                sequence = scriptObject.getInt("planSequence");
                if (!StringUtils.equals(planName, planName1)) {
                    planName = planName1;
                    planSequence++;
                    planId = sqLiteManager.get();
                    Object[] planParams = {planId, executionId, planName1, planSequence,
                                           scriptObject.getString("planFile")};
                    sqLiteManager.updateData("SQL_INSERT_PLAN", planParams);
                }
            }

            Object[] scriptParams = {scriptId, scriptObject.getString("name"), sequence, planId,
                                     executionId, scriptObject.getString("scriptFile")};
            sqLiteManager.updateData("SQL_INSERT_SCRIPT", scriptParams);
            insertExecutionData(scriptId, execution, scriptObject.getString("executionLevel"));
            insertExecutionMetaData(scriptId, scriptObject);

            // add iteration data
            JSONArray iterations = scriptObject.getJSONArray("nestedExecutions");
            for (int iter = 0; iter < iterations.length(); iter++) {
                String iterationId = sqLiteManager.get();
                JSONObject iterationObject = iterations.getJSONObject(iter);
                String testScriptLink = iterationObject.getString("testScriptLink");

                Object[] iterationParams = {iterationId, iterationObject.getString("name"), scriptId,
                                            testScriptLink, iterationObject.getInt("iterationIndex")};
                sqLiteManager.updateData("SQL_INSERT_ITERATION", iterationParams);

                insertExecutionData(iterationId, execution, iterationObject.getString("executionLevel"));
                insertExecutionMetaData(iterationId, iterationObject);
                JSONObject iterationData = insertIterationData(iterationId, testScriptLink, projectName, name);

                // add scenario details
                JSONArray scenarios = iterationObject.getJSONArray("nestedExecutions");
                for (int scenario = 0; scenario < scenarios.length(); scenario++) {
                    String scenarioId = sqLiteManager.get();
                    JSONObject scenarioObject = scenarios.getJSONObject(scenario);
                    String scenarioName = scenarioObject.getString("name");

                    Object[] scenarioParams = {scenarioId, scenarioName, iterationId, scenario + 1, "scenarioURL"};
                    sqLiteManager.updateData("SQL_INSERT_SCENARIO", scenarioParams);

                    insertExecutionData(scenarioId, execution, scenarioObject.getString("executionLevel"));
                    insertExecutionMetaData(scenarioId, scenarioObject);

                    // add activity details
                    JSONArray activities = scenarioObject.getJSONArray("nestedExecutions");
                    for (int act = 0; act < activities.length(); act++) {
                        String activityId = sqLiteManager.get();
                        JSONObject activityObject = activities.getJSONObject(act);
                        String activityName = activityObject.getString("name");

                        Object[] activityParams = {activityId, activityName, scenarioId, act + 1};
                        sqLiteManager.updateData("SQL_INSERT_ACTIVITY", activityParams);

                        insertExecutionData(activityId, execution, activityObject.getString("executionLevel"));
                        insertExecutionMetaData(activityId, activityObject);
                        insertStepDetails(activityId, iterationData, scenarioName, activityName);
                    }
                }
            }
        }
    }

    private JSONObject insertIterationData(String iterationId,
                                           String testScriptLink,
                                           String projectName,
                                           String runId) {
        String path = getLocalPath(testScriptLink, projectName, runId);
        return parseExcel(iterationId, path);
    }

    private String getLocalPath(String testScriptLink, String projectName, String runId) {
        String link = StringUtils.replace(testScriptLink, "\\", "/");
        String path = properties.getLocalArtifactsPath();
        path = path + CLOUD_AWS_SEPARATOR + projectName + CLOUD_AWS_SEPARATOR + runId +
               CLOUD_AWS_SEPARATOR + StringUtils.substringAfterLast(link, CLOUD_AWS_SEPARATOR);
        return path;
    }

    private JSONObject parseExcel(String iterationId, String path) {
            /*StopWatch watch = new StopWatch();
            watch.start();*/
        File f = new File(path);
        Excel excel;
        JSONObject scenarios = new JSONObject();
        try {
            excel = new Excel(f, false, false);
            excel.getWorksheetsStartWith("").forEach(worksheet -> {
                if (StringUtils.equalsAny(worksheet.getName(), SUMMARY_TAB_NAME, SHEET_SYSTEM)) { return; }
                // insert iteration test data
                if (StringUtils.equals(worksheet.getName(), SHEET_MERGED_DATA)) {
                    insertTestData(iterationId, worksheet);
                    return;
                }

                XSSFSheet sheet = worksheet.getSheet();
                int lastCommandRow = sheet.getLastRowNum();
                ExcelArea area = new ExcelArea(worksheet,
                                               new ExcelAddress(FIRST_STEP_ROW + ":" + COL_REASON + lastCommandRow),
                                               false);
                String testCase = null;
                JSONObject activities = new JSONObject();
                JSONArray steps = new JSONArray();
                for (int i = 0; i < area.getWholeArea().size(); i++) {
                    List<XSSFCell> row = area.getWholeArea().get(i);
                    if (isEmptyRow(row)) { break; }

                    String activity = Excel.getCellValue(row.get(COL_IDX_TESTCASE));
                    if (StringUtils.isNotBlank(activity)) {
                        if (testCase != null) { activities.put(testCase, steps); }
                        testCase = activity;
                        steps = new JSONArray();
                    }
                    JSONObject step = addStepDetails(row);

                    // add stepLinks and logsInfo if it is there
                    int j = 0;
                    while (true) {
                        XSSFRow nextRow = sheet.getRow(row.get(1).getRowIndex() + j + 1);
                        if (!isContainLogs(nextRow)) { break; }
                        step = addStepLogDetails(step, nextRow);
                        j++;
                    }
                    i += j;
                    steps.put(step);
                }
                activities.put(testCase, steps);
                scenarios.put(worksheet.getName(), activities);
            });
            excel.close();
        } catch (IOException e) {
            e.getMessage();
        } finally {
            return scenarios;
        }
    }

    private static JSONObject addStepDetails(List<XSSFCell> row) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("rowNo", row.get(0).getRowIndex() + 1);
        jsonObject.put("description", Excel.getCellValue(row.get(COL_IDX_DESCRIPTION)));
        jsonObject.put("target", Excel.getCellValue(row.get(COL_IDX_TARGET)));
        jsonObject.put("command", Excel.getCellValue(row.get(COL_IDX_COMMAND)));
        jsonObject.put("flowControl", Excel.getCellValue(row.get(COL_IDX_FLOW_CONTROLS)));
        jsonObject.put("result", Excel.getCellValue(row.get(COL_IDX_RESULT)));
        jsonObject.put("reason", Excel.getCellValue(row.get(COL_IDX_REASON)));
        jsonObject.put("elapsedTime", Excel.getCellValue(row.get(COL_IDX_ELAPSED_MS)));
        for (int i = COL_IDX_PARAMS_START; i <= COL_IDX_PARAMS_END; i++) {
            XSSFCell cell = row.get(i);
            String cellValue = Excel.getCellValue(cell);
            cellValue = cellValue != null ? cellValue : "";
            XSSFComment cellComment = cell != null ? cell.getCellComment() : null;
            String originalValue = cellValue;
            if (cellComment != null) {
                // added both line separator to get original text
                originalValue = StringUtils.removeStart(cellComment.getString().toString(), "test script:\r\n");
                originalValue = StringUtils.removeStart(originalValue, "test script:\n");
            }
            jsonObject.put("paramOutput" + (i - COL_IDX_PARAMS_START + 1), getLink(cellValue));
            jsonObject.put("param" + (i - COL_IDX_PARAMS_START + 1), getLink(originalValue));
        }
        return jsonObject;
    }

    private static JSONObject addStepLogDetails(JSONObject steps, XSSFRow row) {
        XSSFCell cell = row.getCell(COL_IDX_CAPTURE_SCREEN);
        String screenCapture = Excel.getCellValue(cell);
        String linkDescription = Excel.getCellValue(row.getCell(COL_IDX_PARAMS_START));
        if (StringUtils.isBlank(screenCapture)) {
            JSONObject jsonObject = new JSONObject();
            JSONArray logs = steps.has("logs") ? steps.getJSONArray("logs") : new JSONArray();
            jsonObject.put("logInfo", linkDescription);
            logs.put(jsonObject);
            steps.put("logs", logs);
        } else {
            JSONArray stepLinks = steps.has("stepLinks") ? steps.getJSONArray("stepLinks") : new JSONArray();
            JSONObject jsonObject = new JSONObject();
            if (StringUtils.startsWith(screenCapture, "HYPERLINK")) {
                jsonObject.put("linkLabel", getLinkLabel(screenCapture));
                jsonObject.put("linkDescription", linkDescription);
                jsonObject.put("linkUrl", getLink(screenCapture));
                stepLinks.put(jsonObject);
                steps.put("stepLinks", stepLinks);
            }
        }
        return steps;
    }

    private static String getLink(String text) {
        if (StringUtils.isBlank(text)) { return text; }
        String prefix = "HYPERLINK(IF(ISERROR(FIND(\"dos\",INFO(\"system\"))),";
        if (StringUtils.startsWith(text, prefix)) {
            String s = StringUtils.substringAfter(text, prefix);
            String urls = StringUtils.substringBefore(s, ")");
            return StringUtils.substringAfterLast(StringUtils.substringBeforeLast(urls, "\""), "\"");
        } else if (StringUtils.containsAny(text, "http://", "https://")) {
            return StringUtils.substringBefore(StringUtils.substringAfter(text, "\""), "\"");
        }
        return text;
    }

    private static String getLinkLabel(String text) {
        if (StringUtils.isBlank(text)) { return text; }
        return StringUtils.substringAfterLast(StringUtils.substringBeforeLast(text, "\""), "\"");
    }

    /*private static boolean isContainLogs(List<XSSFCell> row) {
        return StringUtils.isAllBlank(Excel.getCellValue(row.get(COL_IDX_TARGET)),
                                      Excel.getCellValue(row.get(COL_IDX_COMMAND))) &&
               StringUtils.isNotBlank(Excel.getCellValue(row.get(COL_IDX_PARAMS_START)));
    }*/
    private static boolean isContainLogs(XSSFRow row) {
        return StringUtils.isAllBlank(Excel.getCellValue(row.getCell(COL_IDX_TARGET)),
                                      Excel.getCellValue(row.getCell(COL_IDX_COMMAND))) &&
               StringUtils.isNotBlank(Excel.getCellValue(row.getCell(COL_IDX_PARAMS_START)));
    }

    private static boolean isEmptyRow(List<XSSFCell> row) {
        return StringUtils.isAllBlank(Excel.getCellValue(row.get(COL_IDX_TARGET)),
                                      Excel.getCellValue(row.get(COL_IDX_COMMAND)),
                                      Excel.getCellValue(row.get(COL_IDX_PARAMS_START)),
                                      Excel.getCellValue(row.get(COL_IDX_CAPTURE_SCREEN)));
    }

    private void insertExecutionData(String scopeId, JSONObject jsonData, String scopeLevel) {
        Object[] executionDataParams = {sqLiteManager.get(), jsonData.getLong("startTime"),
                                        jsonData.getLong("endTime"), jsonData.getInt("totalSteps"),
                                        jsonData.getInt("passCount"), jsonData.getInt("failCount"),
                                        jsonData.getInt("warnCount"), jsonData.getInt("executed"),
                                        jsonData.getBoolean("failedFast"), scopeId, scopeLevel};
        sqLiteManager.updateData("SQL_INSERT_EXECUTION_DATA", executionDataParams);
    }

    private void insertExecutionMetaData(String scopeId, JSONObject scriptObject) {
        if (!scriptObject.has("referenceData")) { return; }
        String creationTime = DateUtility.format(scriptObject.getLong("startTime"));
        Map<String, Object> referenceMap = scriptObject.getJSONObject("referenceData").toMap();
        referenceMap.forEach((key, value) -> sqLiteManager.updateData("SQL_INSERT_EXECUTION_META_DATA",
                                                                      new Object[]{
                                                                          sqLiteManager.get(), key, value,
                                                                          creationTime, scopeId,
                                                                          scriptObject.getString("executionLevel")}));
    }

    private void insertTestData(String iterationId, Excel.Worksheet worksheet) {
        int lastDataRow = worksheet.findLastDataRow(ADDR_FIRST_DATA_COL);
        for (int index = 0; index < lastDataRow; index++) {
            XSSFRow row = worksheet.getSheet().getRow(index);
            String key = Excel.getCellValue(row.getCell(0));
            String value = Excel.getCellValue(row.getCell(1));
            Object[] param = {sqLiteManager.get(), key, value, iterationId};
            sqLiteManager.updateData("SQL_INSERT_ITERATION_DATA", param);
        }
    }

    private void insertStepDetails(String activityId, JSONObject jsonObject,
                                   String scenario, String activity) {
        JSONArray steps = jsonObject.getJSONObject(scenario.replace("\\n", "\n")).getJSONArray(activity.replace("\\n",
                                                                                                                "\n"));
        steps.forEach(step -> {
            if (Thread.interrupted()) {
                LoggerUtils.info("*****Interrupted Thread******" + Thread.currentThread().getName());
                return;
            }
            JSONObject stepObject = (JSONObject) step;
            String stepId = sqLiteManager.get();
            sqLiteManager.updateData("SQL_INSERT_STEPS",
                                     new Object[]{
                                         stepId, activityId, stepObject.getString("description"),
                                         stepObject.getString("target"), stepObject.getString("command"),
                                         stepObject.getString("param1"), stepObject.getString("param2"),
                                         stepObject.getString("param3"), stepObject.getString("param4"),
                                         stepObject.getString("param5"), stepObject.getString("paramOutput1"),
                                         stepObject.getString("paramOutput2"), stepObject.getString("paramOutput3"),
                                         stepObject.getString("paramOutput4"), stepObject.getString("paramOutput5"),
                                         stepObject.getString("flowControl"), stepObject.getString("result"),
                                         stepObject.getString("reason"), stepObject.getInt("rowNo"),
                                         stepObject.getString("elapsedTime")});

            if (stepObject.has("stepLinks")) { insertStepLinks(stepObject, stepId); }
            if (stepObject.has("logs")) { insertLogs(stepObject, stepId); }
        });
    }

    private void insertStepLinks(JSONObject stepObject, String stepId) {
        JSONArray stepLinks = stepObject.getJSONArray("stepLinks");
        stepLinks.forEach(stepLink1 -> {
            JSONObject stepLink = (JSONObject) stepLink1;
            sqLiteManager.updateData("SQL_INSERT_STEP_LINKS",
                                     new Object[]{
                                         sqLiteManager.get(), stepId, stepLink.getString("linkLabel"),
                                         stepLink.getString("linkDescription"), stepLink.getString("linkUrl")
                                     });
        });
    }

    private void insertLogs(JSONObject stepObject, String stepId) {
        JSONArray logs = stepObject.getJSONArray("logs");
        logs.forEach(log1 -> {
            JSONObject log = (JSONObject) log1;
            sqLiteManager.updateData("SQL_INSERT_LOGS",
                                     new Object[]{sqLiteManager.get(), stepId, log.getString("logInfo")});
        });
    }

}

