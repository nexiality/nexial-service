package org.nexial.service.domain.dashboard.scheduler;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.nexial.commons.utils.DateUtility;
import org.nexial.service.domain.dashboard.service.ProcessRecordService;
import org.nexial.service.domain.dashboard.service.ProjectMeta;
import org.nexial.service.domain.dashboard.service.PurgeExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static org.nexial.service.domain.utils.Constants.TIME_OUT;

@Component
public class ExecutionSummaryScheduler {
    private final BeanFactory beanFactory;
    private static final Logger logger = LoggerFactory.getLogger(ExecutionSummaryScheduler.class);

    public ExecutionSummaryScheduler(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Scheduled(fixedRateString = "${summary.schedule.time}")
    private void summaryScheduler() {
        //Todo  use Spring functionality and configure through xml
        logger.info("Summary Scheduler called " + DateUtility.format(System.currentTimeMillis()));
        List<Map<String, Object>> projectList = beanFactory.getBean(ProcessRecordService.class).getReceivedProjects();
        Map<ProjectMeta, CompletableFuture<Boolean>> completableFutures = new ConcurrentHashMap<>();

        for (Map<String, Object> row : projectList) {
            //check whether there is a worker thread is present or not if yes dont add those project/prefix
            ProcessRecordService processRecordService = beanFactory.getBean(ProcessRecordService.class);
            String project = (String) row.get("ProjectName");
            String prefix = (String) row.get("Prefix");
            long startTime = System.currentTimeMillis();
            int count = processRecordService.getWorkerCount(project, prefix);

            if (count == 0) {
                logger.info("--------" + project + "-----Started at ----" + new Date().getTime());
                CompletableFuture<Boolean> completableFuture = processRecordService.generateSummary(project, prefix);
                ProjectMeta meta = new ProjectMeta(project, prefix, startTime);
                completableFutures.put(meta, completableFuture);
            }
        }

        while (completableFutures.size() > 0) {
            for (Entry<ProjectMeta, CompletableFuture<Boolean>> entry : completableFutures.entrySet()) {
                ProjectMeta meta = entry.getKey();
                CompletableFuture<Boolean> completableFuture = entry.getValue();
                String project = meta.getProject();
                String prefix = meta.getPrefix();
                if (meta != null && completableFuture != null) {

                    if ((new Date().getTime() - meta.getStartTime()) > TIME_OUT) {
                        if (!completableFuture.isDone() || completableFuture.isCancelled()) {
                            beanFactory.getBean(ProcessRecordService.class).interruptThread(project, prefix);
                            completableFuture.cancel(true);
                        }
                        completableFutures.remove(meta);
                    }
                }
            }
        }
    }

    @Scheduled(cron = "${purge.schedule.time}")
    private void purgeExecution() {
        logger.info("Purge Scheduler started--- ");
        PurgeExecutionService service = beanFactory.getBean(PurgeExecutionService.class);
        service.autoPurging();
    }
}
