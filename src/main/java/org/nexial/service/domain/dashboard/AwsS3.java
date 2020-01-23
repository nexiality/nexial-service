package org.nexial.service.domain.dashboard;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.nexial.core.aws.AwsS3Helper;
import org.nexial.service.domain.ApplicationProperties;
import org.nexial.service.domain.awsconfig.AWSConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static org.nexial.service.domain.utils.Constants.PATH_SEPARATOR;

@Component("aws")
public class AwsS3 implements IFileStorage {
    private static final Logger logger = LoggerFactory.getLogger(AwsS3.class);
    private final AWSConfiguration awsConfiguration;
    private final ApplicationProperties properties;

    public AwsS3(AWSConfiguration awsConfiguration, ApplicationProperties properties) {
        this.awsConfiguration = awsConfiguration;
        this.properties = properties;
    }

    @Override
    public String uploadArtifact(File file, String project, String runId, String folderPath) {
        String artifactPath = properties.getArtifactCloudBase() + project + PATH_SEPARATOR + runId;
        artifactPath += folderPath != null ? PATH_SEPARATOR + folderPath : "";
        return uploadToCloud(file, artifactPath, false);
    }

    @Override
    public void uploadSummary(File file, String project) {
        String summaryOutputKey = properties.getOutputCloudBase() + project;
        uploadToCloud(file, summaryOutputKey, true);
    }

    @Override
    public void deleteFolders(String path) {
        File file = new File(path);
        if (!file.exists()) { return; }
        try {
            FileUtils.cleanDirectory(file.getParentFile());
        } catch (IOException e) {
            logger.error("Unable to delete the folder", e);
        }
    }

    @Override
    public String getSummaryUrl() {
        return "https://s3." + properties.getRegion() + ".amazonaws.com/" + properties.getCloudResourcePath() +
               "/executionsummary.html?project=";
    }
    @Nullable
    private String uploadToCloud(File file, String summaryOutputKey, boolean removeLocal) {
        if (!file.exists()) { return null; }
        String returnUrl = null;
        AwsS3Helper helper = awsConfiguration.getAwsS3HelperObject();
        try {
            returnUrl = helper.importToS3(file, summaryOutputKey, removeLocal);
        } catch (IOException e) {
            logger.error("upload to cloud is failed", e);
        }
        return returnUrl;
    }
}
