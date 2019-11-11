package org.nexial.service.domain.dbconfig;

import java.util.List;
import java.util.Map;
import javax.annotation.Resource;
import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;

public class SQLiteConfig {
    @Resource
    private JdbcTemplate jdbcTemplate;
    private DataSource dataSource;

    public DataSource getDataSource() { return dataSource; }

    public void setDataSource(DataSource dataSource) { this.dataSource = dataSource; }

    List<Map<String, Object>> queryForList(String sql, Object... params) {
        return jdbcTemplate.queryForList(sql, params);
    }

    Object queryForObject(String sql, Class requiredClass, Object... params) {
        return jdbcTemplate.queryForObject(sql, params, requiredClass);
    }

    void execute(String sql, Object... params) { jdbcTemplate.update(sql, params); }
}
