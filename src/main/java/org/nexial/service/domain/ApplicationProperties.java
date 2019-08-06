package org.nexial.service.domain;

import org.springframework.stereotype.Component;

import static org.nexial.service.domain.utils.Constants.*;

@Component
public class ApplicationProperties extends ReloadApplicationProperties {

    public String getOutputCloudBase() { return environment.getProperty(CLOUD_AWS_OUTPUTBASE); }

    public String getArtifactCloudBase() { return environment.getProperty(CLOUD_AWS_ARTIFACTBASE); }

    public String getAccessKey() { return environment.getProperty(CLOUD_AWS_ACCESSKEY); }

    public String getSecretKey() { return environment.getProperty(CLOUD_AWS_SECRETKEY); }

    public String getRegion() { return environment.getProperty(CLOUD_AWS_REGION); }

    public String getResourceConfigCountPath() { return environment.getProperty(CLOUD_AWS_RESOURCE_CONFIG_PATH); }

    public String getLocalProjectsListPath() { return environment.getProperty(LOCAL_PROJECTSLIST_JSON); }

    public String getLocalProjectSummaryPath() { return environment.getProperty(LOCAL_SUMMARYOUTPUT_JSON); }

    public String getLocalResourceConfigPath() { return environment.getProperty(LOCAL_RESOURCE_CONFIG_JSON); }

    public String getWorkerTimeout() { return environment.getProperty(EXECUTOR_WORKER_THREAD_TIMEOUT); }

}
