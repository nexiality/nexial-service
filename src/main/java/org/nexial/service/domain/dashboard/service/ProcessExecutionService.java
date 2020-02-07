package org.nexial.service.domain.dashboard.service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.json.JSONArray;
import org.json.JSONObject;
import org.nexial.commons.utils.FileUtil;
import org.nexial.commons.utils.RegexUtils;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.excel.ExcelAddress;
import org.nexial.core.excel.ExcelArea;
import org.nexial.core.utils.JsonUtils;
import org.nexial.service.domain.ApplicationProperties;
import org.nexial.service.domain.dashboard.IFileStorage;
import org.nexial.service.domain.dbconfig.ApplicationDao;
import org.nexial.service.domain.utils.UtilityHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.nexial.core.CommandConst.CMD_REPEAT_UNTIL;
import static org.nexial.core.NexialConst.Data.*;
import static org.nexial.core.NexialConst.GSON;
import static org.nexial.core.excel.ExcelConfig.*;
import static org.nexial.service.domain.utils.Constants.*;

@Service
@Scope("prototype")
public class ProcessExecutionService {
    private static final Logger logger = LoggerFactory.getLogger(ProcessExecutionService.class);

    private ApplicationDao dao;
    private final ApplicationProperties properties;
    private BeanFactory factory;
    private String project;
    private String prefix;
    private String projectName;
    private String dashboardName;

    public ProcessExecutionService(ApplicationDao dao, ApplicationProperties properties, BeanFactory factory) {
        this.dao = dao;
        this.properties = properties;
        this.factory = factory;
    }

    public void setProject(String project) {
        this.project = project;
        this.projectName = dao.getProjectName(project);
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
        this.dashboardName = dao.getDashboardName(this.project,prefix);
        if(this.dashboardName == null) this.dashboardName = prefix;
    }

    @Transactional
    public void processJsonData(String content) throws IOException {
        JSONObject executionObj = JsonUtils.toJSONObject(content);
        String executionId = dao.insertExecutionInfo(executionObj, project, prefix, projectName);

        // adding script details
        String planId = EMPTY;
        String planName = EMPTY;
        int sequence = 1;
        int planSequence = 0;
        JSONArray scripts = executionObj.getJSONArray("nestedExecutions");
        scripts = sortScripts(scripts);
        for (int scriptIndex = 0; scriptIndex < scripts.length(); scriptIndex++) {
            JSONObject scriptObj = scripts.getJSONObject(scriptIndex);

            // todo need to rewrite the logic
            if (scriptObj.has("planName")) {
                String planName1 = scriptObj.getString("planName");
                sequence = scriptObj.getInt("planSequence");
                if (!StringUtils.equals(planName, planName1)) {
                    planName = planName1;
                    planSequence++;
                    planId = dao.insertPlanInfo(projectName, executionId, scriptObj, planSequence, planName1);
                }
            }
            String scriptId = dao.insertScriptInfo(projectName, executionId, planId, sequence, scriptObj);

            // add iteration data
            JSONArray iterations = scriptObj.getJSONArray("nestedExecutions");
            for (int iterIndex = 0; iterIndex < iterations.length(); iterIndex++) {
                JSONObject iterationObj = iterations.getJSONObject(iterIndex);
                String testScriptLink = iterationObj.getString("testScriptLink");
                String iterationId = dao.insertIterationInfo(scriptId, iterationObj, testScriptLink);

                String path = getLocalPath(testScriptLink, executionObj.getString("name"));
                boolean isWindows = StringUtils.contains(executionObj.getString("runHostOs"), "Windows");
                Map<String, Map<String, List<StepData>>> iterationData = parseExcel(iterationId, path, isWindows);

                // add scenario details
                JSONArray scenarios = iterationObj.getJSONArray("nestedExecutions");
                for (int scenarioIndex = 0; scenarioIndex < scenarios.length(); scenarioIndex++) {
                    JSONObject scenarioObj = scenarios.getJSONObject(scenarioIndex);
                    String scenarioName = scenarioObj.getString("name");
                    String scenarioId = dao.insertScenarioInfo(iterationId, scenarioName,
                                                               scenarioIndex + 1, scenarioObj);

                    // add activity details
                    JSONArray activities = scenarioObj.getJSONArray("nestedExecutions");
                    for (int activityIndex = 0; activityIndex < activities.length(); activityIndex++) {
                        JSONObject activityObj = activities.getJSONObject(activityIndex);
                        String activityName = activityObj.getString("name");

                        String activityId = dao.insertActivityInfo(scenarioId, activityName,
                                                                   activityIndex + 1, activityObj);

                        if (!iterationData.containsKey(scenarioName)) { return; }
                        insertStepDetails(activityId, iterationData.get(scenarioName), activityName, activityIndex + 1);
                    }
                }
            }
        }
    }

    @Transactional
    public void createSummaryOutput() throws Exception {
        JSONArray executionJSONArray = new JSONArray();
        List<Map<String, Object>> executionList = dao.getExecutionSummary(project, prefix);
        executionList.sort(Comparator.comparing(row -> StringUtils.substringAfter(row.get("name").toString(), ".")));
        executionList = executionList.subList(0, Math.min(executionList.size(), 90));
        logger.info("/----------------------------------------------------------------\\");
        logger.info(projectName + " Size is " + executionList.size());
        logger.info("\\----------------------------------------------------------------/");

        for (Map<String, Object> exeObjectMap : executionList) {
            JSONObject plan = new JSONObject();
            mapExecutionData(exeObjectMap, plan, "EXECUTION");
            Object executionId = exeObjectMap.get("Id");
            List<Map<String, Object>> executionScriptList = dao.getScriptSummaryList(executionId);
            JSONArray nestedScripts = new JSONArray();

            for (Map<String, Object> exeScriptObjectMap : executionScriptList) {
                JSONObject script = new JSONObject();
                mapExecutionData(exeScriptObjectMap, script, "SCRIPT");
                script.put("scriptFile", exeScriptObjectMap.get("ScriptURL"));
                script.put("executionLog", exeObjectMap.get("ExecutionLogUrl"));
                List<Map<String, Object>> executionIterationList = dao.getIterationList(exeScriptObjectMap.get("Id"));
                JSONArray nestedIteration = new JSONArray();

                for (Map<String, Object> exeIterationObjectMap : executionIterationList) {
                    JSONObject iteration = new JSONObject();
                    iteration.put("sourceScript", exeScriptObjectMap.get("ScriptURL"));
                    iteration.put("testScriptLink", exeIterationObjectMap.get("TestScriptUrl"));
                    mapExecutionData(exeIterationObjectMap, iteration, "ITERATION");
                    iteration.put("executionLog", exeObjectMap.get("LogFile"));
                    nestedIteration.put(iteration);
                }
                script.put("nestedExecutions", nestedIteration);
                nestedScripts.put(script);
            }
            JSONObject execution = new JSONObject();
            execution.put("plan", plan);
            execution.put("scriptResults", nestedScripts);
            JSONObject finalExecution = new JSONObject();
            finalExecution.put(formatToDate((String) exeObjectMap.get("Name")), execution);
            executionJSONArray.put(finalExecution);
        }

        JSONObject summaryOutputJson = new JSONObject();
        summaryOutputJson.put(RESULTS, executionJSONArray);
        uploadSummaryOutput(summaryOutputJson.toString());
    }

    public void uploadSummaryOutput(String content) throws Exception {
        String folder = projectName + (!StringUtils.isEmpty(dashboardName) ? "." + dashboardName : "");

        String summaryPath = properties.getLocalExecutionSummaryPath();
        String path = summaryPath + PATH_SEPARATOR + folder + PATH_SEPARATOR + SUMMARY_OUTPUT_FILE;
        FileUtil.createNewFile(new File(path), content);
        factory.getBean(properties.getStorageLocation(), IFileStorage.class)
               .uploadSummary(new File(path), folder);
        logger.info("Execution-summary output for project " + projectName + "." + dashboardName + " is uploaded on server");
    }

    @Transactional
    protected void mapExecutionData(Map<String, Object> exeObjectMap, JSONObject execution, String scopeType) {
        execution.put("name", exeObjectMap.get("Name"));
        Object scopeId = exeObjectMap.get("Id");
        List<Map<String, Object>> executionDataList = dao.getExecutionData(scopeId, scopeType);
        for (Map<String, Object> exeDataObjectMap : executionDataList) {
            execution.put("startTime", exeDataObjectMap.get("StartTime"));
            execution.put("endTime", exeDataObjectMap.get("EndTime"));
            execution.put("totalSteps", exeDataObjectMap.get("TotalSteps"));
            execution.put("passCount", exeDataObjectMap.get("PassCount"));
            execution.put("failCount", exeDataObjectMap.get("FailCount"));
        }
        List<Map<String, Object>> executionMetaData = dao.getExecutionMetas(scopeId, scopeType);
        JSONObject referenceData = new JSONObject();

        for (Map<String, Object> exeMetaDataObjectMap : executionMetaData) {
            referenceData.put((String) exeMetaDataObjectMap.get("Key"), exeMetaDataObjectMap.get("Value"));
        }
        execution.put("referenceData", referenceData);
    }

    @Transactional
    protected String getLocalPath(String testScriptLink, String runId) {
        String link = UtilityHelper.getPath(testScriptLink, false);

        return properties.getLocalArtifactsPath() + PATH_SEPARATOR + projectName + PATH_SEPARATOR +
               runId + PATH_SEPARATOR + StringUtils.substringAfterLast(link, PATH_SEPARATOR);
    }

    @Transactional
    protected Map<String, Map<String, List<StepData>>> parseExcel(String iterationId, String path,
                                                                  boolean isWindows) throws IOException {
        StopWatch watch = new StopWatch();
        watch.start();
        File f = new File(path);
        Excel excel;
        Map<String, Map<String, List<StepData>>> scenarios = new HashMap<>();
        excel = new Excel(f, false, false);
        for (Worksheet worksheet : excel.getWorksheetsStartWith("")) {
            if (StringUtils.equalsAny(worksheet.getName(), SUMMARY_TAB_NAME, SHEET_SYSTEM)) { continue; }
            // insert iteration test data
            if (StringUtils.equals(worksheet.getName(), SHEET_MERGED_DATA)) {
                dao.insertIterationData(iterationId, worksheet);
                continue;
            }

            ExcelAddress address = new ExcelAddress(FIRST_STEP_ROW + ":" + COL_REASON +
                                                    worksheet.getSheet().getLastRowNum());
            ExcelArea area = new ExcelArea(worksheet, address, false);
            Map<String, List<StepData>> activities = new HashMap<>();
            String activity = null;
            List<StepData> steps = new ArrayList<>();
            List<List<XSSFCell>> wholeArea = area.getWholeArea();

            int size = wholeArea.size();
            for (int rowIndex = 0; rowIndex < size; rowIndex++) {
                List<XSSFCell> row = wholeArea.get(rowIndex);
                if (isEmptyRow(row)) { break; }

                String currentActivity = Excel.getCellValue(row.get(COL_IDX_TESTCASE));
                if (StringUtils.isNotBlank(currentActivity)) {
                    if (activity != null) { activities.put(activity, steps); }
                    activity = currentActivity;
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
            activities.put(activity, steps);
            scenarios.put(worksheet.getName(), activities);
        }
        excel.close();
        watch.stop();
        logger.info("/----------------------------------------------------------------\\");
        logger.info("Time taken to complete parse excel is " + watch.getTime());
        logger.info("\\----------------------------------------------------------------/");

        return scenarios;

    }

    @Transactional
    protected List<Object> addStepDetails(List<XSSFCell> row, boolean isWindows) {
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

    @Transactional
    protected boolean addStepMeta(List<XSSFCell> nextRow, List<List<Object>> stepLinks,
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

    @Transactional
    protected List<Object> addStepLogDetails(List<XSSFCell> row, boolean isScreenshot, boolean isWindows) {
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

    @Transactional
    protected void insertStepDetails(String activityId, Map<String, List<StepData>> scenarioData,
                                     String activityName, int activitySeq) {
        List<StepData> steps = scenarioData.get(StringUtils.replace(activityName, "\\n", "\n"));

        if (steps == null) {
            logger.info("Steps are null  for activity" + activityName + " and " + activitySeq);
            return;
        }
        dao.insertStepInfo(steps, activityId);
    }

    @Transactional
    protected String getLink(String text, boolean isWindows) {
        if (StringUtils.isBlank(text)) { return text; }

        String prefix = "HYPERLINK(IF(ISERROR(FIND(\"dos\",INFO(\"system\"))),";
        if (StringUtils.startsWith(text, prefix)) {
            String[] urls = StringUtils.substringsBetween(text, "\"", "\"");
            String link = isWindows ? UtilityHelper.getPath(urls[3], false) : urls[2];
            if (StringUtils.contains(link, project + "/")) {
                link = StringUtils.substringAfter(link, projectName + "/");
            }
            return link;
        } else if (StringUtils.containsAny(text, "http://", "https://")) {
            return StringUtils.substringBetween(text, "\"", "\"");
        }
        return text;
    }

    @Transactional
    protected String getLinkLabel(String text) {
        if (StringUtils.isBlank(text)) { return text; }
        String[] subStrings = StringUtils.substringsBetween(text, "\"", "\"");
        return subStrings[subStrings.length - 1];
    }

    @Transactional
    protected boolean isContainLogs(List<XSSFCell> row) {
        return StringUtils.isAllBlank(Excel.getCellValue(row.get(COL_IDX_TARGET)),
                                      Excel.getCellValue(row.get(COL_IDX_COMMAND))) &&
               StringUtils.isNotBlank(Excel.getCellValue(row.get(COL_IDX_PARAMS_START)));
    }

    @Transactional
    protected boolean isEmptyRow(List<XSSFCell> row) {
        return StringUtils.isAllBlank(Excel.getCellValue(row.get(COL_IDX_TARGET)),
                                      Excel.getCellValue(row.get(COL_IDX_COMMAND)),
                                      Excel.getCellValue(row.get(COL_IDX_PARAMS_START)),
                                      Excel.getCellValue(row.get(COL_IDX_CAPTURE_SCREEN)));
    }

    @Transactional
    protected JSONArray sortScripts(JSONArray scripts) {
        List<Object> list = scripts.toList();
        list.sort((script1, script2) -> compare((Map<String, Object>) script1, (Map<String, Object>) script2));
        return new JSONArray(GSON.toJson(list));
    }

    @Transactional
    protected int compare(Map<String, Object> script1, Map<String, Object> script2) {
        String planName1 = (String) script1.get("planName");
        String planName2 = (String) script2.get("planName");
        return planName1.compareTo(planName2);
    }

    @Transactional
    protected String formatToDate(String name) {
        List<String> groups = RegexUtils.collectGroups(name, TIMESTAMP_REGEX);
        return groups.get(0) + "/" + groups.get(1) + "/" + groups.get(2) + " " +
               groups.get(3) + ":" + groups.get(4) + ":" + groups.get(5);
    }
}
