package org.nexial.service.domain.dashboard.controller;

import org.nexial.service.domain.ApplicationProperties;
import org.nexial.service.domain.dashboard.service.PurgeExecutionService;
import org.nexial.service.domain.utils.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.nexial.core.NexialConst.GSON;

@RestController
public class PurgeExecutionController {
    private static final Logger logger = LoggerFactory.getLogger(PurgeExecutionController.class);
    private final PurgeExecutionService purgeService;
    private final ApplicationProperties properties;

    public PurgeExecutionController(PurgeExecutionService purgeService, ApplicationProperties properties) {
        this.purgeService = purgeService;
        this.properties = properties;
    }

    @RequestMapping(value = {"/purge"}, method = RequestMethod.GET,
                    produces = {"application/json", "application/xml"})
    public ResponseEntity<String> purgeDataWithRunID(@RequestParam("RunId") String runId) {
        purgeService.purgeWithRunId(runId);
        Response response = new Response("/purge", "", 200, "OK", "");
        return ResponseEntity.status(200).contentType(MediaType.APPLICATION_JSON).body(GSON.toJson(response));
    }
}
