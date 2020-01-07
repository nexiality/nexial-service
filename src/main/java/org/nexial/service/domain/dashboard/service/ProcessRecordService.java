package org.nexial.service.domain.dashboard.service;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.service.domain.ApplicationProperties;
import org.nexial.service.domain.dashboard.IFileStorage;
import org.nexial.service.domain.dbconfig.ApplicationDao;
import org.nexial.service.domain.utils.UtilityHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import static org.nexial.core.NexialConst.DEF_CHARSET;
import static org.nexial.service.domain.utils.Constants.SIMPLE_DATE_FORMAT;
import static org.nexial.service.domain.utils.Constants.Status.*;

@Service("service")
@Scope("prototype")
public class ProcessRecordService {
    private static final Logger logger = LoggerFactory.getLogger(ProcessRecordService.class);
    private static final HashMap<String, Boolean> THREAD_STATUS = new HashMap<>();

    private ApplicationDao dao;
    private final ApplicationProperties properties;
    private final BeanFactory factory;

    public ProcessRecordService(ApplicationDao dao, ApplicationProperties properties, BeanFactory factory) {
        this.dao = dao;
        this.properties = properties;
        this.factory = factory;
    }

    public int getWorkerCount(String project, String prefix) { return dao.getWorkerCount(project, prefix); }

    public ApplicationProperties getProperties() { return properties; }

    public List<Map<String, Object>> getReceivedProjects() { return dao.getReceivedProjects(); }

    public void interruptThread(String project, String prefix) {
        String workerThread = dao.getWorkerId(project, prefix);
        if (StringUtils.isNotBlank(workerThread) && THREAD_STATUS.containsKey(workerThread)) {
            THREAD_STATUS.put(workerThread, true);
            logger.info("The thread must be interrupted at time= " + SIMPLE_DATE_FORMAT.format(new Date()));
        }
    }

    @Async("threadPoolTaskExecutor")
    public CompletableFuture<Boolean> generateSummary(String project, String prefix) {
        String threadName = Thread.currentThread().getName();
        logger.info("--------" + project + "-------[" + threadName + "]");
        dao = factory.getBean(ApplicationDao.class);

        THREAD_STATUS.put(threadName, false);
        dao.updateWorkerInfo(project, prefix, threadName);
        try {
            ProcessExecutionService service = factory.getBean(ProcessExecutionService.class);
            service.setProject(project);
            service.setPrefix(prefix);
            while (true) {
                List<Map<String, Object>> runIdList = dao.getRunIds(project, prefix);
                if (runIdList.isEmpty()) { break; }
                if (processExecutionDetailInfo(service, runIdList)) { break; }
            }
            service.createSummaryOutput();

        } catch (Exception e) {
            logger.error("The generating summary process for project='" + project +
                         " and prefix='" + prefix + "' has been timed out");
            e.printStackTrace();
        } finally {
            dao.deleteWorkerInfo(project, prefix);
            THREAD_STATUS.remove(threadName);
        }
        return CompletableFuture.completedFuture(true);
    }

    public boolean processExecutionDetailInfo(ProcessExecutionService service, List<Map<String, Object>> runIdList) {
        // To make sure all records  status should be changed before another schedule call
        for (Map<String, Object> row : runIdList) {
            String runId = (String) row.get("RunId");
            String outputPath = dao.getExecutionOutputPath(runId);
            outputPath = UtilityHelper.getPath(outputPath, false);
            //Todo change path store full path
            try {
                dao.updateScheduleInfoStatus(runId, INPROGRESS);
                String content = FileUtils.readFileToString(new File(outputPath), DEF_CHARSET);
                if (content != null) { service.processJsonData(content); }
            } catch (Exception e) {
                // e.printStackTrace();
                dao.updateScheduleInfoStatus(runId, FAILED);
                return true;
            } finally {
                factory.getBean(properties.getStorageLocation(), IFileStorage.class).deleteFolders(outputPath);
            }
            dao.updateScheduleInfoStatus(runId, COMPLETED);

            String threadName = Thread.currentThread().getName();
            if (THREAD_STATUS.containsKey(threadName) && THREAD_STATUS.get(threadName)) {
                logger.info("This thread for run id " + runId + " is interrupted; Time=" +
                            SIMPLE_DATE_FORMAT.format(new Date()));
                return true;
            }
        }
        return false;
    }
}
