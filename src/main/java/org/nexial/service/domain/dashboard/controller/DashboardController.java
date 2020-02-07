package org.nexial.service.domain.dashboard.controller;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.nexial.service.domain.ApplicationProperties;
import org.nexial.service.domain.dashboard.IFileStorage;
import org.nexial.service.domain.dashboard.service.DashboardService;
import org.nexial.service.domain.utils.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import static org.nexial.core.NexialConst.GSON;

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
    public ResponseEntity<Object> updateProject(@RequestBody Map<String, String> projectObj) {
        Response response;
        try {
        String projectName = projectObj.get("name");
        String dashboard = projectObj.get("dashboard");
        String description = projectObj.get("description");
            if (StringUtils.isEmpty(projectName) || StringUtils.isEmpty(dashboard)) {
                response = new Response("project/create", "", 500, "Internal Server Error!!",
                                        "project name or dashboard name not blank");
            } else {
        dashboardService.createProject(projectName, dashboard, description);
                response = new Response("project/create", "", 200, "OK", "Successfully created");
            }
        } catch (UncategorizedSQLException e) {
            response = new Response("project/create", "", 500, "Internal Server Error!!",
                                    "Project name already exists.Try with another name");
        } catch (Exception e) {
            response = new Response("project/create", "", 500, "Internal Server Error!!",
                                    "Internal Server Error!!");
        }
        return ResponseEntity.status(response.getReturnCode()).contentType(MediaType.APPLICATION_JSON)
                             .body(GSON.toJson(response));
    }

    @RequestMapping(value = "dashboard/update", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<Object> updateDashboard(@RequestBody Map<String, String> dashboardInfo) {
        String id = dashboardInfo.get("id");
        String name = dashboardInfo.get("name");
        String description = dashboardInfo.get("description");
        String projectId = dashboardInfo.get("projectId");
        Response response;
        try {
        dashboardService.updateDashboard(id, name, description, projectId);
            response = new Response("project/create", "", 200, "OK", "Successfully created");
        } catch (UncategorizedSQLException e) {
            response = new Response("project/create", "", 500, "Internal Server Error!!",
                                    "Dashboard name already exists.Try with another name");
        } catch (Exception e) {
            response = new Response("project/create", "", 500, "Internal Server Error!!",
                                    "Internal Server Error!!");
        }
        return ResponseEntity.status(response.getReturnCode()).contentType(MediaType.APPLICATION_JSON)
                             .body(GSON.toJson(response));

    }

    @RequestMapping(value = "superdashboard/update", method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<Object> updateSuperDashboard(@RequestBody Map<String, Object> data) {
        Response response;
        try{
        dashboardService.updateSuperDashboard(data);
            response = new Response("project/create", "", 200, "OK",
                                    "Successfully created");
        }catch (UncategorizedSQLException e){
            response = new Response("project/create", "", 500, "Internal Server Error!!",
                                    "Duplicate SuperDashboard Name");
        }catch (Exception e){
            response = new Response("project/create", "", 500, "Internal Server Error!!",
                                    "Internal Server Error!!");
        }
        return ResponseEntity.status(response.getReturnCode()).contentType(MediaType.APPLICATION_JSON)
                             .body(GSON.toJson(response));
    }

    @RequestMapping(value = "superdashboard/sort", method = RequestMethod.POST)
    @ResponseBody
    public String updateSuperDashboardPosition(@RequestBody List<Map<String, Object>> data) {
        dashboardService.sortSuperDashboard(data);
        return "Update successfully";
    }
}
