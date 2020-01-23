package org.nexial.service.domain.dashboard;

import java.io.File;

public interface IFileStorage {
    String uploadArtifact(File file, String projectName, String runId, String folderPath);

    void uploadSummary(File file, String project);

    void deleteFolders(String path);

    String getSummaryUrl();
}
