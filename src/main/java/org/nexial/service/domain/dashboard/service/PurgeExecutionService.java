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
import org.springframework.stereotype.Service;

import static org.nexial.service.domain.utils.Constants.SIMPLE_DATE_FORMAT;
import static org.nexial.service.domain.utils.Constants.SIMPLE_DATE_FORMAT1;

@Service
public class PurgeExecutionService {
    private static final Logger logger = LoggerFactory.getLogger(PurgeExecutionService.class);
    private final ApplicationProperties properties;
    private final ApplicationDao dao;
    private final ProcessRecordService processRecordService;

    public PurgeExecutionService(ApplicationProperties properties, ProcessRecordService processRecordService,
                                 ApplicationDao dao) {
        this.properties = properties;
        this.processRecordService = processRecordService;
        this.dao = dao;
    }

    public void autoPurging() {
        Set<String> projects = new HashSet<>();
        List<Map<String, Object>> sql_select_schedule_info_dates = dao.getScheduleInfo();
        String today = SIMPLE_DATE_FORMAT.format(new Date());
        sql_select_schedule_info_dates.forEach(row -> {
            String createdDate = (String) row.get("CreatedOn");
            org.nexial.core.variable.Date date = new org.nexial.core.variable.Date();
            if (Float.parseFloat(date.diff(today, createdDate, "DAY")) > properties.getAutoPurgePeriod()) {

                String project = (String) row.get("ProjectName");
                String id = (String) row.get("Id");
                String runId = (String) row.get("RunId");
                deleteExecutionData(id, project, runId);
                String prefix = StringUtils.substringAfter(runId, ".");
                projects.add(project + (StringUtils.isEmpty(prefix) ? "" : ".") + prefix);
            }
        });
        projects.forEach(projectData -> {
            String project = StringUtils.substringBefore(projectData, ".");
            String prefix = StringUtils.substringAfter(projectData, ".");
            processRecordService.setProject(project);
            processRecordService.setPrefix(prefix);
            processRecordService.createSummaryOutput();

        });
    }

    // need to be explore
    public Response purgeWithDate(String beforeDate, Response response) {
        try {
            List<Map<String, Object>> scheduleInfoDates = dao.getScheduleInfo();
            scheduleInfoDates.forEach(row -> {
                String createdOn = (String) row.get("CreatedOn");
                createdOn = SIMPLE_DATE_FORMAT1.format(new Date(createdOn));
                if (StringUtils.compare(beforeDate, createdOn) < 1) {
                    String project = (String) row.get("ProjectName");
                    String runId = (String) row.get("RunId");
                    String id = (String) row.get("Id");
                    deleteExecutionData(id, project, runId);
                }
            });
        } catch (Exception e) {
            response.setReturnCode(500);
            response.setStatusText("Fail");
            response.setDetailMessage(e.getMessage());
        }
        return response;
    }

    // project and runid needed
    public Response purgeWithRunId(String runId, Response response) {
        List<Map<String, Object>> scheduleInfoWithRunId = dao.getScheduleInfoWithRunId(runId);

        scheduleInfoWithRunId.forEach(row -> {
            String project = (String) row.get("ProjectName");
            String id = (String) row.get("Id");
            deleteExecutionData(id, project, runId);
            String prefix = StringUtils.substringAfter(runId, ".");
            processRecordService.setProject(project);
            processRecordService.setPrefix(prefix);
            processRecordService.createSummaryOutput();
        });
        return response;
    }

    // todo use transaction
    public void deleteExecutionData(String id, String project, String runId) {
        dao.deleteExecutionData(id, project, runId);
    }
}