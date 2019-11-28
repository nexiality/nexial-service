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

    public String getStorageLocation() { return environment.getProperty(STORAGE_LOCATION); }

    public String getArtifactPathPrefix() { return environment.getProperty(LOCAL_PATH_PREFIX); }

    public String getArtifactPath() { return environment.getProperty(LOCAL_ARTIFACTS_PATH); }

    public String getSummaryPath() { return environment.getProperty(LOCAL_EXECUTION_SUMMARY_PATH); }

    public String getLocalArtifactsPath() {
        return UtilityHelper.getPath(getArtifactPathPrefix() + getArtifactPath(), true);
    }

    public String getLocalExecutionSummaryPath() {
        return UtilityHelper.getPath(getArtifactPathPrefix() + getSummaryPath(), true);
    }

    public String getLocalAddress() {
        return "http://" + environment.getProperty(SERVER_ADDRESS) + ":" +
               environment.getProperty(SERVER_PORT) + PATH_SEPARATOR;
    }

    public Float getAutoPurgePeriod() {
        // handle parse error
        return Float.parseFloat(environment.getProperty("config.purge.period"));
    }
}
