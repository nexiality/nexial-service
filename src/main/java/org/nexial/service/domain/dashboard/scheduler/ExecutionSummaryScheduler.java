package org.nexial.service.domain.dashboard.scheduler;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.DateUtility;
import org.nexial.service.domain.dashboard.service.ProcessRecordService;
import org.nexial.service.domain.dbconfig.SQLiteManager;
import org.nexial.service.domain.utils.LoggerUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import static org.nexial.service.domain.utils.Constants.DATE_TIME_FORMAT;
import static org.nexial.service.domain.utils.Constants.Status.*;

@Component
public class ExecutionSummaryScheduler {

    private final ProcessRecordService processRecordService;
    private final SQLiteManager sqLiteManager;
    private final ThreadPoolTaskExecutor threadPoolTaskExecutor;

    public ExecutionSummaryScheduler(ProcessRecordService processRecordService,
                                     SQLiteManager sqLiteManager,
                                     ThreadPoolTaskExecutor threadPoolTaskExecutor) {
        this.processRecordService = processRecordService;
        this.sqLiteManager = sqLiteManager;
        this.threadPoolTaskExecutor = threadPoolTaskExecutor;
    }

    @Scheduled(fixedRate = 600000)
    private void summaryScheduler() {
        //Todo  use Spring functionality and configure through xml
        LoggerUtils.info("Summary Scheduler called " + DateUtility.format(System.currentTimeMillis()));
        List<Map<String, Object>> projectList = sqLiteManager.selectForList("SQL_SELECT_SCHEDULEINFO",
                                                                            new Object[]{RECEIVED});
        Map<String, CompletableFuture<Boolean>> completableFutures = new ConcurrentHashMap<>();
        for (Map<String, Object> row : projectList) {
            //check whether there is a worker thread is present or not if yes dont add thoser project/prefix
            int count = (int) sqLiteManager.selectForObject("SQL_SELECT_COUNT_WORKERINFO",
                                                            new Object[]{row.get("ProjectName"), row.get("Prefix")},
                                                            Integer.class);
            if (count == 0) {
                String projectName = (String) row.get("ProjectName");
                String prefix = (String) row.get("Prefix");
                CompletableFuture<Boolean> completableFuture = processRecordService.generateSummary(projectName,
                                                                                                    prefix);
                LoggerUtils.info("--------" + projectName + "-----Started at ----" + new Date().getTime());
                completableFutures.put(projectName + "%" + prefix + "%" + new Date().getTime(),
                                       completableFuture);
            }
        }
        while (completableFutures.size() > 0) {
            for (Entry<String, CompletableFuture<Boolean>> entry : completableFutures.entrySet()) {
                String k = entry.getKey();
                CompletableFuture<Boolean> v = entry.getValue();
                if (k != null && v != null) {
                    String[] split = StringUtils.split(k, "%");
                    String projectName = null;
                    String prefix = null;
                    String startTime = null;
                    if (split.length == 3) {
                        projectName = split[0];
                        prefix = split[1];
                        startTime = split[2];
                    } else if (split.length == 2) {
                        projectName = split[0];
                        prefix = StringUtils.EMPTY;
                        startTime = split[1];
                    }
                    if ((startTime != null) && ((new Date().getTime() - Long.parseLong(startTime)) > 90000)) {

                        String dateNow = new SimpleDateFormat(DATE_TIME_FORMAT).format(new Date());
                        if (v.isDone() && !v.isCancelled()) {
                            sqLiteManager.updateData("SQL_UPDATE_SCHEDULEINFO_STATUS_COMPLETED",
                                                     new Object[]{COMPLETED, dateNow, projectName,
                                                                  prefix, INPROGRESS});
                        } else {
                            v.cancel(true);
                            sqLiteManager.updateData("SQL_UPDATE_SCHEDULEINFO_STATUS_FAILED",
                                                     new Object[]{FAILED, dateNow, projectName,
                                                                  prefix});

                        }

                        //completableFutures.values().remove(v);
                        completableFutures.remove(k);
                        sqLiteManager.deleteData("SQL_DELETE_WORKERINFO",
                                                 new Object[]{projectName, prefix});
                    }
                }
            }
        }
    }
}
