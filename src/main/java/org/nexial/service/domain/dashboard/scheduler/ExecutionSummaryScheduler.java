package org.nexial.service.domain.dashboard.scheduler;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.nexial.commons.utils.DateUtility;
import org.nexial.service.domain.dashboard.service.ProcessRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static org.nexial.service.domain.utils.Constants.TIME_OUT;

@Component
public class ExecutionSummaryScheduler {
    private final ProcessRecordService processRecordService;
    private static final Logger logger = LoggerFactory.getLogger(ExecutionSummaryScheduler.class);

    public ExecutionSummaryScheduler(ProcessRecordService processRecordService) {
        this.processRecordService = processRecordService;
    }

    @Scheduled(fixedRate = 400000)
    private void summaryScheduler() {
        //Todo  use Spring functionality and configure through xml
        logger.info("Summary Scheduler called " + DateUtility.format(System.currentTimeMillis()));
        List<Map<String, Object>> projectList = processRecordService.getReceivedProjects();
        Map<ProjectMeta, CompletableFuture<Boolean>> completableFutures = new ConcurrentHashMap<>();
        for (Map<String, Object> row : projectList) {
            //check whether there is a worker thread is present or not if yes dont add thoser project/prefix
            String projectName = (String) row.get("ProjectName");
            String prefix = (String) row.get("Prefix");
            processRecordService.setProject(projectName);
            processRecordService.setPrefix(prefix);
            int count = processRecordService.getWorkerCount();

            if (count == 0) {
                CompletableFuture<Boolean> completableFuture = processRecordService.generateSummary();

                logger.info("--------" + projectName + "-----Started at ----" + new Date().getTime());
                // todo need to think new logic may be ProcessRecordService as key
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
                        if (!completableFuture.isDone() || completableFuture.isCancelled()) {
                            processRecordService.interruptThread(projectName, prefix);
                            completableFuture.cancel(true);
                        }
                        completableFutures.remove(projectMeta);
                    }
                }
            }
        }
    }
}
