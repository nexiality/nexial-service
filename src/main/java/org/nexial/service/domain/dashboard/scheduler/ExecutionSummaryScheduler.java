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

    @Scheduled(fixedRate = 1000000)
    private void summaryScheduler() {
        //Todo  use Spring functionality and configure through xml
        logger.info("Summary Scheduler called " + DateUtility.format(System.currentTimeMillis()));
        List<Map<String, Object>> projectList = beanFactory.getBean(ProcessRecordService.class).getReceivedProjects();
        Map<ProcessRecordService, CompletableFuture<Boolean>> completableFutures = new ConcurrentHashMap<>();
        for (Map<String, Object> row : projectList) {
            //check whether there is a worker thread is present or not if yes dont add those project/prefix
            ProcessRecordService processRecordService = beanFactory.getBean(ProcessRecordService.class);
            String projectName = (String) row.get("ProjectName");
            String prefix = (String) row.get("Prefix");
            processRecordService.setProject(projectName);
            processRecordService.setPrefix(prefix);
            processRecordService.setStartTime(System.currentTimeMillis());
            int count = processRecordService.getWorkerCount();

            if (count == 0) {
                CompletableFuture<Boolean> completableFuture = processRecordService.generateSummary();

                logger.info("--------" + projectName + "-----Started at ----" + new Date().getTime());
                // todo need to think new logic may be ProcessRecordService as key
                completableFutures.put(processRecordService, completableFuture);
            }
        }
        while (completableFutures.size() > 0) {
            for (Entry<ProcessRecordService, CompletableFuture<Boolean>> entry : completableFutures.entrySet()) {
                ProcessRecordService processRecord = entry.getKey();
                CompletableFuture<Boolean> completableFuture = entry.getValue();
                if (processRecord != null && completableFuture != null) {

                    if ((new Date().getTime() - processRecord.getStartTime()) > TIME_OUT) {
                        if (!completableFuture.isDone() || completableFuture.isCancelled()) {
                            processRecord.interruptThread();
                            completableFuture.cancel(true);
                        }
                        completableFutures.remove(processRecord);
                    }
                }
            }
        }
    }
}
