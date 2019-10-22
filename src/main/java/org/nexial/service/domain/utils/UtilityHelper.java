package org.nexial.service.domain.utils;

import org.apache.commons.lang3.StringUtils;

import static org.nexial.service.domain.utils.Constants.PATH_SEPARATOR;

public class UtilityHelper {
    public static String getPath(String path, boolean append) {
        path = StringUtils.replace(path, "\\", PATH_SEPARATOR);
        return append ? StringUtils.appendIfMissing(path, PATH_SEPARATOR) : path;
    }
}

