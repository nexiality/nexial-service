package org.nexial.service.domain.dashboard.controller;

import java.io.File;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.nexial.service.domain.ApplicationProperties;
import org.nexial.service.domain.dashboard.service.FileStorageService;
import org.nexial.service.domain.utils.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.HandlerMapping;

import static org.nexial.core.tools.CommandDiscovery.GSON;

@RestController
public class FileStorageController {
    private final FileStorageService fileStorageService;
    private final ApplicationProperties properties;
    private static final Logger logger = LoggerFactory.getLogger(FileStorageController.class);

    public FileStorageController(FileStorageService fileStorageService, ApplicationProperties properties) {
        this.fileStorageService = fileStorageService;
        this.properties = properties;
    }

    @RequestMapping(value = {"/upload"}, method = RequestMethod.POST, consumes = {"multipart/form-data"},
                    produces = {"application/json", "application/xml"})
    public @ResponseBody
    ResponseEntity<String> uploadFile(@RequestParam("FileName") MultipartFile file,
                                      @RequestParam("ProjectName") String projectName,
                                      @RequestParam("RunId") String runId,
                                      @RequestParam("Path") String folderPath) {
        return fileStorageService.storeFile(file, projectName, runId, folderPath);
    }

    // to support download from nexial-summary folder
    // For the files:- http://172.00.00:8099/execution/project/runid/junit.xml
    @RequestMapping("/execution/**")
    public ResponseEntity<Object> downloadSummary(HttpServletRequest request) {
        String requestUrl = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

        String filePath = StringUtils.substringAfter(requestUrl, "/execution/");
        filePath = properties.getLocalArtifactsPath() + filePath;
        return getResourceResponseEntity(request, filePath, requestUrl);
    }

    // For the files:- http://172.00.00:8099/dashboard/project/summary_output.json
    @RequestMapping("/dashboard/**")
    public ResponseEntity<Object> download(HttpServletRequest request) {
        String requestUrl = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

        String filePath = StringUtils.substringAfter(requestUrl, "/dashboard/");
        filePath = properties.getLocalExecutionSummaryPath() + filePath;
        return getResourceResponseEntity(request, filePath, requestUrl);
    }

    @NotNull
    private ResponseEntity<Object> getResourceResponseEntity(HttpServletRequest request, String filePath,
                                                             String reqUrl) {
        File file = new File(filePath);
        String contentType = "application/octet-stream";
        ;
        if (file.exists()) {
            Resource resource;
            try {
                resource = new UrlResource(file.toURI());
                contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
                return ResponseEntity.ok()
                                     .contentType(MediaType.parseMediaType(contentType))
                                     .header(HttpHeaders.CONTENT_DISPOSITION,
                                             "attachment; filename=\"" + resource.getFilename() + "\"")
                                     .body(resource);
            } catch (Exception e) {
                logger.info("Specified file with path " + filePath + "is not present");
                Response error = new Response(reqUrl, "", 404, "URL resource not found", e.getMessage());
                return ResponseEntity.status(404)
                                     .contentType(MediaType.APPLICATION_JSON)
                                     .body(GSON.toJson(error));
            }
        } else {
            logger.info("Specified file with path " + filePath + " is not present");
            Response error = new Response(reqUrl, "", 404, "URL resource does not exist", "");
            return ResponseEntity.status(404)
                                 .contentType(MediaType.APPLICATION_JSON)
                                 .body(GSON.toJson(error));
        }
    }
}
