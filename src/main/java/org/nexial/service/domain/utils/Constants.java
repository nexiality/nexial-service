package org.nexial.service.domain.utils;

import static java.io.File.separator;

public final class Constants {
    public static final String SUMMARY_OUTPUT_FILE = "/summary_output.json";
    public static final String HISTORY_SUMMARY_OUTPUT_FILE = "/history-summary_output.json";
    public static final String PROJECT_SPECIFIC_CONFIG_FILE = "/config.json";
    public static final String[] LOG_STATUSES = {"Received", "In Progress", "Completed", "Fail"};
    public static final String DATE_TIME_FORMAT = "YYYY-MM-DD hh:mm:ss";

    public static final String TEST_SUMMARY_OUTPUT_FILE = separator + "summary_output.json";
    public static final String TEST_HISTORY_SUMMARY_OUTPUT_FILE = separator + "history-summary_output.json";
    public static final int DEFAULT_EXECOUNT = 90;

    // aws related

    public static final String CLOUD_AWS_OUTPUTBASE = "config.cloud.outputCloudBase";
    public static final String CLOUD_AWS_ARTIFACTBASE = "config.cloud.artifactCloudBase";
    public static final String CLOUD_AWS_SECRETKEY = "config.cloud.secretKey";
    public static final String CLOUD_AWS_ACCESSKEY = "config.cloud.accessKey";
    public static final String CLOUD_AWS_REGION = "config.cloud.region";
    public static final String CLOUD_AWS_RESOURCE_CONFIG_PATH = "config.cloud.resourceConfigCloudBase";
    public static final String LOCAL_PROJECTSLIST_JSON = "config.local.projectsListPath";
    public static final String LOCAL_SUMMARYOUTPUT_JSON = "config.local.summaryOutputPath";
    public static final String LOCAL_RESOURCE_CONFIG_JSON = "config.local.resourcesConfigPath";
    public static final String EXECUTOR_WORKER_THREAD_TIMEOUT = "config.executor.thread.timeout.seconds";
    public static final String CLOUD_AWS_SEPARATOR = "/";
    // external configuration
    public static final String CONFIGURATION_PATH = "config.location";
    public static final String CONFIGURATION_NAME = "additionalConfigurations";
    public static final String CONFIGURATION_CHANGE_MESSAGE = "Configuration changed and new properties are loaded";
    // Execution-summary-output
    public static final String NAME = "name";
    public static final String START_TIME = "startTime";
    public static final String END_TIME = "endTime";
    public static final String FAIL_COUNT = "failCount";
    public static final String PASS_COUNT = "passCount";
    public static final String TOTAL_STEPS = "totalSteps";
    public static final String REFERENCE_DATA = "referenceData";
    public static final String PLAN = "plan";
    public static final String SCRIPT_RESULTS = "scriptResults";
    public static final String NESTED_EXECUTIONS = "nestedExecutions";
    public static final String EXE_COUNT = "execCount";
    public static final String RESULTS = "results";

}