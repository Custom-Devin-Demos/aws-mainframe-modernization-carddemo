package com.carddemo.online.report;

import com.carddemo.online.common.ScreenResponse;
import com.carddemo.online.session.SessionUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** CORPT00C (CR00) transaction report request, map CORPT00. */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    /** Not a CORPT00C message: the program had no way to look a submitted job up again. */
    static final String MSG_NOT_FOUND = "Report request NOT found...";

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    /** ENTER on the report screen. */
    @PostMapping
    public ScreenResponse<ReportRequest> submit(@RequestBody ReportRequestBody body, SessionUser user) {
        return service.submit(body, user);
    }

    @GetMapping("/{requestId}")
    public ScreenResponse<ReportRequest> find(@PathVariable String requestId) {
        return service.find(requestId);
    }
}
