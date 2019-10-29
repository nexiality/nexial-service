package org.nexial.service.domain.dashboard.scheduler;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class Activity {
    private String activityName;
    private int activitySeq;

    public Activity(String activityName, int activitySeq) {
        this.activityName = activityName;
        this.activitySeq = activitySeq;
    }

    @Override
    public String toString() {
        return "Activity{" +
               "activity=\"" + activityName + "\"," +
               "activitySeq=" + activitySeq +
               "}";
    }

    @Override
    public boolean equals(Object obj) {
        Activity activity = (Activity) obj;
        return StringUtils.equals(this.activityName, activity.activityName) && this.activitySeq == activity.activitySeq;
    }

    @Override
    public int hashCode() { return new Integer(this.activitySeq).hashCode(); }

    public static class StepData {
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
}
