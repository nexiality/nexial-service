package org.nexial.service.domain.dashboard.controller;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.nexial.service.domain.dashboard.model.DashboardLog;
import org.nexial.service.domain.dashboard.services.RequestHandleService;
import org.nexial.service.domain.utils.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RequestHandleController {

    @Autowired
    RequestHandleService service;

    @RequestMapping(value = "/services/dashboard/update", consumes = "application/json")
    @ResponseBody
    public ResponseEntity<String> insertDataToDashboardUpdateLog(@RequestBody DashboardLog dashboardLog) {
        String dateNow = new SimpleDateFormat(Constants.DATE_TIME_FORMAT).format(new Date());
        dashboardLog.setStatus(Constants.LOG_STATUSES[0]);
        dashboardLog.setCreatedon(dateNow);
        dashboardLog.setModifiedon(dateNow);
        service.insertData(dashboardLog);
        return new ResponseEntity<>("", HttpStatus.OK);
    }
}
