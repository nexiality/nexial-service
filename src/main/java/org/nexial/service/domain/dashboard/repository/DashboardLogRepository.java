package org.nexial.service.domain.dashboard.repository;

import java.util.List;

import org.nexial.service.domain.dashboard.model.DashboardLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface DashboardLogRepository extends JpaRepository<DashboardLog, Integer> {

    @Query(value = "SELECT * FROM dashboard_log WHERE status = :status", nativeQuery = true)
    List<DashboardLog> findByStatus(@Param("status") String status);

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO dashboard_log (project,prefix,path,status,createdon,modifiedon,key) VALUES (:project,:prefix,:path,:status,:createdon,:modifiedon,:key)", nativeQuery = true)
    void insertIntoDashboardLog(@Param("project") String project,
                                @Param("prefix") String prefix,
                                @Param("path") String path,
                                @Param("status") String status,
                                @Param("createdon") String createdon,
                                @Param("modifiedon") String modifiedon,
                                @Param("key") String key);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = "UPDATE dashboard_log SET status= :status, modifiedon= :modifiedon WHERE path= :path", nativeQuery = true)
    void updateLogStatus(@Param("status") String status,
                         @Param("path") String path,
                         @Param("modifiedon") String modifiedon);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(value = "UPDATE dashboard_log SET workerid= :workerid WHERE key= :key", nativeQuery = true)
    void updateWorkerId(@Param("workerid") String workerid, @Param("key") String key);

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO execution_summary_log (project,prefix,executionsummary,createdon) VALUES (:project,:prefix,:executionsummary,:createdon)", nativeQuery = true)
    void insertExecutionSummary(@Param("project") String project,
                                @Param("prefix") String prefix,
                                @Param("executionsummary") String executionSummary,
                                @Param("createdon") String createdon);
}
