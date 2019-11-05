package org.nexial.service.domain.dashboard.controller;

import java.io.File;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.nexial.service.domain.ApplicationProperties;
import org.nexial.service.domain.dashboard.service.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
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
    String uploadFile(@RequestParam("FileName") MultipartFile file,
                      @RequestParam("ProjectName") String projectName,
                      @RequestParam("RunId") String runId,
                      @RequestParam("Path") String folderPath) {
        return fileStorageService.storeFile(file, projectName, runId, folderPath);
    }

    // to support download from nexial-summary folder
    // For the files:- http://172.00.00:8099/execution/project/runid/junit.xml
    @RequestMapping("/execution/**")
    public ResponseEntity<Object> downloadSummary(HttpServletRequest request) throws IOException {
        String restURL = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

        String filePath = StringUtils.substringAfter(restURL, "/execution/");
        filePath = properties.getLocalArtifactsPath() + filePath;
        return getResourceResponseEntity(request, filePath);
    }

    // For the files:- http://172.00.00:8099/dashboard/project/summary_output.json
    @RequestMapping("/dashboard/**")
    public ResponseEntity<Object> download(HttpServletRequest request) throws IOException {
        String restURL = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);

        String filePath = StringUtils.substringAfter(restURL, "/dashboard/");
        filePath = properties.getLocalExecutionSummaryPath() + filePath;
        return getResourceResponseEntity(request, filePath);
    }

    @NotNull
    private ResponseEntity<Object> getResourceResponseEntity(HttpServletRequest request, String filePath)
        throws IOException {
        File file = new File(filePath);
        String contentType;
        if (file.exists()) {
            Resource resource = fileStorageService.loadFileAsResource(file);
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
            if (contentType == null) { contentType = "application/octet-stream"; }
            return ResponseEntity.ok()
                                 .contentType(MediaType.parseMediaType(contentType))
                                 .header(HttpHeaders.CONTENT_DISPOSITION,
                                         "attachment; filename=\"" + resource.getFilename() + "\"")
                                 .body(resource);
        } else {
            logger.info("Specified file with path " + filePath + "is not present");
            // todo return something to let user know the error.

            return ResponseEntity.status(400).contentType(MediaType.APPLICATION_JSON).body("\"Wrong File\": + \"\"");
        }
    }
}
