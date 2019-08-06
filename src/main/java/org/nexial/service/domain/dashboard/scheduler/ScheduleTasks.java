package org.nexial.service.domain.dashboard.scheduler;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nexial.core.NexialConst;
import org.nexial.service.domain.ApplicationProperties;
import org.nexial.service.domain.awsconfig.AWSConfiguration;
import org.nexial.service.domain.dashboard.model.DashboardLog;
import org.nexial.service.domain.dashboard.repository.DashboardLogRepository;
import org.nexial.service.domain.dashboard.services.RequestHandleService;
import org.nexial.service.domain.utils.UtilityHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import static org.nexial.service.domain.utils.Constants.*;

@Component
public class ScheduleTasks {
    private final RequestHandleService service;
    private final AWSConfiguration awsS3Client;
    private final DashboardLogRepository repository;
    private final ApplicationProperties properties;
    private static final Log logger = LogFactory.getLog(ScheduleTasks.class);

    private CompletionService<Boolean> worker = new ExecutorCompletionService<>(Executors.newFixedThreadPool(20));

    public ScheduleTasks(RequestHandleService service,
                         AWSConfiguration awsS3Client,
                         DashboardLogRepository repository, ApplicationProperties properties) {
        this.service = service;
        this.awsS3Client = awsS3Client;
        this.repository = repository;
        this.properties = properties;
    }

    @Scheduled(cron = "${config.scheduleTasks.summaryupload.cron}")
    public void fetchDashboardLogFromDB() throws IOException {
        List<DashboardLog> list = service.findByStatus(LOG_STATUSES[0]);
        list.forEach(dashboardLog -> repository.updateLogStatus(LOG_STATUSES[1],
                                                                dashboardLog.getPath(),
                                                                new SimpleDateFormat(DATE_TIME_FORMAT)
                                                                    .format(new Date())));
        if (!list.isEmpty()) {
            Map<String, List<String>> distProjects = list.stream().collect(
                Collectors.groupingBy(DashboardLog::getKey,
                                      Collectors.mapping(DashboardLog::getPath, Collectors.toList())));
            Collection<S3Cloud> taskThreads = new ArrayList<>();
            distProjects.forEach((k, v) -> taskThreads.add(new S3Cloud(v, repository, properties, awsS3Client)));
            List<Future<Set<String>>> taskFutures = new ArrayList<>(taskThreads.size());
            for (Callable task : taskThreads) {
                taskFutures.add(worker.submit(task));
            }
            for (int count = 1; count <= taskFutures.size(); count++) {
                try {
                    Future<Boolean> taskResult = worker.poll(NumberUtils.toInt(properties.getWorkerTimeout()),
                                                             TimeUnit.SECONDS);
                    if (taskResult == null) {

                        logger.info(new SimpleDateFormat(DATE_TIME_FORMAT).format(new Date()) +
                                    " worker task timeout ");

                        // So lets cancel the first futures we find that haven't completed
                        for (Future taskFuture : taskFutures) {
                            if (!taskFuture.isDone()) {
                                List<String> failedRecords = (List<String>) taskFuture.get();
                                taskFuture.cancel(true);
                                failedRecords.forEach(path -> repository.updateLogStatus(LOG_STATUSES[3],
                                                                                         path,
                                                                                         new SimpleDateFormat(
                                                                                             DATE_TIME_FORMAT)
                                                                                             .format(new Date())));

                                logger.info(" ==> Worker task " + count + " cancelled due to long process");
                                break;
                            }
                        }
                    } else {
                        if (taskResult.isDone() && !taskResult.isCancelled()) {
                            logger.info(new SimpleDateFormat(DATE_TIME_FORMAT).format(new Date()) +
                                        "Task completed");
                            for (Future taskFuture : taskFutures) {
                                if (taskFuture.isDone()) {
                                    List<String> completedRecords = (List<String>) taskFuture.get();
                                    completedRecords.forEach(path -> repository
                                                                         .updateLogStatus(LOG_STATUSES[2],
                                                                                          path,
                                                                                          new SimpleDateFormat(
                                                                                              DATE_TIME_FORMAT)
                                                                                              .format(new Date())));
                                }
                            }
                        } else {
                            logger.info(new SimpleDateFormat(DATE_TIME_FORMAT).format(new Date()) +
                                        "Worker task failed");
                        }
                    }
                } catch (InterruptedException e) {
                    logger.info("Interrupted");
                    logger.error(e.getMessage(), e);
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                } finally {
                    FileUtils.deleteDirectory(new File(NexialConst.TEMP +
                                                       StringUtils
                                                           .substringBefore(properties.getOutputCloudBase(),
                                                                            CLOUD_AWS_SEPARATOR)));
                }
            }
        }
    }

    @PostConstruct
    @Scheduled(cron = "${config.scheduleTasks.projectslist.cron}")
    private void getProjectList() {
        String path = properties.getOutputCloudBase();
        String projectPath = StringUtils.substringAfter(path, CLOUD_AWS_SEPARATOR);
        List<String> keys = awsS3Client.getAwsS3HelperObject().listFiles(path);
        if (!keys.isEmpty()) { updateProjectList(keys, projectPath); }
    }

    private void updateProjectList(List<String> keys, String projectPath) {
        Set<String> subProjectSet = new HashSet<>();
        HashMap<String, Set<String>> projectMap = new HashMap<>();
        Gson gson = new Gson();
        String REGEX_AWS_PROJECT_PATTERN = "((.*)?)(\\/summary\\_output\\.json$)";
        for (String key : keys) {
            Pattern p = Pattern.compile(projectPath + REGEX_AWS_PROJECT_PATTERN);
            Matcher m = p.matcher(key);
            if (m.find()) {
                UtilityHelper.getProjectList(subProjectSet, projectMap, m);
            }
        }
        String json = gson.toJson(projectMap);
        if (json != null) {
            UtilityHelper.uploadFileOnLocalPath(gson.fromJson(json, JsonObject.class),
                                                properties.getLocalProjectsListPath());
        }
    }

    @PostConstruct
    private void loadResourcesConfigToLocal() {
        try {
            String path = properties.getResourceConfigCountPath();
            FileUtils.writeByteArrayToFile(new File(properties.getLocalResourceConfigPath()),
                                           awsS3Client.getAwsS3HelperObject()
                                                      .copyFromS3(StringUtils
                                                                      .substringBefore(path, CLOUD_AWS_SEPARATOR),
                                                                  StringUtils.substringAfter(path, CLOUD_AWS_SEPARATOR),
                                                                  false));
        } catch (IOException e) {
            logger.error(e.getMessage());
        }
    }
}
