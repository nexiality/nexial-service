package org.nexial.service.domain.dbconfig;

import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;

public class SQLiteConfig {

    List<String> sqlCreateStatements;
    private DataSource dataSource;

    @Resource
    private JdbcTemplate jdbcTemplate;

    public DataSource getDataSource() { return dataSource; }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void setSqlCreateStatements(List<String> sqlCreateStatements) {
        this.sqlCreateStatements = sqlCreateStatements;
    }

    void execute(String sql) { jdbcTemplate.update(sql); }

    List<Map<String, Object>> queryForList(String sql, Object[] params) {
        return jdbcTemplate.queryForList(sql, params);
    }

    Object queryForObject(String sql, Object[] params, Class neededClass) {
        return jdbcTemplate.queryForObject(sql, params, neededClass);
    }

    void execute(String sqlStatement, Object[] params) { jdbcTemplate.update(sqlStatement, params); }

}
