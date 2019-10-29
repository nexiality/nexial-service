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

    public String storeFile(MultipartFile multiPartFile, String project, String runId, String folderPath) {
        String fileName = multiPartFile.getOriginalFilename();
        String parent = properties.getLocalArtifactsPath() + project + PATH_SEPARATOR + runId + PATH_SEPARATOR;

        try {
            String targetLocation =
                parent + (StringUtils.isBlank(folderPath) ? "" : folderPath + PATH_SEPARATOR) + fileName;

            File file = FileUtil.writeBinaryFile(targetLocation, true, multiPartFile.getBytes());
            if (fileName != null && fileName.equals(EXECUTION_DETAIL_JSON)) {
                dao.insertIntoScheduleInfo(project, runId, targetLocation);
            }
            return beanFactory.getBean(properties.getStorageLocation(), IFileStorage.class)
                              .uploadArtifact(file, project, runId, folderPath);
        } catch (MalformedStreamException e) {
            logger.error("File is Malformed", e);
        } catch (IOException e) {
            logger.error("Unable to find the location", e);
        }
        // todo returning null for now
        return null;
    }

    public Resource loadFileAsResource(File file) throws IOException {
        try {
            return new UrlResource(file.toURI());
        } catch (MalformedURLException e) {
            logger.error("File is not found in the specified path = " + file.getAbsolutePath(), e);
            throw new IOException("File is not found in the specified path = " + file.getAbsolutePath());
        }
    }
}