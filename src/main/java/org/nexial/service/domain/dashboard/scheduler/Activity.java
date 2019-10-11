package org.nexial.service.domain.dashboard.scheduler;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

/*
public class HandleStepDetails {
    private Activity activity;
    private List<StepDetails> steps;

    public HandleStepDetails(Activity activity, List<StepDetails> steps) {
        this.activity = activity;
        this.steps = steps;
    }

    @Override
    public String toString() {
        return "HandleStepDetails{" +
               "activity=" + activity +
               ", steps=" + steps +
               '}';
    }

    @Override
    public boolean equals(Object obj) {
        HandleStepDetails act = (HandleStepDetails)obj;
        return this.activity.equals(act.activity);
    }

    @Override
    public int hashCode() { return this.activity.hashCode(); }

    public Activity getActivity() {
        return activity;
    }

    public List<StepDetails> getSteps() {
        return steps;
    }

*/
public class Activity {
    private String activity;
    private int activitySeq;

    public Activity(String activity, int activitySeq) {
        this.activity = activity;
        this.activitySeq = activitySeq;
    }

    @Override
    public String toString() {
        return "Activity{" +
               "activity='" + activity + '\'' +
               ", activitySeq=" + activitySeq +
               '}';
    }

    @Override
    public boolean equals(Object obj) {
        Activity act = (Activity) obj;
        return StringUtils.equals(this.activity, act.activity) && this.activitySeq == act.activitySeq;
    }

    @Override
    public int hashCode() { return new Integer(this.activitySeq).hashCode(); }

    public static class StepData {
        // private String stepStmt;
        private List<Object> stepParams;
        // private String stepLinkStmt;
        private List<List<Object>> stepLinkParams;
        // private String logsStmt;
        private List<List<Object>> logsParams;

        public StepData(List<Object> stepParams) {
            this.stepParams = stepParams;
        }

        public StepData(List<Object> stepParams, List<List<Object>> stepLinkParams, List<List<Object>> logsParams) {
            this.stepParams = stepParams;
            this.stepLinkParams = stepLinkParams;
            this.logsParams = logsParams;
        }

        /*public String getStepStmt() { return stepStmt; }

        public void setStepStmt(String stepStmt) { this.stepStmt = stepStmt; }*/

        public List<Object> getStepParams() { return stepParams; }

        public void setStepParams(List<Object> stepParams) { this.stepParams = stepParams; }

        /*public String getStepLinkStmt() { return stepLinkStmt; }

        public void setStepLinkStmt(String stepLinkStmt) { this.stepLinkStmt = stepLinkStmt; }
*/
        public List<List<Object>> getStepLinkParams() { return stepLinkParams; }

        public void setStepLinkParams(List<List<Object>> stepLinkParams) { this.stepLinkParams = stepLinkParams; }

  /*      public String getLogsStmt() { return logsStmt; }

        public void setLogsStmt(String logsStmt) { this.logsStmt = logsStmt; }
*/
        public List<List<Object>> getLogsParams() { return logsParams; }

        public void setLogsParams(List<List<Object>> logsParams) { this.logsParams = logsParams; }

    }
}

// }
