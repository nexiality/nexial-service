package org.nexial.service.domain.dashboard.scheduler;

import java.util.List;

public class StepData {
    private List<Object> stepParams;
    private List<List<Object>> stepLinkParams;
    private List<List<Object>> logsParams;

    public StepData(List<Object> stepParams, List<List<Object>> stepLinkParams, List<List<Object>> logsParams) {
        this.stepParams = stepParams;
        this.stepLinkParams = stepLinkParams;
        this.logsParams = logsParams;
    }

    public List<Object> getStepParams() { return stepParams; }

    public List<List<Object>> getStepLinkParams() { return stepLinkParams; }

    public List<List<Object>> getLogsParams() { return logsParams; }
}
