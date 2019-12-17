package org.nexial.service.domain.dashboard.service;

public class ProjectMeta {
    private String project;
    private String prefix;
    private long startTime;

    public ProjectMeta(String project, String prefix, long startTime) {
        this.project = project;
        this.prefix = prefix;
        this.startTime = startTime;
    }

    public String getProject() { return project; }

    public void setProject(String project) { this.project = project; }

    public String getPrefix() { return prefix; }

    public void setPrefix(String prefix) { this.prefix = prefix; }

    public long getStartTime() { return startTime; }
}
