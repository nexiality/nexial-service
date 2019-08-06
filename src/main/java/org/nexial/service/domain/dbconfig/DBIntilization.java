package org.nexial.service.domain.dbconfig;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.annotation.PostConstruct;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DBIntilization {
    @Qualifier("dataSource")
    @Autowired
    private DataSource dataSource;

    @PostConstruct
    public void initialize() {
        try {

            Connection connection = dataSource.getConnection();
            System.out.println(dataSource);
            Statement statement = connection.createStatement();
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS DASHBOARD_LOG(" +
                "ID INTEGER Primary key not null, " +
                "PROJECT TEXT not null," +
                "PREFIX TEXT," +
                "PATH TEXT not null, " +
                "STATUS TEXT," +
                "WORKERID TEXT," +
                "CREATEDON TEXT," +
                "MODIFIEDON TEXT," +
                "KEY TEXT not null)");
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS EXECUTION_SUMMARY_LOG(" +
                "ID INTEGER Primary key not null, " +
                "PROJECT TEXT not null," +
                "PREFIX TEXT," +
                "CREATEDON TEXT," +
                "EXECUTIONSUMMARY TEXT)");
            statement.close();
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
