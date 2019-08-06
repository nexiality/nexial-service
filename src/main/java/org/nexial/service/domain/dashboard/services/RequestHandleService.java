package org.nexial.service.domain.dashboard.services;

import java.util.List;

import org.nexial.service.domain.dashboard.model.DashboardLog;
import org.nexial.service.domain.dashboard.repository.DashboardLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RequestHandleService {
    @Autowired
    private DashboardLogRepository repository;

    public void insertData(DashboardLog data) {
        repository.insertIntoDashboardLog(data.getProject(), data.getPrefix(), data.getPath(), data.getStatus(),
                                          data.getCreatedon(), data.getModifiedon(), data.getKey());
    }

    public List<DashboardLog> findByStatus(String status) {
        return repository.findByStatus(status);
    }
}
