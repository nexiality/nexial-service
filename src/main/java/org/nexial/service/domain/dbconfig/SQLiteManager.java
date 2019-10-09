package org.nexial.service.domain.dbconfig;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.annotation.PostConstruct;

import org.apache.commons.lang3.RandomStringUtils;
import org.nexial.commons.utils.ResourceUtils;
import org.springframework.stereotype.Component;

@Component
public class SQLiteManager {
    private static final int ID_LENGTH = 16;

    private final SQLiteConfig sqLiteConfig;

    private Properties properties;

    public SQLiteManager(SQLiteConfig sqLiteConfig) {
        this.sqLiteConfig = sqLiteConfig;
    }

    @PostConstruct
    public void initialize() {
        generateSqlStatement();
        try {
            properties = ResourceUtils.loadProperties("sqlQueryStatements.properties");
        } catch (IOException ex) {
            throw new RuntimeException("SQL Query Statement property Not loaded");
        }
    }

    public String get() {
        return RandomStringUtils.randomAlphanumeric(ID_LENGTH);
    }

    public void updateData(String key, Object[] paramArray) {
        sqLiteConfig.execute(getSqlStatement(key), paramArray);
    }

    public void deleteData(String key, Object[] paramArray) {
        updateData(key, paramArray);
    }

    public List<Map<String, Object>> selectForList(String key, Object[] paramArray) {
        return sqLiteConfig.queryForList(getSqlStatement(key), paramArray);
    }

    public Object selectForObject(String key, Object[] paramArray, Class neededClass) {
        return sqLiteConfig.queryForObject(getSqlStatement(key), paramArray, neededClass);
    }

    private void generateSqlStatement() {
        sqLiteConfig.sqlCreateStatements.forEach(sqLiteConfig::execute);
    }

    private String getSqlStatement(String sqlStatement) {
        return properties.getProperty(sqlStatement);
    }
}
