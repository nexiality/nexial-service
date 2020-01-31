package org.nexial.service.domain;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nexial.commons.utils.ResourceUtils;
import org.nexial.core.plugins.json.JsonCommand;
import org.nexial.service.domain.dashboard.service.PurgeExecutionService;
import org.nexial.service.domain.dbconfig.ApplicationDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import com.google.gson.Gson;
import com.google.gson.JsonElement;

import static org.nexial.core.NexialConst.GSON;
import static org.nexial.core.NexialConst.GSON_COMPRESSED;

@RunWith(SpringRunner.class)
@SpringBootTest
public class SummarySchedulerTest {
    private static String jsonFile = "execution-detail.json";
    private static String artifactPath = "execution-summary-artifacts";
    private static String separator = "/";
    private String SUMMARY_OUTPUT = "/summary_output.json";

    @Autowired
    private ApplicationProperties properties;
    @Autowired
    private ApplicationDao dao;
    @Autowired
    private PurgeExecutionService purgeService;

    @BeforeClass
    public static void tearUp() {
        String DB_NAME = "nexial-db-test.db";
        File file = new File(DB_NAME);
        if (file.exists()) {
            FileUtils.deleteQuietly(file);
        }
        String resourceFilePath = ResourceUtils.getResourceFilePath(DB_NAME);
        if (resourceFilePath != null) {
            FileUtils.deleteQuietly(new File(resourceFilePath));
        }
    }

    @AfterClass
    public static void tearDown() {
        File file = new File("nexial-db-test.db");
        if (file.exists()) { FileUtils.deleteQuietly(file); }

        String resourceFilePath = ResourceUtils.getResourceFilePath("nexial-db-test.db");
        if (resourceFilePath != null) { FileUtils.deleteQuietly(new File(resourceFilePath)); }
    }

    @After
    public void cleanUp() {
        File file = new File(properties.getLocalExecutionSummaryPath());
        if (file.exists()) { FileUtils.deleteQuietly(file); }
    }

    @Test
    public void scheduleData() throws Exception {
        String project = "NotepadTest";
        String runId = "20191211_180549";
        String resourceFilePath = ResourceUtils.getResourceFilePath(artifactPath + separator + project +
                                                                    separator + runId + separator + jsonFile);

        String project1 = "image-compare";
        String runId1 = "20191220_164816";
        String resourceFilePath1 = ResourceUtils.getResourceFilePath(artifactPath + separator + project1 +
                                                                     separator + runId1 + separator + jsonFile);

        // todo need to provide proper
        Thread.sleep(10000);
        dao.insertIntoScheduleInfo(project1, runId1, resourceFilePath1);
        dao.insertIntoScheduleInfo(project, runId, resourceFilePath);

        Thread.sleep(properties.getSummarySchedulerTime() + 10000);

        assertSummary(project);
        assertSummary(project1);

        assertInsertedData(project, runId);
        assertInsertedData1(project1, runId1);

        assertSummaryAfterPurge(project);
        assertSummaryAfterPurge(project1);
    }

    private void assertInsertedData(String project, String runId) throws Exception {
        List<Map<String, Object>> scheduleInfoData = dao.getScheduleInfo();
        List<Map<String, Object>> scheduleInfo = scheduleInfoData
                                                     .stream()
                                                     .filter(row -> (row.get("ProjectName")).equals(project)
                                                                    && (row.get("RunId")).equals(runId))
                                                     .collect(Collectors.toList());
        Assert.assertNotNull(scheduleInfo);
        Assert.assertEquals(1, scheduleInfo.size());
        String scheduleInfoId = (String) scheduleInfo.get(0).get("Id");

        String executionId = dao.getExecutionId(project, runId);
        Assert.assertNotNull(executionId);

        /*List<Map<String, Object>> executionDatas = dao.getExecutionData1();
        Assert.assertNotNull(executionDatas);
        Assert.assertEquals(5, executionDatas.size());

        List<Map<String, Object>> executionMetaDatas = dao.getExecutionMetaData();
        Assert.assertNotNull(executionMetaDatas);
        Assert.assertEquals(8, executionMetaDatas.size());*/
        List<Map<String, Object>> scripts = dao.getScripts(executionId);
        Assert.assertNotNull(scripts);
        Assert.assertEquals(1, scripts.size());
        String scriptId = (String) scripts.get(0).get("Id");

        List<Map<String, Object>> iterations = dao.getIterations(scriptId);
        Assert.assertNotNull(iterations);
        Assert.assertEquals(2, iterations.size());
        String iterationId = (String) iterations.get(0).get("Id");

        List<Map<String, Object>> scenarios = dao.getScenarios(iterationId);
        Assert.assertNotNull(scenarios);
        Assert.assertEquals(1, scenarios.size());
        String scenarioId = (String) scenarios.get(0).get("Id");

        List<Map<String, Object>> activities = dao.getActivities(scenarioId);
        Assert.assertNotNull(activities);
        Assert.assertEquals(1, activities.size());
        String activityId = (String) activities.get(0).get("Id");

        List<Map<String, Object>> steps = dao.getSteps(activityId);
        Assert.assertNotNull(steps);
        Assert.assertEquals(5, steps.size());

        // dao.deleteExecutionData(scheduleInfoId, project, runId);
        purgeService.purgeWithRunID(project, runId);
    }

    private void assertInsertedData1(String project, String runId) throws Exception {
        List<Map<String, Object>> scheduleInfoData = dao.getScheduleInfo();
        List<Map<String, Object>> scheduleInfo = scheduleInfoData
                                                     .stream()
                                                     .filter(row -> (row.get("ProjectName")).equals(project)
                                                                    && (row.get("RunId")).equals(runId))
                                                     .collect(Collectors.toList());
        Assert.assertNotNull(scheduleInfo);
        Assert.assertEquals(1, scheduleInfo.size());
        String scheduleInfoId = (String) scheduleInfo.get(0).get("Id");

        String executionId = dao.getExecutionId(project, runId);
        Assert.assertNotNull(executionId);

        List<Map<String, Object>> executionDatas = dao.getExecutionData1();
        Assert.assertNotNull(executionDatas);
        Assert.assertEquals(8, executionDatas.size());

        List<Map<String, Object>> executionMetaDatas = dao.getExecutionMetaData();
        Assert.assertNotNull(executionMetaDatas);
        Assert.assertEquals(12, executionMetaDatas.size());

        List<Map<String, Object>> scripts = dao.getScripts(executionId);
        Assert.assertNotNull(scripts);
        Assert.assertEquals(1, scripts.size());
        String scriptId = (String) scripts.get(0).get("Id");

        for (int i = 0; i < scripts.size(); i++) {
            List<Map<String, Object>> iterations = dao.getIterations(scriptId);
            Assert.assertNotNull(iterations);
            Assert.assertEquals(2, iterations.size());
            for (int j = 0; j < iterations.size(); j++) {
                String iterationId = (String) iterations.get(j).get("Id");

                List<Map<String, Object>> scenarios = dao.getScenarios(iterationId);
                Assert.assertNotNull(scenarios);
                Assert.assertEquals(1, scenarios.size());

                for (int k = 0; k < scenarios.size(); k++) {
                    String scenarioId = (String) scenarios.get(k).get("Id");

                    List<Map<String, Object>> activities = dao.getActivities(scenarioId);
                    Assert.assertNotNull(activities);
                    Assert.assertEquals(1, activities.size());
                    for (int l = 0; l < activities.size(); l++) {
                        String activityId = (String) activities.get(l).get("Id");

                        List<Map<String, Object>> steps = dao.getSteps(activityId);
                        Assert.assertNotNull(steps);
                        Assert.assertEquals(8, steps.size());
                    }
                }
            }

        }

        // dao.deleteExecutionData(scheduleInfoId, project, runId);
        purgeService.purgeWithRunID(project, runId);
    }

    private void assertSummary(String project) throws IOException {
        File expectedSummary = new File(ResourceUtils.getResourceFilePath("expected-nexial-summary/" + project +
                                                                          SUMMARY_OUTPUT));
        File actualSummary = new File(properties.getLocalExecutionSummaryPath() + "/" + project + SUMMARY_OUTPUT);

        String expected = FileUtils.readFileToString(expectedSummary, "UTF-8");
        String actual = FileUtils.readFileToString(actualSummary, "UTF-8");
        JsonElement actualElement = GSON.fromJson(actual, JsonElement.class);
        JsonElement expectedElement = GSON.fromJson(expected, JsonElement.class);
        Assert.assertEquals(GSON_COMPRESSED.toJson(expectedElement), GSON_COMPRESSED.toJson(actualElement));
    }

    private void assertSummaryAfterPurge(String project) throws IOException {
        File actualSummary = new File(properties.getLocalExecutionSummaryPath() + separator +
                                      project + SUMMARY_OUTPUT);
        String actual = FileUtils.readFileToString(actualSummary, "UTF-8");

        Assert.assertEquals("{\"results\":[]}", actual);
    }
}
