package org.nexial.service.domain.dashboard.service;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;

import org.apache.commons.fileupload.MultipartStream.MalformedStreamException;
import org.apache.commons.lang3.StringUtils;
import org.nexial.commons.utils.FileUtil;
import org.nexial.service.domain.ApplicationProperties;
import org.nexial.service.domain.dashboard.IFileStorage;
import org.nexial.service.domain.dbconfig.ApplicationDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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

    public String storeFile(MultipartFile file, String projectName, String runId, String folderPath) {
        String targetLocation = null;
        String fileName = file.getOriginalFilename();
        File fileLocation = null;
        try {
            if (StringUtils.isBlank(folderPath)) {
                targetLocation = properties.getLocalArtifactsPath() + projectName +
                                 PATH_SEPARATOR + runId + PATH_SEPARATOR + fileName;
            } else {
                targetLocation = properties.getLocalArtifactsPath() + projectName + PATH_SEPARATOR +
                                 runId + PATH_SEPARATOR + folderPath + PATH_SEPARATOR + fileName;
            }
            try {
                fileLocation = FileUtil.writeBinaryFile(targetLocation,
                                                        true,
                                                        file.getBytes());
            } catch (MalformedStreamException e) {
                logger.error("File is Malformed", e);
            }
        } catch (IOException e) {
            logger.error("Unable to find the location", e);
        }
        String url = beanFactory.getBean(properties.getStorageLocation(), IFileStorage.class)
                                .uploadArtifact(fileLocation, projectName, runId, folderPath);
        if (fileName.equals("execution-detail.json")) {
            dao.insertIntoScheduleInfo(projectName, runId, targetLocation);
        }
        return url;
    }

    public Resource loadFileAsResource(File file) throws IOException {
        try {
            Resource resource = new UrlResource(file.toURI());
            return resource;
        } catch (MalformedURLException e) {
            logger.error("File is not found in the specified path = " + file.getAbsolutePath(), e);
            throw new IOException("File is not found in the specified path = " + file.getAbsolutePath());
        }
    }
}