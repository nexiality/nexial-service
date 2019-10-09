package org.nexial.service.domain.utils;

import static org.apache.commons.lang3.StringUtils.appendIfMissing;
import static org.apache.commons.lang3.StringUtils.contains;
import static org.nexial.service.domain.utils.Constants.CLOUD_AWS_SEPARATOR;

public class UtilityHelper {
    public static String getPath(String path) {
        String slash = contains(path, "\\") ? "\\" : CLOUD_AWS_SEPARATOR;
        path = appendIfMissing(path, slash);
        path = path.replace("\\", CLOUD_AWS_SEPARATOR);
        return path;
    }
}

