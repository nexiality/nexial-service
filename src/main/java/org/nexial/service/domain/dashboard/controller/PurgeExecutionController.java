package org.nexial.service.domain.dashboard.controller;

import org.nexial.service.domain.dashboard.service.PurgeExecutionService;
import org.nexial.service.domain.utils.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import static org.nexial.core.NexialConst.GSON;

@RestController
public class PurgeExecutionController {
    private static final Logger logger = LoggerFactory.getLogger(PurgeExecutionController.class);
    private final PurgeExecutionService purgeService;

    public PurgeExecutionController(PurgeExecutionService purgeService) { this.purgeService = purgeService; }

    @RequestMapping(value = {"/purgeWithRunId/{runId}"}, method = RequestMethod.DELETE, produces = {"application/json"})
    public ResponseEntity<String> purgeDataWithRunID(@PathVariable String runId) {
        Response response = new Response("/purge", "", 200, "OK", "");
        response = purgeService.purgeWithRunId(runId, response);
        return ResponseEntity.status(response.getReturnCode())
                             .contentType(MediaType.APPLICATION_JSON).body(GSON.toJson(response));
    }

    // Work-in progress
    @RequestMapping(value = "/purgeWithDate/{date}", method = RequestMethod.DELETE, produces = "application/json")
    public ResponseEntity<String> purgeDataWithDate(@PathVariable String date) {
        Response response = new Response("/purgeWithDate", "", 200, "OK", "");
        response = purgeService.purgeWithDate(date, response);
        return ResponseEntity.status(response.getReturnCode())
                             .contentType(MediaType.APPLICATION_JSON).body(GSON.toJson(response));
    }
}
