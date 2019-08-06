package org.nexial.service.domain;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Properties;
import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.scheduling.annotation.Scheduled;

import static org.nexial.service.domain.utils.Constants.*;

public abstract class ReloadApplicationProperties {
    @Autowired
    public StandardEnvironment environment;

    private File filePath;
    private long lastModTime = 0L;
    private static final Log logger = LogFactory.getLog(ReloadApplicationProperties.class);

    @PostConstruct
    public void init() throws IOException {

        assert StringUtils.isNotBlank(environment.getProperty(CONFIGURATION_PATH));
        filePath = new File(StringUtils.replace(environment.getProperty(CONFIGURATION_PATH), "\\", "/"));
        assert filePath != null;
        assert filePath.exists();
        assert filePath.canRead();
        lastModTime = filePath.lastModified();
        // pre load cloud-configuration files
        loadExternalConfiguration();

    }

    private void loadExternalConfiguration() throws IOException {
        Properties properties = new Properties();
        InputStream inputStream = null;
        assert filePath != null;
        assert filePath.exists();
        assert filePath.canRead();
        inputStream = Files.newInputStream(filePath.toPath());
        properties.load(inputStream);
        environment.getPropertySources().addFirst(new PropertiesPropertySource(CONFIGURATION_NAME,
                                                                               properties));
    }

    @Scheduled(fixedRate = 100)
    public void reload() throws IOException {
        long currentModified = filePath.lastModified();
        if (currentModified > lastModTime) {
            lastModTime = currentModified;
            loadExternalConfiguration();
            logger.info(CONFIGURATION_CHANGE_MESSAGE);
        }
    }
}

