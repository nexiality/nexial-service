package org.nexial.service.domain.dashboard.service;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.nexial.service.domain.ApplicationProperties;
import org.nexial.service.domain.dbconfig.ApplicationDao;
import org.nexial.service.domain.utils.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Service;

import static org.nexial.service.domain.utils.Constants.SIMPLE_DATE_FORMAT;
import static org.nexial.service.domain.utils.Constants.SIMPLE_DATE_FORMAT1;

@Service
public class
PurgeExecutionService {
    private static final Logger logger = LoggerFactory.getLogger(PurgeExecutionService.class);
    private final ApplicationProperties properties;
    private final ApplicationDao dao;
    private final BeanFactory factory;

    public PurgeExecutionService(BeanFactory factory, ApplicationProperties properties, ApplicationDao dao) {
        this.factory = factory;
        this.properties = properties;
        this.dao = dao;
    }

    public void autoPurging() {
        Set<String> projects = new HashSet<>();
        List<Map<String, Object>> scheduleInfoData = dao.getScheduleInfo();
        String today = SIMPLE_DATE_FORMAT.format(new Date());

        scheduleInfoData.forEach(row -> {
            String createdDate = (String) row.get("CreatedOn");
            org.nexial.core.variable.Date date = new org.nexial.core.variable.Date();
            if (Float.parseFloat(date.diff(today, createdDate, "DAY")) > properties.getAutoPurgePeriod()) {

                String project = (String) row.get("ProjectId");
                String scheduleInfoId = (String) row.get("Id");
                String runId = (String) row.get("RunId");
                logger.error("Execution data for runId " + runId + " for project " + project + " started auto purging");

                try {
                    // need to consider return type
                    dao.deleteExecutionData(scheduleInfoId, project, runId);
                } catch (Exception e) {
                    logger.error("Execution data for runId " + runId + " is not auto purged");
                    return;
                }

                String prefix = StringUtils.substringAfter(runId, ".");
                projects.add(project + (StringUtils.isEmpty(prefix) ? "" : ".") + prefix);
            }
        });

        projects.forEach(projectData -> {
            String project = StringUtils.substringBefore(projectData, ".");
            String prefix = StringUtils.substringAfter(projectData, ".");
            try {
                ProcessExecutionService service = factory.getBean(ProcessExecutionService.class);
                service.setProject(project);
                service.setPrefix(prefix);
                service.createSummaryOutput();
            } catch (Exception e) {
                logger.error("The generating summary process for project='" + dao.getProjectName(project) +
                             " and prefix='" + dao.getDashboardName(project, prefix) + "' has been timed out");
                e.printStackTrace();
            }
        });
    }

    // need to be explored
    public Response purgeWithDate(String date, Response response) {
        try {
            purgWithDate(date);
        } catch (Exception e) {
            response.setReturnCode(500);
            response.setStatusText("FAIL");
            response.setDetailMessage(e.getMessage());
        }
        return response;
    }

    // project and run id needed
    public Response purgeWithRunId(String project, String runId, Response response) {
        try {
            purgeWithRunID(project, runId);
        } catch (Exception e) {
            response.setReturnCode(500);
            response.setStatusText("FAIL");
            response.setDetailMessage(e.getMessage());
        }
        return response;
    }

    public void purgeWithRunID(String projectName, String runId) throws Exception {
        List<Map<String, Object>> scheduleInfoWithRunId = dao.getScheduleInfoWithRunId(projectName, runId);

        for (Map<String, Object> row : scheduleInfoWithRunId) {
            String project = (String) row.get("ProjectId");
            String scheduleInfoId = (String) row.get("Id");
            String prefix = StringUtils.substringAfter(runId, ".");
            dao.deleteExecutionData(scheduleInfoId, project, runId);
            ProcessExecutionService service = factory.getBean(ProcessExecutionService.class);
            service.setProject(project);
            service.setPrefix(prefix);
            service.createSummaryOutput();
        }
    }

    private void purgWithDate(String date) {
        List<Map<String, Object>> scheduleInfoDates = dao.getScheduleInfo();
        for (Map<String, Object> row : scheduleInfoDates) {
            String createdOn = (String) row.get("CreatedOn");
            // Need to use another option
            createdOn = SIMPLE_DATE_FORMAT1.format(new Date(createdOn));
            if (StringUtils.compare(date, createdOn) < 1) {
                String project = (String) row.get("ProjectName");
                String runId = (String) row.get("RunId");
                String id = (String) row.get("Id");
                try {
                    dao.deleteExecutionData(id, project, runId);
                } catch (Exception e) {
                    logger.error("Execution data for runId " + runId + " is not auto purged");
                    continue;
                }

            }
        }
    }
}