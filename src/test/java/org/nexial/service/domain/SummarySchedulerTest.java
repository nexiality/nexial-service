package org.nexial.service.domain;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nexial.service.domain.dbconfig.ApplicationDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class SummarySchedulerTest {
    @Autowired
    private ApplicationDao dao;

    @Test
    public void scheduleData() throws InterruptedException {
        String project = "NotepadTest";
        String runId = "20191029_144728";
        // todo need to give resource path
        dao.insertIntoScheduleInfo(project, runId, "C:/Nexial-project/git/nexial-service/src/" +
                                                   "test/resources/execution-summary-artifacts/" +
                                                   "NotepadTest/20191029_144728/execution-detail.json");
        List<Map<String, Object>> scheduleInfoData = dao.getScheduleInfo();
        List<Map<String, Object>> scheduleInfo = scheduleInfoData
                                                     .stream()
                                                     .filter(row -> (row.get("ProjectName")).equals(project)
                                                                    && (row.get("RunId")).equals(runId))
                                                     .collect(Collectors.toList());
        Assert.assertNotNull(scheduleInfo);
        Assert.assertEquals(1, scheduleInfo.size());
        String scheduleInfoId = (String) scheduleInfo.get(0).get("Id");

        Thread.sleep(30000 * 2);

        String executionId = dao.getExecutionId(project, runId);
        Assert.assertNotNull(executionId);

        List<Map<String, Object>> scripts = dao.getScripts(executionId);
        Assert.assertNotNull(scripts);
        Assert.assertEquals(1, scripts.size());
        String scriptId = (String) scripts.get(0).get("Id");

        List<Map<String, Object>> iterations = dao.getIterations(scriptId);
        Assert.assertNotNull(iterations);
        Assert.assertEquals(1, iterations.size());
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
        Assert.assertEquals(9, steps.size());

        dao.deleteExecutionData(scheduleInfoId, project, runId);
    }
}
