package org.nexial.service.domain;

import org.nexial.service.domain.utils.UtilityHelper;
import org.springframework.stereotype.Component;

import static org.nexial.service.domain.utils.Constants.*;

@Component
public class ApplicationProperties extends ReloadApplicationProperties {

    public String getOutputCloudBase() { return environment.getProperty(CLOUD_AWS_OUTPUTBASE); }

    public String getArtifactCloudBase() { return environment.getProperty(CLOUD_AWS_ARTIFACTBASE); }

    public String getAccessKey() { return environment.getProperty(CLOUD_AWS_ACCESSKEY); }

    public String getSecretKey() { return environment.getProperty(CLOUD_AWS_SECRETKEY); }

    public String getRegion() { return environment.getProperty(CLOUD_AWS_REGION); }

    public String getLocalArtifactsPath() {
        return UtilityHelper.getPath(environment.getProperty(LOCAL_ARTIFACTS_PATH), true);
    }

    public String getStorageLocation() { return environment.getProperty(STORAGE_LOCATION); }

    public String getLocalExecutionSummaryPath() {
        return UtilityHelper.getPath(environment.getProperty(LOCAL_EXECUTION_SUMMARY_PATH), true);
    }
}
