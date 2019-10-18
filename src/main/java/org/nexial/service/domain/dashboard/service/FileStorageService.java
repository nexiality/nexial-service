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
import org.nexial.service.domain.utils.Constants;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {
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
                targetLocation = properties.getLocalArtifactsPath() + projectName + Constants.CLOUD_AWS_SEPARATOR +
                                 runId +
                                 Constants.CLOUD_AWS_SEPARATOR +
                                 fileName;
            } else {
                targetLocation = properties.getLocalArtifactsPath() + projectName + Constants.CLOUD_AWS_SEPARATOR +
                                 runId +
                                 Constants.CLOUD_AWS_SEPARATOR +
                                 folderPath + Constants.CLOUD_AWS_SEPARATOR +
                                 fileName;
            }
            try {
                fileLocation = FileUtil.writeBinaryFile(targetLocation,
                                                        true,
                                                        file.getBytes());
            } catch (MalformedStreamException e) {
                e.printStackTrace();
                System.out.println(e.getCause());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        String url = beanFactory.getBean(properties.getStorageLocation(), IFileStorage.class)
                                .uploadArtifact(fileLocation, projectName, runId, folderPath);
        if ("execution-detail.json".equals(fileName)) {
            dao.insertIntoScheduleInfo(projectName, runId, targetLocation);
        }
        return url;
    }

    public Resource loadFileAsResource(File file) throws IOException {
        try {
            Resource resource = new UrlResource(file.toURI());
            return resource;
        } catch (MalformedURLException e) {
            throw new IOException("File is not found in the specified path = " + file.getAbsolutePath());
        }
    }
}