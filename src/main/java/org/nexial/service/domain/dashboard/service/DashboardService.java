package org.nexial.service.domain.dashboard.service;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.nexial.service.domain.dbconfig.ApplicationDao;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    private ApplicationDao dao;

    public DashboardService(ApplicationDao dao) {
        this.dao = dao;
    }

    public List<Map<String, Object>> fetchProjectList() {
        List<Map<String, Object>> projectList = dao.getProjectsList();
        if (projectList.isEmpty()) { return null;}
        return projectList;
    }

    public List<Map<String, Object>> fetchSuperDashboardList() {
        List<Map<String, Object>> projectList = dao.getSuperDashboardList();
        if (projectList.isEmpty()) { return null;}
        return projectList;
    }

    public List<Map<String, Object>> fetchDashboardList(String id) {
        List<Map<String, Object>> dashboardList = dao.getDashboardList(id);
        if (dashboardList.isEmpty()) { return null;}
        return dashboardList;
    }

    public void createProject(String project, String dashboard, String description) {
        dao.insertIntoProjectInfo(project, dashboard, description);
    }

    public void updateDashboard(String id, String name, String description, String projectId) {
        if (StringUtils.isEmpty(id)) {
            dao.insertDashboardInfo(name, description, projectId);
        } else { dao.updateProjectInfo(id, name, description); }
    }

    public List<Map<String, Object>> fetchSuperDashboardsList(String name) {
        List<Map<String, Object>> dashboardsList = dao.getSuperDashboardProjectList(name);
        if (dashboardsList.isEmpty()) { return null; }
        return dashboardsList;
    }

    public List<Map<String, Object>> getAllProjectsList() {
        List<Map<String, Object>> dashboardsList = dao.getAllProjectsList();
        if (dashboardsList.isEmpty()) { return null; }
        return dashboardsList;
    }

    public void deleteFromSuperDashboard(String name) {
        dao.deleteFromSuperDashboardByName(name);
    }

    public void updateSuperDashboard(Map<String, Object> data) {
        String id = (String) data.get("Id");
        String name = (String) data.get("Name");
        String description = (String) data.get("Description");
        List<Map<String, Object>> selectedProjectsList = (List) data.get("selectedProjectsList");
        if (StringUtils.isEmpty(name)) { return; }
        int maxPosition = dao.getMaxExecutionCount(name);
        if (id == null) {
            if (selectedProjectsList.isEmpty()) { return; }
            for (Map<String, Object> row : selectedProjectsList) {
                if (((Boolean) row.get("selected"))) {
                    maxPosition = maxPosition + 1;
                    dao.updateSuperDashboardProjects(null, name, description, maxPosition, row);
                }
            }
        } else {
            List<Map<String, Object>> unSelectedProjectsList = (List) data.get("unSelectedProjectsList");
            if (selectedProjectsList.isEmpty() && unSelectedProjectsList.isEmpty()) {
                dao.updateSuperDashboardInfo(id, name, description);
                return;
            } else {
                if (!unSelectedProjectsList.isEmpty()) {
                    for (Map<String, Object> row : unSelectedProjectsList) {
                        dao.deleteFromSuperDashboardById((String) row.get("Id"));
                    }
                }
                if (!selectedProjectsList.isEmpty()) {
                    for (Map<String, Object> row : selectedProjectsList) {
                        maxPosition = maxPosition + 1;
                        dao.updateSuperDashboardProjects(null, name, description, maxPosition, row);
                    }
                }
            }
        }
    }

    public void sortSuperDashboard(List<Map<String, Object>> data) {
        data.stream().forEach(record -> dao.updatePosition(record.get("Id"), record.get("Position")));
    }
}
