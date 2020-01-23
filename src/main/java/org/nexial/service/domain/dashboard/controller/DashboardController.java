package org.nexial.service.domain.dashboard.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.nexial.service.domain.ApplicationProperties;
import org.nexial.service.domain.dashboard.IFileStorage;
import org.nexial.service.domain.dashboard.service.DashboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class DashboardController {
    private static final Logger logger = LoggerFactory.getLogger(DashboardController.class);
    private BeanFactory factory;
    private ApplicationProperties properties;
    private DashboardService dashboardService;

    public DashboardController(BeanFactory factory,
                               ApplicationProperties properties,
                               DashboardService dashboardService) {
        this.factory = factory;
        this.properties = properties;
        this.dashboardService = dashboardService;
    }

    @RequestMapping("/configurationURL")
    @ResponseBody
    public String getConfigurationUrl() {
        return factory.getBean(properties.getStorageLocation(), IFileStorage.class)
                      .getSummaryUrl();
    }

    @GetMapping("/")
    public String defaultPage() {
        return "index";
    }

    @GetMapping("/dashboard*")
    public String getDashboard() {
        return "executionsummary";
    }

    @GetMapping(value = "/projectfiles*")
    @ResponseBody
    public ResponseEntity getProjectFiles(HttpServletRequest request, HttpServletResponse response) throws Exception {
        String projectName = request.getParameter("project");
        String file = request.getParameter("file");
        String location = properties.getLocalExecutionSummaryPath() + projectName + file;
        File filePath = new File(location);
        InputStream inputStreams = new FileInputStream(filePath);
        IOUtils.copy(inputStreams, response.getOutputStream());
        inputStreams.close();
        response.flushBuffer();
        return new ResponseEntity(HttpStatus.OK);
    }

    @GetMapping(value = "/defaultfiles*")
    @ResponseBody
    public ResponseEntity getDefaultConfigFiles(HttpServletRequest request, HttpServletResponse response)
        throws Exception {
        String file = request.getParameter("file");
        String location = properties.getLocalExecutionSummaryPath() + file;
        File filePath = new File(location);
        InputStream inputStreams = new FileInputStream(filePath);
        IOUtils.copy(inputStreams, response.getOutputStream());
        inputStreams.close();
        response.flushBuffer();
        return new ResponseEntity(HttpStatus.OK);
    }

    @RequestMapping(value = "/projects", method = RequestMethod.GET)
    @ResponseBody
    public List<Map<String, Object>> getProjectsList() {
        return dashboardService.fetchProjectList();
    }

    @RequestMapping(value = "/dashboards/{projectId}", method = RequestMethod.GET)
    @ResponseBody
    public List<Map<String, Object>> getDashboardList(@PathVariable("projectId") String projectId) {
        return dashboardService.fetchDashboardList(projectId);
    }

    @RequestMapping(value = "/superdashboard", method = RequestMethod.GET)
    @ResponseBody
    public List<Map<String, Object>> getSuperDashboardNamesList() {
        return dashboardService.fetchSuperDashboardList();
    }

    @RequestMapping(value = "/superdashboard/{name}", method = RequestMethod.GET)
    @ResponseBody
    public List<Map<String, Object>> getSuperDashboardsListByName(@PathVariable("name") String name) {
        return dashboardService.fetchSuperDashboardsList(name);
    }

    @RequestMapping(value = "/superdashboard/projects/", method = RequestMethod.GET)
    @ResponseBody
    public List<Map<String, Object>> getAllProjectsList() {
        return dashboardService.getAllProjectsList();
    }

    @DeleteMapping(value = "/superdashboard/delete/", consumes = {MediaType.APPLICATION_JSON_VALUE})
    @ResponseBody
    public String deleteSuperDashboard(@RequestBody Map<String, String> data) {
        String name = data.get("Name");
        dashboardService.deleteFromSuperDashboard(name);
        return "record is deleted";
    }

    @RequestMapping(value = "project/create", method = RequestMethod.POST)
    @ResponseBody
    public String updateProject(@RequestBody Map<String, String> projectObj) {
        String projectName = projectObj.get("name");
        String dashboard = projectObj.get("dashboard");
        String description = projectObj.get("description");

        if (StringUtils.isEmpty(projectName) || StringUtils.isEmpty(dashboard)) { return "Not blank"; }
        dashboardService.createProject(projectName, dashboard, description);
        return "project is successfully inserted";
    }

    @RequestMapping(value = "dashboard/update", method = RequestMethod.POST)
    @ResponseBody
    public String updateDashboard(@RequestBody Map<String, String> dashboardInfo) {
        String id = dashboardInfo.get("id");
        String name = dashboardInfo.get("name");
        String description = dashboardInfo.get("description");
        String projectId = dashboardInfo.get("projectId");

        dashboardService.updateDashboard(id, name, description, projectId);
        return "Update successfully";

    }

    @RequestMapping(value = "superdashboard/update", method = RequestMethod.POST)
    @ResponseBody
    public String updateSuperDashboard(@RequestBody Map<String, Object> data) {
        dashboardService.updateSuperDashboard(data);
        return "Update successfully";
    }

    @RequestMapping(value = "superdashboard/sort", method = RequestMethod.POST)
    @ResponseBody
    public String updateSuperDashboardPosition(@RequestBody List<Map<String, Object>> data) {
        dashboardService.sortSuperDashboard(data);
        return "Update successfully";
    }
}
