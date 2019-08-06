package org.nexial.service.domain.dashboard.scheduler;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nexial.commons.utils.FileUtil;
import org.nexial.core.NexialConst;
import org.nexial.core.aws.AwsS3Helper;
import org.nexial.service.domain.ApplicationProperties;
import org.nexial.service.domain.awsconfig.AWSConfiguration;
import org.nexial.service.domain.dashboard.repository.DashboardLogRepository;
import org.nexial.service.domain.utils.UtilityHelper;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import static org.apache.commons.io.FileUtils.readFileToString;
import static org.apache.commons.io.FileUtils.writeByteArrayToFile;
import static org.nexial.service.domain.utils.Constants.*;

class S3Cloud implements Callable<List<String>> {
    private List<String> paths;
    private final DashboardLogRepository repository;
    private final ApplicationProperties properties;
    private final AwsS3Helper helper;
    private static final Log logger = LogFactory.getLog(S3Cloud.class);

    private static final String REGEX_EXECUTION_SUMMARY =
        "(.*\\/)((.*\\.)?)(\\d{8}_\\d{6}\\/execution\\-summary\\.json$)";

    public S3Cloud(List<String> v,
                   DashboardLogRepository repository,
                   ApplicationProperties properties,
                   AWSConfiguration awsS3Client) {
        this.paths = v;
        this.repository = repository;
        this.properties = properties;
        this.helper = awsS3Client.getAwsS3HelperObject();
    }

    @Override
    public List<String> call() {
        JsonObject summaryOutputJsonObject = new JsonObject();
        JsonArray executionJsonArray = new JsonArray();
        String keyPath = null;
        //1 fetch data from all the paths
        for (String path : paths) {
            path = properties.getArtifactCloudBase() + path;
            byte[] bytes = getDataFromCloud(path);
            JsonObject executionSummary = getJsonData(path, bytes, true);
            /* 4) download existing summary-output.json and append json array to that file and remove from temp dir */
            String[] projectDetails = getProject(path);
            if (executionSummary != null) {
                repository.insertExecutionSummary(projectDetails[1],
                                                  projectDetails[2],
                                                  executionSummary.toString(),
                                                  new SimpleDateFormat(DATE_TIME_FORMAT).format(new Date()));
            }

            executionJsonArray.add(executionSummary);
            keyPath = path;
        }
        //2 check summary-output.json is there for the project if yes return that else return null
        String[] project = getProject(keyPath);
        repository.updateWorkerId(Thread.currentThread().getName(), project[0]);

        //check if project name already present in the file. if yes skip else add this to list
        //append outputCloudBase here and with project name
        //split the output cloud base value to get cloud summary - projects path
        String summaryOutputKey =
            properties.getOutputCloudBase() + project[0] + SUMMARY_OUTPUT_FILE;
        JsonObject existingData;

        /* Check if summary file is present in the local system */
        String localSummaryPath =
            properties.getLocalProjectSummaryPath() + project[0] + TEST_SUMMARY_OUTPUT_FILE;
        String localSummaryJson = readFileFromLocal(localSummaryPath);

        if (localSummaryJson == null) {
            byte[] bytes = getDataFromCloud(summaryOutputKey);
            existingData = getJsonData(summaryOutputKey, bytes, false);
        } else {
            existingData = new Gson().fromJson(localSummaryJson, JsonObject.class);
        }
        if (executionJsonArray.size() > 0) {
            if (existingData == null) {
                summaryOutputJsonObject.add(RESULTS, executionJsonArray);
            } else {
                summaryOutputJsonObject = updateExistingFileDataOnS3Bucket(executionJsonArray,
                                                                           existingData,
                                                                           summaryOutputKey, project[0]);
            }
            if (summaryOutputJsonObject != null) {
                createLocalFile(summaryOutputJsonObject, localSummaryPath);
                createFileOnS3Bucket(new File(localSummaryPath),
                                     StringUtils.substringBeforeLast(summaryOutputKey, CLOUD_AWS_SEPARATOR));
            }
        }
        return paths;
    }

    private String readFileFromLocal(String path) {
        String localJsonObject = null;
        File localPath = new File(path);
        if (!localPath.exists()) { return null; }
        try {
            localJsonObject = (readFileToString(localPath,
                                                NexialConst.DEF_FILE_ENCODING));
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return localJsonObject;
    }

    private void createLocalFile(JsonObject summaryOutputJsonObject, String path) {
        File filePath = new File(path);
        try {
            FileUtil.createNewFile(filePath, summaryOutputJsonObject.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private byte[] getDataFromCloud(String path) {
        String bucketName = StringUtils.substringBefore(path, CLOUD_AWS_SEPARATOR);
        String summaryPath = StringUtils.substringAfter(path, CLOUD_AWS_SEPARATOR);
        byte[] bytes = null;
        try {
            bytes = helper.copyFromS3(bucketName, summaryPath, false);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return bytes;
    }

    private JsonObject getJsonData(String path, byte[] bytes, boolean isArtifact) {
        JsonObject jsonData = null;
        String executionSummaryData;
        if (bytes == null) { return null; }
        File local = new File(NexialConst.TEMP + path);
        try {
            writeByteArrayToFile(local, bytes);
            executionSummaryData = readFileToString(new File(NexialConst.TEMP + path),
                                                    NexialConst.DEF_FILE_ENCODING);
            if (executionSummaryData == null) {return null;}

            if (isArtifact) {
                jsonData = UtilityHelper.createJson(executionSummaryData);
            } else {
                jsonData = new Gson().fromJson(executionSummaryData, JsonObject.class);
            }

        } catch (IOException e) {
            logger.error(e.getMessage());
        }
        return jsonData;
    }

    private JsonObject updateExistingFileDataOnS3Bucket(JsonArray content,
                                                        JsonObject oldData,
                                                        String key,
                                                        String projectName) {
        JsonArray oldJsonArray = oldData.getAsJsonObject().get(RESULTS).getAsJsonArray();
        int execCount = getCountFromConfig(key);
        JsonElement historyElement = null;
        if (oldJsonArray.size() == execCount) {
            // retrieve history element
            historyElement = oldJsonArray.get(0);
            oldJsonArray.remove(0);
        }
        oldJsonArray.addAll(content);
        JsonObject summaryOutputJsonObject = new JsonObject();
        summaryOutputJsonObject.add(RESULTS, oldJsonArray);
        /* First process history records */
        if (historyElement != null) {processHistoricalData(key, historyElement, projectName);}
        return summaryOutputJsonObject;
    }

    private void processHistoricalData(String key, JsonElement historyElement, String projectName) {
        //check file is exists in the local path
        //if yes get data from file else get the data from cloud(first time setup)
        key = StringUtils.replace(key, SUMMARY_OUTPUT_FILE, HISTORY_SUMMARY_OUTPUT_FILE);
        JsonObject existingData;
        JsonArray historyJsonArray = new JsonArray();
        historyJsonArray.add(historyElement);
        /*check whether history-summary file is present in the local system */
        String historySummaryPath =
            properties.getLocalProjectSummaryPath() + projectName + TEST_HISTORY_SUMMARY_OUTPUT_FILE;
        String localSummaryJson = readFileFromLocal(historySummaryPath);

        if (localSummaryJson == null) {
            byte[] bytes = getDataFromCloud(key);
            existingData = getJsonData(key, bytes, false);
        } else {
            existingData = new Gson().fromJson(localSummaryJson, JsonObject.class);
        }
        // if null create file and upload  otherwise update the array with json file data and upload it
        JsonObject summaryOutputJsonObject = new JsonObject();
        if (existingData == null) {
            summaryOutputJsonObject.add(RESULTS, historyJsonArray);
        } else {
            summaryOutputJsonObject = updateExistingFileDataOnS3Bucket(historyJsonArray.getAsJsonArray(),
                                                                       existingData,
                                                                       key, projectName);
        }
        if (summaryOutputJsonObject != null) {
            createLocalFile(summaryOutputJsonObject, historySummaryPath);
            createFileOnS3Bucket(new File(historySummaryPath), key);
        }
    }

    private int getCountFromConfig(String key) {
        key = StringUtils.replace(key, SUMMARY_OUTPUT_FILE, PROJECT_SPECIFIC_CONFIG_FILE);
        key = StringUtils.replace(key, HISTORY_SUMMARY_OUTPUT_FILE, PROJECT_SPECIFIC_CONFIG_FILE);
        JsonObject data;
        byte[] bytes = getDataFromCloud(key);
        data = getJsonData(key, bytes, false);
        if (data == null) { return getCountFromResourceFolder(); }
        return data.get(EXE_COUNT).getAsInt();
    }

    private int getCountFromResourceFolder() {
        JsonObject data;
        String jsonString = readFileFromLocal(properties.getLocalResourceConfigPath());
        if (jsonString == null) { return DEFAULT_EXECOUNT; }
        data = new Gson().fromJson(jsonString, JsonObject.class);
        return data.get(EXE_COUNT).getAsInt();
    }

    private void createFileOnS3Bucket(File source, String key) {
        try {
            helper.importToS3(source, key, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String[] getProject(String path) {
        Pattern p = Pattern.compile(properties.getArtifactCloudBase() + REGEX_EXECUTION_SUMMARY);
        Matcher m = p.matcher(path);
        String[] name = new String[3];
        if (m.find()) {
            String projectName = StringUtils.substringBefore(m.group(1), CLOUD_AWS_SEPARATOR);
            String subProjectName = StringUtils.substringBefore(m.group(2), ".");
            if (StringUtils.isEmpty(subProjectName)) {
                name[0] = projectName;
                name[1] = projectName;
            } else {
                name[0] = projectName + "." + subProjectName;
                name[1] = projectName;
                name[2] = subProjectName;
            }
        }
        return name;
    }
}
