package org.nexial.service.domain.dashboard.model;

import java.io.Serializable;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "DASHBOARD_LOG")
public class DashboardLog implements Serializable {

    private static final long serialVersionUID = 3094783521102453914L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;
    private String project;
    private String prefix;
    private String path;
    private String status;
    private String createdon;
    private String modifiedon;
    private String workerid;
    private String key;

    public String getKey() { return key; }

    public void setKey(String key) { this.key = key; }

    public String getWorkerid() { return workerid; }

    public void setWorkerid(String workerid) { this.workerid = workerid; }

    public int getId() {return id; }

    public void setId(int id) {
        this.id = id;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getPrefix() { return prefix; }

    public void setPrefix(String prefix) { this.prefix = prefix; }

    public String getPath() {
        return path;
    }

    public void setPath(String path) { this.path = path; }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedon() { return createdon; }

    public void setCreatedon(String createdon) { this.createdon = createdon; }

    public String getModifiedon() { return modifiedon; }

    public void setModifiedon(String modifiedon) { this.modifiedon = modifiedon; }

}
