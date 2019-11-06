package org.nexial.service.domain.dashboard.service;

import java.io.File;
import java.io.IOException;

import org.apache.commons.fileupload.MultipartStream.MalformedStreamException;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.service.domain.ApplicationProperties;
import org.nexial.service.domain.dashboard.IFileStorage;
import org.nexial.service.domain.dbconfig.ApplicationDao;
import org.nexial.service.domain.utils.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import static org.nexial.core.NexialConst.GSON;
import static org.nexial.service.domain.utils.Constants.EXECUTION_DETAIL_JSON;
import static org.nexial.service.domain.utils.Constants.PATH_SEPARATOR;

@Service
public class FileStorageService {
    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);
    private final ApplicationProperties properties;
    private final BeanFactory beanFactory;
    private final ApplicationDao dao;

    public FileStorageService(ApplicationProperties properties, BeanFactory beanFactory, ApplicationDao dao) {
        this.properties = properties;
        this.beanFactory = beanFactory;
        this.dao = dao;
    }

    public ResponseEntity<String> storeFile(MultipartFile multiPartFile, String project,
                                            String runId, String folderPath) {
        String fileName = multiPartFile.getOriginalFilename();
        String parent = properties.getLocalArtifactsPath() + project + PATH_SEPARATOR + runId + PATH_SEPARATOR;
        String targetLocation = parent +
                                (StringUtils.isBlank(folderPath) ? "" : folderPath + PATH_SEPARATOR) + fileName;

        int returnCode = 500;
        Response response;

        try {
            File file = FileUtil.writeBinaryFile(targetLocation, true, multiPartFile.getBytes());
            if (fileName != null && fileName.equals(EXECUTION_DETAIL_JSON)) {
                dao.insertIntoScheduleInfo(project, runId, targetLocation);
            }
            String url = beanFactory.getBean(properties.getStorageLocation(), IFileStorage.class)
                                    .uploadArtifact(file, project, runId, folderPath);

            if (url == null) {
                response = new Response("/upload", null, returnCode, "Internal Server Error!!", "");
            } else {
                response = new Response("/upload", url, 200, "OK", "");
            }
        } catch (MalformedStreamException e) {
            response = new Response("/upload", "", returnCode, "Internal Server Error!!", e.getMessage());
            logger.error("File with path " + targetLocation + " is malformed.", e);
        } catch (IOException e) {
            logger.error("Unable to find the location", e);
            response = new Response("/upload", "", returnCode, "Internal Server Error!!", e.getMessage());
        }
        return ResponseEntity.status(response.getReturnCode()).contentType(MediaType.APPLICATION_JSON)
                             .body(GSON.toJson(response));

    }
}