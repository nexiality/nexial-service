package org.nexial.service.domain.dashboard.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.excel.ExcelArea;
import org.nexial.core.utils.JsonUtils;
import org.nexial.service.domain.ApplicationProperties;
import org.nexial.service.domain.dashboard.IFileStorage;
import org.nexial.service.domain.dashboard.scheduler.Activity;
import org.nexial.service.domain.dashboard.scheduler.Activity.StepData;
import org.nexial.service.domain.dbconfig.ApplicationDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.nexial.core.CommandConst.CMD_REPEAT_UNTIL;
import static org.nexial.core.NexialConst.DEF_CHARSET;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.excel.ExcelConfig.*;
import static org.nexial.service.domain.utils.Constants.*;
import static org.nexial.service.domain.utils.Constants.Status.COMPLETED;
import static org.nexial.service.domain.utils.Constants.Status.FAILED;

@Service
public class ProcessRecordService {
    private final ApplicationDao dao;
    private final ApplicationProperties properties;
    private final BeanFactory factory;
    private static final Logger logger = LoggerFactory.getLogger(ProcessRecordService.class);
    private String project;
    private String prefix;

    protected ProcessRecordService(ApplicationDao dao, ApplicationProperties properties, BeanFactory factory) {
        this.dao = dao;
        this.properties = properties;
        this.factory = factory;

    }

    public void setProject(String project) { this.project = project; }

    public void setPrefix(String prefix) { this.prefix = prefix; }

    public int getWorkerCount() { return dao.getWorkerCount(project, prefix); }

    public List<Map<String, Object>> getReceivedProjects() { return dao.getReceivedProjects(); }

    public void interruptThread(String projectName, String prefix) {
        String workerId = dao.getWorkerId(projectName, prefix);

        Thread.getAllStackTraces().keySet().stream()
              .filter(t -> t.getName().equals(workerId)).forEach(Thread::interrupt);
    }

    @Async("threadPoolTaskExecutor")
    public CompletableFuture<Boolean> generateSummary() {
        dao.setProject(project);
        dao.setPrefix(prefix);
        try {
            dao.updateWorkerInfo(Thread.currentThread().getName());
            while (true) {
                List<Map<String, Object>> runIdList = dao.getRunIds();
                if (runIdList.isEmpty()) { break; }
                processExecutionDetailInfo(runIdList);
            }

            ProcessSummaryOutput();
            dao.updateScheduleInfoStatus(COMPLETED);
        } catch (Exception e) {
            logger.error("The generating summary process for project='" + project +
                         " and prefix='" + prefix + "' has been timed out");
            e.printStackTrace();
            dao.updateScheduleInfoStatus(FAILED);
        } finally {
            dao.deleteWorkerInfo();
        }
        return CompletableFuture.completedFuture(true);
    }

    private void processExecutionDetailInfo(List<Map<String, Object>> runIdList) {
        // To make sure all records  status should be changed before another schedule call
        for (Map<String, Object> row : runIdList) {
            if (Thread.interrupted()) {
                logger.info("*****Interrupted Thread******" + Thread.currentThread().getName());
                return;
            }
            String runId = (String) row.get("RunId");
            String outputPath = dao.getExecutionOutputPath(runId);
            dao.updateScheduleInfoStatusInProgress(runId);
            getExecutionDetailData(outputPath);
        }
    }

    private void getExecutionDetailData(String outputPath) {
        outputPath = StringUtils.replace(outputPath, "\\", "/");
        String content = null;
        //Todo change path store full path
        try {
            content = FileUtils.readFileToString(new File(outputPath), DEF_CHARSET);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (content != null) {
            processJsonData(content);
        }
        factory.getBean(properties.getStorageLocation(), IFileStorage.class).deleteFolders(outputPath);
    }

    private void processJsonData(String content) {
        logger.info("--------" + project + "----------" + Thread.currentThread().getName());
        JSONObject execution = JsonUtils.toJSONObject(content);
        String executionId = dao.insertExecutionInfo(execution);

        // todo consider asynchronous plan
        // adding script details
        String planId = EMPTY;
        String planName = EMPTY;
        int sequence = 1;
        int planSequence = 0;
        JSONArray scripts = execution.getJSONArray("nestedExecutions");
        for (int exec = 0; exec < scripts.length(); exec++) {
            JSONObject scriptObject = scripts.getJSONObject(exec);
            // to need to rewrite the logic
            if (scriptObject.has("planName")) {
                String planName1 = scriptObject.getString("planName");
                sequence = scriptObject.getInt("planSequence");
                if (!StringUtils.equals(planName, planName1)) {
                    planName = planName1;
                    planSequence++;
                    planId = dao.insertPlanInfo(executionId, scriptObject, planSequence, planName1);
                }
            }
            String scriptId = dao.insertScriptInfo(executionId, planId, sequence, scriptObject);

            // add iteration data
            JSONArray iterations = scriptObject.getJSONArray("nestedExecutions");
            for (int iter = 0; iter < iterations.length(); iter++) {
                JSONObject iterationObject = iterations.getJSONObject(iter);
                String testScriptLink = iterationObject.getString("testScriptLink");
                String iterationId = dao.insertIterationInfo(scriptId, iterationObject, testScriptLink);

                String path = getLocalPath(testScriptLink, execution.getString("name"));
                boolean isWindows = StringUtils.contains(execution.getString("runHostOs"), "Windows");
                Map<String, Map<Activity, List<StepData>>> iterationData = parseExcel(iterationId, path, isWindows);

                // add scenario details
                JSONArray scenarios = iterationObject.getJSONArray("nestedExecutions");
                for (int scenario = 0; scenario < scenarios.length(); scenario++) {
                    JSONObject scenarioObject = scenarios.getJSONObject(scenario);
                    String scenarioName = scenarioObject.getString("name");
                    String scenarioId = dao.insertScenarioInfo(scenarioName, scenario + 1,
                                                               iterationId, scenarioObject);

                    // add activity details
                    JSONArray activities = scenarioObject.getJSONArray("nestedExecutions");
                    for (int act = 0; act < activities.length(); act++) {
                        JSONObject activityObject = activities.getJSONObject(act);
                        String activityName = activityObject.getString("name");

                        String activityId = dao.insertActivityInfo(scenarioId, act + 1,
                                                                   activityName, activityObject);

                        if (!iterationData.containsKey(scenarioName)) { return; }

                        Map<Activity, List<StepData>> scenarioData = iterationData.get(scenarioName);
                        insertStepDetails(activityId, scenarioData, activityName, act + 1);
                    }
                }
            }
        }
    }

    private void ProcessSummaryOutput() {
        JsonObject projectOutputJsonObject = new JsonObject();
        JsonArray executionJsonArray = new JsonArray();
        List<Map<String, Object>> executionList = dao.getExecutionSummary();
        executionList.sort(Comparator.comparing(row -> StringUtils.substringAfter(row.get("name").toString(), ".")));
        executionList = executionList.subList(0, Math.min(executionList.size(), 90));
        logger.info("/----------------------------------------------------------------\\");
        logger.info(project + " Size is " + executionList.size());
        logger.info("\\----------------------------------------------------------------/");

        for (Map<String, Object> exeObjectMap : executionList) {
            JsonObject execution = new JsonObject();
            execution.addProperty("name", (String) exeObjectMap.get("Name"));
            mapExecutionData(exeObjectMap, execution, "EXECUTION");
            JsonArray nestedScripts = new JsonArray();
            Object executionId = exeObjectMap.get("Id");
            List<Map<String, Object>> executionScriptList = dao.getScriptSummaryList(executionId);
            for (Map<String, Object> exeScriptObjectMap : executionScriptList) {
                JsonObject script = new JsonObject();
                script.addProperty("name", (String) exeScriptObjectMap.get("Name"));
                script.addProperty("scriptFile", (String) exeScriptObjectMap.get("ScriptURL"));
                mapExecutionData(exeScriptObjectMap, script, "SCRIPT");
                script.addProperty("executionLog", (String) exeObjectMap.get("LogFile"));
                JsonArray nestedIteration = new JsonArray();
                List<Map<String, Object>> executionIterationList = dao.getIterationList(exeScriptObjectMap.get("Id"));
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
            List<Map<String, Object>> executionMetaData = dao.getExecutionMetas(executionId);
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
        String path = summaryPath + CLOUD_AWS_SEPARATOR + project +
                      prefix + CLOUD_AWS_SEPARATOR + "summary_output.json";
        try {
            FileUtil.createNewFile(new File(path), projectOutputJsonObject.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
        factory.getBean(properties.getStorageLocation(), IFileStorage.class)
               .uploadSummary(new File(path), project + prefix);
        logger.info("+++" + project + "upladed on server");
    }

    private String formatToDate(String name) {
        List<String> groups = RegexUtils.collectGroups(name, TIMESTAMP_REGEX);
        return groups.get(0) + "/" + groups.get(1) + "/" + groups.get(2) + " " +
               groups.get(3) + ":" + groups.get(4) + ":" + groups.get(5);
    }

    private void mapExecutionData(Map<String, Object> exeObjectMap, JsonObject execution, String scopeType) {
        List<Map<String, Object>> executionDataList = dao.getExecutionData(exeObjectMap.get("Id"), scopeType);
        for (Map<String, Object> exeDataObjectMap : executionDataList) {
            execution.addProperty("startTime", (Long) exeDataObjectMap.get("StartTime"));
            execution.addProperty("endTime", (Long) exeDataObjectMap.get("EndTime"));
            execution.addProperty("totalSteps", (Integer) exeDataObjectMap.get("TotalSteps"));
            execution.addProperty("passCount", (Integer) exeDataObjectMap.get("PassCount"));
            execution.addProperty("failCount", (Integer) exeDataObjectMap.get("FailCount"));
        }
    }

    private String getLocalPath(String testScriptLink, String runId) {
        String link = StringUtils.replace(testScriptLink, "\\", "/");
        String path = properties.getLocalArtifactsPath();
        path = path + CLOUD_AWS_SEPARATOR + project + CLOUD_AWS_SEPARATOR + runId +
               CLOUD_AWS_SEPARATOR + StringUtils.substringAfterLast(link, CLOUD_AWS_SEPARATOR);
        return path;
    }

    private Map<String, Map<Activity, List<StepData>>> parseExcel(String iterationId, String path, boolean isWindows) {
        StopWatch watch = new StopWatch();
        watch.start();
        File f = new File(path);
        Excel excel;
        Map<String, Map<Activity, List<StepData>>> scenarios = new HashMap<>();
        try {
            excel = new Excel(f, false, false);
            excel.getWorksheetsStartWith("").forEach(worksheet -> {
                if (StringUtils.equalsAny(worksheet.getName(), SUMMARY_TAB_NAME, SHEET_SYSTEM)) { return; }
                // insert iteration test data
                if (StringUtils.equals(worksheet.getName(), SHEET_MERGED_DATA)) {
                    dao.insertIterationData(iterationId, worksheet);
                    return;
                }

                ExcelAddress address = new ExcelAddress(FIRST_STEP_ROW + ":" + COL_REASON +
                                                        worksheet.getSheet().getLastRowNum());
                ExcelArea area = new ExcelArea(worksheet, address, false);
                String testCase = null;
                int activitySeq = 0;
                Map<Activity, List<StepData>> activities = new HashMap<>();
                List<StepData> steps = new ArrayList<>();
                List<List<XSSFCell>> wholeArea = area.getWholeArea();

                int size = wholeArea.size();
                for (int rowIndex = 0; rowIndex < size; rowIndex++) {
                    List<XSSFCell> row = wholeArea.get(rowIndex);
                    if (isEmptyRow(row)) { break; }

                    String activity = Excel.getCellValue(row.get(COL_IDX_TESTCASE));
                    if (StringUtils.isNotBlank(activity)) {
                        if (testCase != null) { activities.put(new Activity(testCase, activitySeq), steps); }
                        testCase = activity;
                        activitySeq++;
                        steps = new ArrayList<>();
                    }
                    List<List<Object>> stepLinks = new ArrayList<>();
                    List<List<Object>> logs = new ArrayList<>();
                    List<Object> step = addStepDetails(row, isWindows);

                    // add stepLinks and logsInfo if it is there
                    int index = 0;
                    boolean isRepeatUntil = StringUtils.equals(step.get(1) + "." + step.get(2), CMD_REPEAT_UNTIL);

                    // Might need to change logic
                    while (!isRepeatUntil) {
                        int rowIdx = rowIndex + index + 1;
                        if (rowIdx >= size) { break; }
                        if (addStepMeta(wholeArea.get(rowIdx), stepLinks, logs, isWindows)) { break; }
                        index++;
                    }
                    StepData stepData = new StepData(step, stepLinks, logs);
                    rowIndex += index;
                    steps.add(stepData);

                    // to avoid activity name problem in execution-detail.json if repeat-until steps has activity
                    if (isRepeatUntil) {
                        int totalSteps = Integer.parseInt(Excel.getCellValue(row.get(COL_IDX_PARAMS_START)));
                        for (index = 0; index < totalSteps; index++) {
                            List<XSSFCell> row1 = wholeArea.get(rowIndex + index + 1);
                            steps.add(new StepData(addStepDetails(row1, isWindows),
                                                   new ArrayList<>(), new ArrayList<>()));
                            //  todo check for screenshots and logs if any
                        }
                        rowIndex += totalSteps;
                    }
                }
                activities.put(new Activity(testCase, activitySeq), steps);
                scenarios.put(worksheet.getName(), activities);
            });
            excel.close();
            watch.stop();
            logger.info("/----------------------------------------------------------------\\");
            logger.info("Time taken to complete parse excel is " + watch.getTime());
            logger.info("\\----------------------------------------------------------------/");

        } catch (Exception e) {
            logger.error(e.getMessage());
        } finally {
            return scenarios;
        }
    }

    private List<Object> addStepDetails(List<XSSFCell> row, boolean isWindows) {
        List<Object> list = new ArrayList<>();
        list.add(Excel.getCellValue(row.get(COL_IDX_DESCRIPTION)));
        list.add(Excel.getCellValue(row.get(COL_IDX_TARGET)));
        list.add(Excel.getCellValue(row.get(COL_IDX_COMMAND)));
        List<Object> params = new ArrayList<>();
        List<Object> paramsOutput = new ArrayList<>();
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
            params.add(getLink(originalValue, isWindows));
            paramsOutput.add(getLink(cellValue, isWindows));
        }
        list.addAll(params);
        list.addAll(paramsOutput);
        list.add(Excel.getCellValue(row.get(COL_IDX_FLOW_CONTROLS)));
        list.add(Excel.getCellValue(row.get(COL_IDX_RESULT)));
        list.add(Excel.getCellValue(row.get(COL_IDX_REASON)));
        list.add(row.get(0).getRowIndex() + 1);
        list.add(Excel.getCellValue(row.get(COL_IDX_ELAPSED_MS)));
        return list;
    }

    private boolean addStepMeta(List<XSSFCell> nextRow, List<List<Object>> stepLinks,
                                List<List<Object>> logs, boolean isWindows) {
        if (!isContainLogs(nextRow)) { return true; }

        // check if screenshot column has a link
        if (StringUtils.isNotBlank(Excel.getCellValue(nextRow.get(COL_IDX_CAPTURE_SCREEN)))) {
            stepLinks.add(addStepLogDetails(nextRow, true, isWindows));
        } else {
            logs.add(addStepLogDetails(nextRow, false, isWindows));
        }
        return false;
    }

    private List<Object> addStepLogDetails(List<XSSFCell> row, boolean isScreenshot, boolean isWindows) {
        XSSFCell cell = row.get(COL_IDX_CAPTURE_SCREEN);
        String screenCapture = Excel.getCellValue(cell);
        String linkDescription = Excel.getCellValue(row.get(COL_IDX_PARAMS_START));
        List<Object> list = new ArrayList<>();
        if (!isScreenshot) {
            list.add(linkDescription);
        } else {
            if (StringUtils.startsWith(screenCapture, "HYPERLINK")) {
                list.add(getLinkLabel(screenCapture));
                list.add(linkDescription);
                list.add(getLink(screenCapture, isWindows));
            }
        }
        return list;
    }

    private void insertStepDetails(String activityId, Map<Activity, List<StepData>> scenarioData,
                                   String activity, int activitySeq) {
        Activity activity1 = new Activity(StringUtils.replace(activity, "\\n", "\n"), activitySeq);
        List<StepData> steps = scenarioData.get(activity1);

        if (steps == null) {
            logger.info("Steps are null  for activity" + activity + " and " + activitySeq);
            return;
        }
        dao.insertStepInfo(steps, activityId);
    }

    private String getLink(String text, boolean isWindows) {
        if (StringUtils.isBlank(text)) { return text; }

        String prefix = "HYPERLINK(IF(ISERROR(FIND(\"dos\",INFO(\"system\"))),";
        if (StringUtils.startsWith(text, prefix)) {
            String[] urls = StringUtils.substringsBetween(text, "\"", "\"");
            String link = !isWindows ? urls[2] : urls[3].replace("\\", "/");
            return link;
        } else if (StringUtils.containsAny(text, "http://", "https://")) {
            return StringUtils.substringBetween(text, "\"", "\"");
        }
        return text;
    }

    private String getLinkLabel(String text) {
        if (StringUtils.isBlank(text)) { return text; }
        String[] strings = StringUtils.substringsBetween(text, "\"", "\"");
        return strings[strings.length - 1];
    }

    private static boolean isContainLogs(List<XSSFCell> row) {
        return StringUtils.isAllBlank(Excel.getCellValue(row.get(COL_IDX_TARGET)),
                                      Excel.getCellValue(row.get(COL_IDX_COMMAND))) &&
               StringUtils.isNotBlank(Excel.getCellValue(row.get(COL_IDX_PARAMS_START)));
    }

    private static boolean isEmptyRow(List<XSSFCell> row) {
        return StringUtils.isAllBlank(Excel.getCellValue(row.get(COL_IDX_TARGET)),
                                      Excel.getCellValue(row.get(COL_IDX_COMMAND)),
                                      Excel.getCellValue(row.get(COL_IDX_PARAMS_START)),
                                      Excel.getCellValue(row.get(COL_IDX_CAPTURE_SCREEN)));
    }
}
