package org.nexial.service.domain.dbconfig;

import java.io.IOException;
import java.util.ArrayList;
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
import org.nexial.service.domain.dashboard.service.StepData;
import org.nexial.service.domain.utils.Constants.Status;
import org.nexial.service.domain.utils.UtilityHelper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.nexial.core.NexialConst.Project.NEXIAL_EXECUTION_TYPE_PLAN;
import static org.nexial.core.NexialConst.Project.NEXIAL_EXECUTION_TYPE_SCRIPT;
import static org.nexial.core.excel.ExcelConfig.ADDR_FIRST_DATA_COL;
import static org.nexial.service.domain.utils.Constants.PATH_SEPARATOR;
import static org.nexial.service.domain.utils.Constants.SIMPLE_DATE_FORMAT;
import static org.nexial.service.domain.utils.Constants.Status.FAILED;
import static org.nexial.service.domain.utils.Constants.Status.RECEIVED;

@Repository("dao")
public class ApplicationDao {
    private static final int ID_LENGTH = 16;
    private final SQLiteConfig sqLiteConfig;
    private Properties properties;

    public ApplicationDao(SQLiteConfig sqLiteConfig) { this.sqLiteConfig = sqLiteConfig; }

    @PostConstruct
    public void initialize() {
        try {
            properties = ResourceUtils.loadProperties("sqlQueryStatements.properties");
            createDatabaseTables();
        } catch (IOException ex) {
            throw new RuntimeException("SQL Query Statement property Not loaded");
        }
    }

    @Transactional
    public boolean deleteExecutionData(String scheduleInfoId, String project, String name) {
        List<String> list = new ArrayList<>();
        boolean flag = false;
        String executionId = getExecutionId(project, name);
        list.add(executionId);
        List<Map<String, Object>> scripts = getScripts(executionId);
        scripts.forEach(row -> {
            String scriptId = (String) row.get("Id");
            list.add(scriptId);
            List<Map<String, Object>> iterations = getIterations(scriptId);
            iterations.forEach(row1 -> {
                String iterationId = (String) row1.get("Id");
                list.add(iterationId);
                List<Map<String, Object>> scenarios = getScenarios(iterationId);

                scenarios.forEach(row2 -> {
                    String scenarioId = (String) row2.get("Id");
                    list.add(scenarioId);
                    List<Map<String, Object>> activities = getActivities(scenarioId);
                    activities.forEach(row3 -> list.add((String) row3.get("Id")));
                });
            });
        });
        sqLiteConfig.execute(getSqlStatement("SQL_DELETE_SCHEDULE_INFO"), scheduleInfoId);
        sqLiteConfig.execute(getSqlStatement("SQL_DELETE_EXECUTION"), project, name);
        for (String id : list) {
            sqLiteConfig.execute(getSqlStatement("SQL_DELETE_EXECUTION_DATA"), id);
            sqLiteConfig.execute(getSqlStatement("SQL_DELETE_EXECUTIONMETA_DATA"), id);
        }
        flag = true;
        return flag;
    }

    public List<Map<String, Object>> getActivities(String scenarioId) {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_ACTIVITY"), scenarioId);
    }

    public List<Map<String, Object>> getSteps(String activityId) {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_STEPS"), activityId);
    }

    public List<Map<String, Object>> getStepLink(String stepId) {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_STEPLINKS"), stepId);
    }

    public List<Map<String, Object>> getLogs(String stepId) {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_LOGS"), stepId);
    }

    public List<Map<String, Object>> getScenarios(String iterationId) {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_SCENARIO"), iterationId);
    }

    public List<Map<String, Object>> getIterations(String scriptId) {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_ITERATION"), scriptId);
    }

    public List<Map<String, Object>> getScripts(String executionId) {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_SCRIPT"), executionId);
    }

    public String getExecutionId(String project, String name) {
        return (String) sqLiteConfig.queryForObject(getSqlStatement("SQL_SELECT_EXECUTION_ID"),
                                                    String.class, project, name);
    }

    @Transactional
    public String insertExecutionInfo(JSONObject execution, String project, String prefix) {
        String executionId = generateId();
        String name = execution.getString("name");
        String executionLog = UtilityHelper.getPath(JSONPath.find(execution, "nestedExecutions[0].executionLog"),
                                                    false);
        String location = StringUtils.substringBefore(executionLog, project) + project + PATH_SEPARATOR;
        executionLog = StringUtils.substringAfter(executionLog, location);
        String logFile = "output/" + name + "/logs/nexial-" + name + ".log";
        //Todo execution log file location check need to create manually or get from  script
        // String logFile = JSONPath.find(jsonData,"nestedExecutions[0].executionLog");
        String executionType = (JSONPath.find(execution, "nestedExecutions[0].planName") == null) ?
                               NEXIAL_EXECUTION_TYPE_SCRIPT : NEXIAL_EXECUTION_TYPE_PLAN;

        sqLiteConfig.execute(getSqlStatement("SQL_INSERT_EXECUTION"), executionId, name,
                             location, logFile, executionLog, executionType, prefix, project);

        insertExecutionData(execution, executionId);
        insertExecutionMetaData(execution, executionId);
        insertExecutionEnvironmentInfo(execution, executionId);
        return executionId;
    }

    @Transactional
    public String insertPlanInfo(String project, String executionId, JSONObject scriptObject,
                                 int planSequence, String planName1) {
        String planId = generateId();
        String planFile = UtilityHelper.getPath(scriptObject.getString("planFile"), false);
        sqLiteConfig.execute(getSqlStatement("SQL_INSERT_PLAN"), planId, executionId, planName1,
                             planSequence, StringUtils.substringAfter(planFile, project + "/"));
        return planId;
    }

    @Transactional
    public String insertScriptInfo(String project, String executionId, String planId,
                                   int sequence, JSONObject scriptObject) {
        String scriptId = generateId();
        String scriptFile = UtilityHelper.getPath(scriptObject.getString("scriptFile"), false);
        sqLiteConfig.execute(getSqlStatement("SQL_INSERT_SCRIPT"),
                             scriptId, scriptObject.getString("name"), sequence, planId,
                             executionId, StringUtils.substringAfter(scriptFile, project + "/"));

        insertExecutionData(scriptObject, scriptId);
        insertExecutionMetaData(scriptObject, scriptId);
        return scriptId;
    }

    @Transactional
    public String insertIterationInfo(String scriptId, JSONObject iterationObject, String testScriptLink) {
        String iterationId = generateId();
        sqLiteConfig.execute(getSqlStatement("SQL_INSERT_ITERATION"),
                             iterationId, iterationObject.getString("name"), scriptId,
                             testScriptLink, iterationObject.getInt("iterationIndex"));

        insertExecutionData(iterationObject, iterationId);
        insertExecutionMetaData(iterationObject, iterationId);
        return iterationId;
    }

    @Transactional
    public void insertIterationData(String iterationId, Worksheet worksheet) {
        int lastDataRow = worksheet.findLastDataRow(ADDR_FIRST_DATA_COL);
        for (int index = 0; index < lastDataRow; index++) {
            XSSFRow row = worksheet.getSheet().getRow(index);
            sqLiteConfig.execute(getSqlStatement("SQL_INSERT_ITERATION_DATA"),
                                 generateId(), Excel.getCellValue(row.getCell(0)),
                                 Excel.getCellValue(row.getCell(1)), iterationId);
        }
    }

    @Transactional
    public String insertScenarioInfo(String iterationId,
                                     String scenario,
                                     int scenarioIndex,
                                     JSONObject scenarioObject) {
        String scenarioId = generateId();
        sqLiteConfig.execute(getSqlStatement("SQL_INSERT_SCENARIO"), scenarioId,
                             scenario, iterationId, scenarioIndex, "scenarioURL");

        insertExecutionData(scenarioObject, scenarioId);
        insertExecutionMetaData(scenarioObject, scenarioId);
        return scenarioId;
    }

    @Transactional
    public String insertActivityInfo(String scenarioId, String activity, int activityIndex, JSONObject activityObj) {
        String activityId = generateId();
        sqLiteConfig.execute(getSqlStatement("SQL_INSERT_ACTIVITY"),
                             activityId, activity, scenarioId, activityIndex);

        insertExecutionData(activityObj, activityId);
        insertExecutionMetaData(activityObj, activityId);
        return activityId;
    }

    @Transactional
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

    public void deleteWorkerInfo(String project, String prefix) {
        sqLiteConfig.execute(getSqlStatement("SQL_DELETE_WORKER_INFO"), project, prefix);
    }

    @Transactional
    public void insertIntoScheduleInfo(String projectName, String runId, String outputPath) {
        String dateNow = SIMPLE_DATE_FORMAT.format(new Date());
        String prefix = StringUtils.substringBefore(runId, ".");
        String id = StringUtils.substringAfter(runId, ".");
        if (StringUtils.isEmpty(id)) {
            prefix = "";
        }
        Object[] param = {generateId(), projectName, prefix, runId, RECEIVED, dateNow,
                          dateNow, UtilityHelper.getPath(outputPath, false)};
        sqLiteConfig.execute(getSqlStatement("SQL_INSERT_SCHEDULE_INFO"), param);
    }

    @Transactional
    public List<Map<String, Object>> getExecutionMetas(Object scopeId, String scopeType) {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_EXECUTION_META"), scopeId, scopeType);
    }

    public String updateWorkerInfo(String project, String prefix, String threadName) {
        String id = generateId();
        sqLiteConfig.execute(getSqlStatement("SQL_INSERT_WORKER_INFO"), id, project, prefix, threadName);
        return id;
    }

    @Transactional
    public List<Map<String, Object>> getExecutionData(Object scopeId, String scopeType) {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_EXECUTION_DATA"), scopeId, scopeType);
    }

    @Transactional
    public List<Map<String, Object>> getExecutionData1() {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_EXECUTION_DATA1"));
    }

    @Transactional
    public List<Map<String, Object>> getExecutionMetaData() {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_EXECUTION_META_DATA"));
    }

    public void updateScheduleInfoStatus(String runId, Status status) {
        String processingDate = SIMPLE_DATE_FORMAT.format(new Date());
        sqLiteConfig.execute(getSqlStatement("SQL_UPDATE_SCHEDULE_INFO_STATUS_STAGE"),
                             status, processingDate, runId);
    }

    public void insertIntoProjectInfo(String project, String dashboard, String description) {
        String date = SIMPLE_DATE_FORMAT.format(new Date());
        String projectId = generateId();
        int result = sqLiteConfig.execute(getSqlStatement("SQL_INSERT_PROJECTINFO"),
                                          projectId, project, description, date, date);
        if (result == 1) { insertDashboardInfo(dashboard, description, projectId);}
    }

    public void insertDashboardInfo(String dashboard, String description, String projectId) {
        String date = SIMPLE_DATE_FORMAT.format(new Date());
        sqLiteConfig.execute(getSqlStatement("SQL_INSERT_DASHBOARDINFO"),
                             generateId(), dashboard, description, projectId, date, date);
    }

    public void updateProjectInfo(String id, String name, String description) {
        String date = SIMPLE_DATE_FORMAT.format(new Date());
        sqLiteConfig.execute(getSqlStatement("SQL_UPDATE_DASHBOARD"), name, description, date, id);
    }

    @Transactional
    public void updateSuperDashboardInfo(String id, String name, String description) {
        String existingName = (String) sqLiteConfig.queryForObject(getSqlStatement("SQL_SELECT_SUPERDASHBOARD_NAME"), String.class, id);
        String date = SIMPLE_DATE_FORMAT.format(new Date());
        sqLiteConfig.execute(getSqlStatement("SQL_UPDATE_SUPERDASHBOARD_INFO"), name, description, date, existingName);
    }

    @Transactional
    public void updateSuperDashboardProjects(String id, String name, String description, int maxPosition, Map<String, Object> row) {
        if (id != null) { updateSuperDashboardInfo(id, name, description); }
        String date = SIMPLE_DATE_FORMAT.format(new Date());
        sqLiteConfig.execute(getSqlStatement("SQL_INSERT_SUPERDASHBOARD_INFO"), generateId(), name, description,maxPosition,
                             row.get("ProjectId"), row.get("DashboardId"), date, date);
    }

    @Transactional
    public List<Map<String, Object>> getRunIds(String project, String prefix) {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_RUN_ID_SCHEDULE_INFO"),
                                         project, prefix, RECEIVED);
    }

    @Transactional
    public String getExecutionOutputPath(String runId) {
        return (String) sqLiteConfig.queryForObject(getSqlStatement("SQL_SELECT_OUTPUT_PATH_SCHEDULE_INFO"),
                                                    String.class,
                                                    new Object[]{runId, RECEIVED});
    }

    @Transactional
    public int getWorkerCount(String projectName, String prefix) {
        return (int) sqLiteConfig.queryForObject(getSqlStatement("SQL_SELECT_COUNT_WORKER_INFO"),
                                                 Integer.class,
                                                 new Object[]{projectName, prefix});

    }

    @Transactional
    public String getWorkerId(String projectName, String prefix) {
        return (String) sqLiteConfig.queryForObject(getSqlStatement("SQL_SELECT_WORKER_INFO"),
                                                    String.class,
                                                    new Object[]{projectName, prefix});
    }

    @Transactional
    public List<Map<String, Object>> getExecutionSummary(String project, String prefix) {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_EXECUTION"), project, prefix, FAILED);
    }

    @Transactional
    public List<Map<String, Object>> getScriptSummaryList(Object executionId) {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_SCRIPT"), executionId);
    }

    @Transactional
    public List<Map<String, Object>> getIterationList(Object scriptId) {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_ITERATION"), scriptId);
    }

    @Transactional
    public List<Map<String, Object>> getReceivedProjects() {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_SCHEDULE_INFO"), RECEIVED);
    }

    @Transactional
    public List<Map<String, Object>> getScheduleInfo() {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_SCHEDULE_INFOS"));
    }

    @Transactional
    public List<Map<String, Object>> getScheduleInfoWithRunId(String runId) {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_SCHEDULE_INFO_RUNID"), runId);
    }

    public int getMaxExecutionCount(String name) {
        return (int)sqLiteConfig.queryForObject(getSqlStatement("SQL_COUNT_POSITION_SUPERDASHBOARD"),Integer.class,name);
    }

    public void updatePosition(Object id, Object position) {
        String date = SIMPLE_DATE_FORMAT.format(new Date());
        sqLiteConfig.execute(getSqlStatement("SQL_UPDATE_SUPERDASHBOARD_POSITION"),position,date,id);
    }

    @Transactional
    protected void insertStepLinks(String stepId, StepData stepData) {
        List<List<Object>> stepLinkParams = stepData.getStepLinkParams();
        if (stepLinkParams == null || stepLinkParams.size() == 0) { return; }
        stepLinkParams.forEach(list -> {
            if (list.size() == 0) { return; }
            list.add(0, generateId());
            list.add(1, stepId);
            sqLiteConfig.execute(getSqlStatement("SQL_INSERT_STEP_LINKS"), list.toArray());
        });

    }

    @Transactional
    protected void insertLogInfo(String stepId, StepData steps) {
        List<List<Object>> logsParams = steps.getLogsParams();
        if (logsParams == null || logsParams.size() == 0) { return; }
        logsParams.forEach(list -> {
            if (list.size() == 0) { return; }
            list.add(0, generateId());
            list.add(1, stepId);
            sqLiteConfig.execute(getSqlStatement("SQL_INSERT_LOGS"), list.toArray());
        });

    }

    @Transactional
    protected void insertExecutionData(JSONObject json, String scopeId) {
        Object[] executionDataParams = {generateId(), json.getLong("startTime"),
                                        json.getLong("endTime"), json.getInt("totalSteps"),
                                        json.getInt("passCount"), json.getInt("failCount"),
                                        json.getInt("warnCount"), json.getInt("executed"),
                                        json.getBoolean("failedFast"), scopeId,
                                        json.getString("executionLevel")};
        sqLiteConfig.execute(getSqlStatement("SQL_INSERT_EXECUTION_DATA"), executionDataParams);
    }

    @Transactional
    protected void insertExecutionMetaData(JSONObject json, String scopeId) {
        if (!json.has("referenceData")) { return; }
        String creationTime = DateUtility.format(json.getLong("startTime"));
        Map<String, Object> referenceMap = json.getJSONObject("referenceData").toMap();
        referenceMap.forEach((key, value) -> sqLiteConfig.execute(getSqlStatement("SQL_INSERT_EXECUTION_META_DATA"),
                                                                  generateId(), key, value, creationTime, scopeId,
                                                                  json.getString("executionLevel")));
    }

    public List<Map<String, Object>> getProjectsList() {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_PROJECTS"), null);
    }

    public List<Map<String, Object>> getDashboardList(String id) {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_DASHBOARDS"), id);
    }

    public List<Map<String, Object>> getSuperDashboardList() {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_SUPERDASHBOARDS"), null);
    }

    public List<Map<String, Object>> getSuperDashboardProjectList(String name) {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_SUPERDASHBOARD_JOIN"), name);
    }

    public List<Map<String, Object>> getAllProjectsList() {
        return sqLiteConfig.queryForList(getSqlStatement("SQL_SELECT_PROJECT_DASHBOARD_JOIN"));
    }

    @Transactional
    public void deleteFromSuperDashboardByName(String name) {
        sqLiteConfig.execute(getSqlStatement("SQL_DELETE_SUPERDASHBOARD_PROJECTS_BY_NAME"), name);
    }

    @Transactional
    public void deleteFromSuperDashboardById(String Id) {
        sqLiteConfig.execute(getSqlStatement("SQL_DELETE_SUPERDASHBOARD_PROJECTS_BY_ID"), Id);
    }

    private String generateId() { return RandomStringUtils.randomAlphanumeric(ID_LENGTH); }

    @Transactional
    protected String getSqlStatement(String sqlStatement) { return properties.getProperty(sqlStatement); }

    @Transactional
    protected String insertExecutionEnvironmentInfo(JSONObject execution, String executionId) {
        String id = generateId();
        sqLiteConfig.execute(getSqlStatement("SQL_INSERT_EXECUTION_ENVIRONMENT"),
                             id, execution.get("runHost"), execution.get("runUser"),
                             execution.get("runHostOs"), EMPTY, EMPTY, EMPTY, EMPTY, executionId);
        return id;
    }

    private void createDatabaseTables() {
        sqLiteConfig.execute(getSqlStatement("SQL_ENFORCE_FOREIGN_KEYS"));
        properties.forEach((key, value) -> {
            if (!StringUtils.startsWith((String) key, "SQL_CREATE")) { return; }
            System.out.println((String) key);
            sqLiteConfig.execute((String) value);
        });
    }
}