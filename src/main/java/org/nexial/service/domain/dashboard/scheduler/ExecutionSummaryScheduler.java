package org.nexial.service.domain.dashboard.scheduler;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.nexial.commons.utils.DateUtility;
import org.nexial.service.domain.dashboard.service.ProcessRecordService;
import org.nexial.service.domain.dbconfig.SQLiteManager;
import org.nexial.service.domain.utils.LoggerUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import static org.nexial.service.domain.utils.Constants.DATE_TIME_FORMAT;
import static org.nexial.service.domain.utils.Constants.Status.*;
import static org.nexial.service.domain.utils.Constants.TIME_OUT;

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

    @Scheduled(fixedRate = 6000)
    private void summaryScheduler() {
        //Todo  use Spring functionality and configure through xml
        LoggerUtils.info("Summary Scheduler called " + DateUtility.format(System.currentTimeMillis()));
        List<Map<String, Object>> projectList = sqLiteManager.selectForList("SQL_SELECT_SCHEDULE_INFO",
                                                                            new Object[]{RECEIVED});
        Map<ProjectMeta, CompletableFuture<Boolean>> completableFutures = new ConcurrentHashMap<>();
        for (Map<String, Object> row : projectList) {
            //check whether there is a worker thread is present or not if yes dont add thoser project/prefix
            int count = (int) sqLiteManager.selectForObject("SQL_SELECT_COUNT_WORKER_INFO",
                                                            new Object[]{row.get("ProjectName"), row.get("Prefix")},
                                                            Integer.class);
            if (count == 0) {
                String projectName = (String) row.get("ProjectName");
                String prefix = (String) row.get("Prefix");
                CompletableFuture<Boolean> completableFuture = processRecordService.generateSummary(projectName,
                                                                                                    prefix);
                LoggerUtils.info("--------" + projectName + "-----Started at ----" + new Date().getTime());
                completableFutures.put(new ProjectMeta(projectName, prefix, new Date().getTime()), completableFuture);
            }
        }
        while (completableFutures.size() > 0) {
            for (Entry<ProjectMeta, CompletableFuture<Boolean>> entry : completableFutures.entrySet()) {
                ProjectMeta projectMeta = entry.getKey();
                CompletableFuture<Boolean> completableFuture = entry.getValue();
                if (projectMeta != null && completableFuture != null) {
                    String projectName = projectMeta.getProject();
                    String prefix = projectMeta.getPrefix();
                    Long startTime = projectMeta.getStartTime();

                    if ((new Date().getTime() - startTime) > TIME_OUT) {

                        String dateNow = new SimpleDateFormat(DATE_TIME_FORMAT).format(new Date());
                        if (completableFuture.isDone() && !completableFuture.isCancelled()) {
                            sqLiteManager.updateData("SQL_UPDATE_SCHEDULE_INFO_STATUS_COMPLETED",
                                                     new Object[]{COMPLETED, dateNow, projectName,
                                                                  prefix, INPROGRESS});
                        } else {
                            interruptThread(projectName, prefix);
                            completableFuture.cancel(true);
                            sqLiteManager.updateData("SQL_UPDATE_SCHEDULE_INFO_STATUS_FAILED",
                                                     new Object[]{FAILED, dateNow, projectName,
                                                                  prefix});

                        }

                        completableFutures.remove(projectMeta);
                        sqLiteManager.deleteData("SQL_DELETE_WORKER_INFO", new Object[]{projectName, prefix});
                    }
                }
            }
        }
    }

    private void interruptThread(String projectName, String prefix) {
        String WorkerId = (String) sqLiteManager.selectForObject("SQL_SELECT_WORKER_INFO",
                                                                 new Object[]{projectName,
                                                                              prefix}, String.class);

        Thread.getAllStackTraces().keySet().stream()
              .filter(t -> t.getName().equals(WorkerId)).forEach(Thread::interrupt);
    }
}
