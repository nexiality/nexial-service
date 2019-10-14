package org.nexial.service.domain.dashboard.scheduler;

public class ProjectMeta {
    private final String project;
    private final String prefix;
    private final Long startTime;

    public ProjectMeta(String project, String prefix, Long startTime) {
        this.project = project;
        this.prefix = prefix;
        this.startTime = startTime;
    }

    public String getProject() { return project; }

    public String getPrefix() {
        return prefix;
    }

    public Long getStartTime() {
        return startTime;
    }
}
