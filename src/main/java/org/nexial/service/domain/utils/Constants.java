package org.nexial.service.domain.utils;

import java.text.SimpleDateFormat;

public final class Constants {
    public static final String SUMMARY_OUTPUT_FILE = "/summary_output.json";
    public static final String DATE_TIME_FORMAT = "MM/dd/YYYY hh:mm:ss";
    ;
    public static final String TIMESTAMP_REGEX = "^.*(\\d{4})(\\d{2})(\\d{2})_(\\d{2})(\\d{2})(\\d{2})$";
    // external configuration
    public static final String STORAGE_LOCATION = "config.storage.location";
    // aws related

    public static final String CLOUD_AWS_OUTPUTBASE = "config.cloud.outputCloudBase";
    public static final String CLOUD_AWS_ARTIFACTBASE = "config.cloud.artifactCloudBase";
    public static final String CLOUD_AWS_SECRETKEY = "config.cloud.secretKey";
    public static final String CLOUD_AWS_ACCESSKEY = "config.cloud.accessKey";
    public static final String CLOUD_AWS_REGION = "config.cloud.region";
    public static final String EXECUTOR_WORKER_THREAD_TIMEOUT = "config.executor.thread.timeout.seconds";
    public static final String CLOUD_AWS_SEPARATOR = "/";
    public static final String LOCAL_ARTIFACTS_PATH = "config.local.artifactsPath";
    public static final String LOCAL_EXECUTION_SUMMARY_PATH = "config.local.executionSummaryPath";

    public enum Status {RECEIVED, INPROGRESS, COMPLETED, FAILED}
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
    public static final String RESULTS = "results";

    public static final Long TIME_OUT = 300000L;

    public static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat(DATE_TIME_FORMAT);

}