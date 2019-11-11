package org.nexial.service.domain.dashboard.service;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.nexial.service.domain.ApplicationProperties;
import org.nexial.service.domain.dbconfig.ApplicationDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Service;

import static org.nexial.service.domain.utils.Constants.SIMPLE_DATE_FORMAT;

@Service
public class PurgeExecutionService {
    private static final Logger logger = LoggerFactory.getLogger(PurgeExecutionService.class);
    private final ApplicationProperties properties;
    private final ApplicationDao dao;

    public PurgeExecutionService(ApplicationProperties properties, BeanFactory beanFactory, ApplicationDao dao) {
        this.properties = properties;
        this.dao = dao;
    }

    public void autoPurging() {
        List<Map<String, Object>> sql_select_schedule_info_dates = dao.getScheduleInfo();
        String today = SIMPLE_DATE_FORMAT.format(new Date());
        sql_select_schedule_info_dates.forEach(row -> {
            String createdDate = (String) row.get("CreatedOn");

            org.nexial.core.variable.Date date = new org.nexial.core.variable.Date();
            if (Float.parseFloat(date.diff(today, createdDate, "DAY")) > properties.getAutoPurgePeriod()) {
                deleteExecutionData(row);
            }
        });
    }

    public boolean purgeWithRunId(String runId) {
        List<Map<String, Object>> sql_select_schedule_info_dates = dao.getScheduleInfoWithRunId(runId);
        sql_select_schedule_info_dates.forEach(this::deleteExecutionData);
        return true;
    }

    private void deleteExecutionData(Map<String, Object> row) {
        String project = (String) row.get("ProjectName");
        String runId = (String) row.get("RunId");
        dao.deleteScheduleInfoDetails((String) row.get("Id"));
        dao.deleteExecutionDetails(project, runId);
    }
}