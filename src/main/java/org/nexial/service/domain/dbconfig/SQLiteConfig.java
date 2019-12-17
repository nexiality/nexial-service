package org.nexial.service.domain.dbconfig;

import java.util.List;
import java.util.Map;
import javax.annotation.Resource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

public class SQLiteConfig {
    @Resource
    private JdbcTemplate jdbcTemplate;

    public JdbcTemplate getJdbcTemplate() { return jdbcTemplate; }

    public void setJdbcTemplate(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public List<Map<String, Object>> queryForList(String sql, Object... params) {
        return jdbcTemplate.queryForList(sql, params);
    }

    public Object queryForObject(String sql, Class requiredClass, Object... params) {
        return jdbcTemplate.queryForObject(sql, params, requiredClass);
    }

    @Transactional
    void execute(String sql, Object... params) { jdbcTemplate.update(sql, params); }
}
