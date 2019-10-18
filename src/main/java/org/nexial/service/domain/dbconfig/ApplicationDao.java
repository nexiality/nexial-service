package org.nexial.service.domain.dbconfig;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.annotation.PostConstruct;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.json.JSONObject;
import org.nexial.commons.utils.DateUtility;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.core.excel.Excel;
import org.nexial.core.excel.Excel.Worksheet;
import org.nexial.core.utils.JSONPath;
import org.nexial.service.domain.dashboard.scheduler.Activity.StepData;
import org.nexial.service.domain.utils.Constants.Status;
import org.springframework.stereotype.Component;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.nexial.core.NexialConst.Project.NEXIAL_EXECUTION_TYPE_PLAN;
import static org.nexial.core.NexialConst.Project.NEXIAL_EXECUTION_TYPE_SCRIPT;
import static org.nexial.core.excel.ExcelConfig.ADDR_FIRST_DATA_COL;
import static org.nexial.service.domain.utils.Constants.SIMPLE_DATE_FORMAT;
import static org.nexial.service.domain.utils.Constants.Status.INPROGRESS;
import static org.nexial.service.domain.utils.Constants.Status.RECEIVED;

@Component
public class ApplicationDao {
    private static final int ID_LENGTH = 16;
    private String project;
    private String prefix;
    private final SQLiteConfig sqLiteConfig;

    private Properties properties;

    public ApplicationDao(SQLiteConfig sqLiteConfig) { this.sqLiteConfig = sqLiteConfig; }

    public void setProject(String project) { this.project = project; }

    public void setPrefix(String prefix) { this.prefix = prefix; }

    @PostConstruct
    public void initialize() {
        try {
            // Can add both into one properties file
            properties = ResourceUtils.loadProperties("sqlCreateStatements.properties");
            properties.putAll(ResourceUtils.loadProperties("sqlQueryStatements.properties"));
            createDatabaseTables();
        } catch (IOException ex) {
            throw new RuntimeException("SQL Query Statement property Not loaded");
        }
    }

    public String insertExecutionInfo(JSONObject execution) {
        String executionId = generateId();
        String name = execution.getString("name");
        //Todo execution log file location check need to create manually or get from  script
        // String logFile = JSONPath.find(jsonData,"nestedExecutions[0].executionLog");
        String logFile = "logs" + File.separator + "nexial-" + name + ".log";
        String executionType = (JSONPath.find(execution, "nestedExecutions[0].planName") == null) ?
                               NEXIAL_EXECUTION_TYPE_SCRIPT : NEXIAL_EXECUTION_TYPE_PLAN;
        sqLiteConfig.execute(getSqlStatement("SQL_INSERT_EXECUTION"), executionId, name, EMPTY,
                             logFile, EMPTY, executionType, prefix, project);

        insertExecutionData(execution, executionId);
        insertExecutionMetaData(execution, executionId);
        insertExecutionEnvironmentInfo(execution, executionId);

        return executionId;
    }

    public String insertPlanInfo(String executionId, JSONObject scriptObject, int planSequence, String planName1) {
        String planId = generateId();
        sqLiteConfig.execute(getSqlStatement("SQL_INSERT_PLAN"), planId, executionId, planName1, planSequence,
                             scriptObject.getString("planFile"));
        return planId;
    }

    public String insertScriptInfo(String executionId, String planId, int sequence, JSONObject scriptObject) {
        String scriptId = generateId();
        sqLiteConfig.execute(getSqlStatement("SQL_INSERT_SCRIPT"), scriptId, scriptObject.getString("name"),
                             sequence, planId, executionId, scriptObject.getString("scriptFile"));
        insertExecutionData(scriptObject, scriptId);
        insertExecutionMetaData(scriptObject, scriptId);
        return scriptId;
    }

    public String insertIterationInfo(String scriptId, JSONObject iterationObject, String testScriptLink) {
        String iterationId = generateId();
        sqLiteConfig.execute(getSqlStatement("SQL_INSERT_ITERATION"), iterationId, iterationObject.getString("name"),
                             scriptId, testScriptLink, iterationObject.getInt("iterationIndex"));

        insertExecutionData(iterationObject, iterationId);
        insertExecutionMetaData(iterationObject, iterationId);
        return iterationId;
    }

    public void insertIterationData(String iterationId, Worksheet worksheet) {
        int lastDataRow = worksheet.findLastDataRow(ADDR_FIRST_DATA_COL);
        for (int index = 0; index < lastDataRow; index++) {
            XSSFRow row = worksheet.getSheet().getRow(index);
            sqLiteConfig.execute(getSqlStatement("SQL_INSERT_ITERATION_DATA"),
                                 generateId(), Excel.getCellValue(row.getCell(0)),
                                 Excel.getCellValue(row.getCell(1)), iterationId);
        }
    }

    public String insertScenarioInfo(String scenario,
                                     int scenarioIndex,
                                     String iterationId,
                                     JSONObject scenarioObject) {
        String scenarioId = generateId();
        sqLiteConfig.execute(getSqlStatement("SQL_INSERT_SCENARIO"), scenarioId,
                             scenario, iterationId, scenarioIndex, "scenarioURL");
        insertExecutionData(scenarioObject, scenarioId);
        insertExecutionMetaData(scenarioObject, scenarioId);
        return scenarioId;
    }

    public String insertActivityInfo(String scenarioId, int activityIndex,
                                     String activityName, JSONObject activityObject) {
        String activityId = generateId();
        sqLiteConfig.execute(getSqlStatement("SQL_INSERT_ACTIVITY"),
                             activityId, activityName, scenarioId, activityIndex);
        insertExecutionData(activityObject, activityId);
        insertExecutionMetaData(activityObject, activityId);
        return activityId;
    }

    public void insertStepInfo(List<StepData> steps, String activityId) {
        steps.forEach(step -> {
            String stepId = generateId();
            List<Object> stepParams = step.getStepParams();
            stepParams.add(0, stepId);
            stepParams.add(1, activityId);
            sqLiteConfig.execute(getSqlStatement("SQL_INSERT_STEPS"), stepParams.toArray());
            insertStepLinks(stepId, step);
            insertLogInfo(stepId, step);
        });
    }

    public void insertStepLinks(String stepId, StepData stepData) {
        List<List<Object>> stepLinkParams = stepData.getStepLinkParams();
        if (stepLinkParams == null || stepLinkParams.size() == 0) { return; }
        stepLinkParams.forEach(list -> {
            if (list.size() == 0) { return; }
            list.add(0, generateId());
            list.add(1, stepId);
            sqLiteConfig.execute(getSqlStatement("SQL_INSERT_STEP_LINKS"), list.toArray());
        });

    }

    public void insertLogInfo(String stepId, StepData steps) {
        List<List<Object>> logsParams = steps.getLogsParams();
        if (logsParams == null || logsParams.size() == 0) { return; }
        logsParams.forEach(list -> {
            if (list.size() == 0) { return; }
            list.add(0, generateId());
            list.add(1, stepId);
            sqLiteConfig.execute(getSqlStatement("SQL_INSERT_LOGS"), list.toArray());
        });

    }

    public void insertExecutionData(JSONObject json, String scopeId) {
        Object[] executionDataParams = {generateId(), json.getLong("startTime"),
                                        json.getLong("endTime"), json.getInt("totalSteps"),
                                        json.getInt("passCount"), json.getInt("failCount"),
                                        json.getInt("warnCount"), json.getInt("executed"),
                                        json.getBoolean("failedFast"), scopeId,
                                        json.getString("executionLevel")};
        sqLiteConfig.execute(getSqlStatement("SQL_INSERT_EXECUTION_DATA"), executionDataParams);
    }

    public void insertExecutionMetaData(JSONObject json, String scopeId) {
        if (!json.has("referenceData")) { return; }
        String creationTime = DateUtility.format(json.getLong("startTime"));
        Map<String, Object> referenceMap = json.getJSONObject("referenceData").toMap();
        referenceMap.forEach((key, value) -> sqLiteConfig.execute(getSqlStatement("SQL_INSERT_EXECUTION_META_DATA"),
                                                                  generateId(), key, value, creationTime, scopeId,
                                                                  json.getString("executionLevel")));
    }

    public String updateWorkerInfo(String threadName) {
        String id = generateId();
        sqLiteConfig.execute(getSqlStatement("SQL_INSERT_WORKER_INFO"), id, project, prefix, threadName);
        return id;
    }

    public void deleteWorkerInfo() {
        sqLiteConfig.execute(getSqlStatement("SQL_DELETE_WORKER_INFO"), project, prefix);
    }

    public void updateScheduleInfoStatus(Status status) {
        String dateNow = SIMPLE_DATE_FORMAT.format(new Date());
        sqLiteConfig.execute(getSqlStatement("SQL_UPDATE_SCHEDULE_INFO_STATUS"),
                             status, dateNow, project, prefix);
    }

    public void updateScheduleInfoStatusInProgress(String runId) {
        String processingDate = SIMPLE_DATE_FORMAT.format(new Date());
        sqLiteConfig.execute(getSqlStatement("SQL_UPDATE_SCHEDULE_INFO_STATUS_STAGE"),
                             INPROGRESS, processingDate, runId);
    }

    public void insertIntoScheduleInfo(String projectName, String runId, String outputPath) {
        String dateNow = SIMPLE_DATE_FORMAT.format(new Date());
        String prefix = StringUtils.substringBefore(runId, ".");
        String id = StringUtils.substringAfter(runId, ".");
        if (StringUtils.isEmpty(id)) {
            prefix = "";
        }
        Object[] param = {generateId(), projectName, prefix, runId, RECEIVED, dateNow,
                          dateNow, StringUtils.replace(outputPath, "\\", "/")};
        sqLiteConfig.execute("SQL_INSERT_SCHEDULE_INFO", param);
    }

    public List<Map<String, Object>> getRunIds() {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_RUN_ID_SCHEDULE_INFO"),
                                         new Object[]{project, prefix, RECEIVED});
    }

    public String getExecutionOutputPath(String runId) {
        return (String) sqLiteConfig.queryForObject(getSqlStatement("SQL_SELECT_OUTPUT_PATH_SCHEDULE_INFO"),
                                                    new Object[]{runId, RECEIVED},
                                                    String.class);
    }

    public int getWorkerCount(String projectName, String prefix) {
        return (int) sqLiteConfig.queryForObject(getSqlStatement("SQL_SELECT_COUNT_WORKER_INFO"),
                                                 new Object[]{projectName, prefix},
                                                 Integer.class);

    }

    public String getWorkerId(String projectName, String prefix) {
        return (String) sqLiteConfig.queryForObject(getSqlStatement("SQL_SELECT_WORKER_INFO"),
                                                    new Object[]{projectName, prefix},
                                                    String.class);
    }

    public List<Map<String, Object>> getExecutionSummary() {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_EXECUTION"), new Object[]{project, prefix});
    }

    public List<Map<String, Object>> getScriptSummaryList(Object executionId) {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_SCRIPT"), new Object[]{executionId});
    }

    public List<Map<String, Object>> getIterationList(Object scriptId) {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_ITERATION"), new Object[]{scriptId});
    }

    public List<Map<String, Object>> getExecutionMetas(Object executionId) {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_EXECUTION_META"),
                                         new Object[]{executionId, "EXECUTION"});
    }

    public List<Map<String, Object>> getExecutionData(Object id, String scopeType) {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_EXECUTION_DATA"), new Object[]{id, scopeType});
    }

    public List<Map<String, Object>> getReceivedProjects() {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_SCHEDULE_INFO"), new Object[]{RECEIVED});
    }

    private String generateId() { return RandomStringUtils.randomAlphanumeric(ID_LENGTH); }

    private void createDatabaseTables() {
        properties.forEach((key, value) -> {
            if (!StringUtils.startsWith((String) key, "SQL_CREATE")) { return; }
            System.out.println((String) key);
            sqLiteConfig.execute((String) value);
        });
    }

    private String getSqlStatement(String sqlStatement) { return properties.getProperty(sqlStatement); }

    private String insertExecutionEnvironmentInfo(JSONObject execution, String executionId) {
        String id = generateId();
        sqLiteConfig.execute(getSqlStatement("SQL_INSERT_EXECUTION_ENVIRONMENT"),
                             id, execution.get("runHost"), execution.get("runUser"),
                             execution.get("runHostOs"), EMPTY, EMPTY, EMPTY, EMPTY, executionId);
        return id;
    }
}
