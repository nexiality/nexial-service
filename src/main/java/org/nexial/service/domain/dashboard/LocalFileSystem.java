package org.nexial.service.domain.dashboard;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.nexial.service.domain.utils.Constants;
import org.nexial.service.domain.utils.UtilityHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("localSystem")
public class LocalFileSystem implements IFileStorage {
    private static final Logger logger = LoggerFactory.getLogger(LocalFileSystem.class);

    @Override
    public String uploadArtifact(File file, String projectName, String runId, String folderPath) {
        return getLocalUrl(file);
    }

    @Override
    public void uploadSummary(File file, String project) {
        getLocalUrl(file);
    }

    @Override
    public void deleteFolders(String path) { }

    @NotNull
    private String getLocalUrl(File file) {
        String url = null;
        try {
            url = UtilityHelper.getPath(file.getCanonicalPath(), false);
            url = StringUtils.substringAfter(url, Constants.PATH_SEPARATOR);
            url = StringUtils.substringAfter(url, Constants.PATH_SEPARATOR);
        } catch (IOException e) {
            logger.error("Unable to find the file - " + file.getAbsolutePath(), e);
        }
        // need to change to ip address
        return "http://localhost:8099/download/" + url;
    }
}
