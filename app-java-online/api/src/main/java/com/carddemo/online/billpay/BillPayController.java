package com.carddemo.online.billpay;

import com.carddemo.online.common.ScreenResponse;
import com.carddemo.online.session.SessionUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** COBIL00C (CB00) bill payment, map COBIL00. */
@RestController
@RequestMapping("/api/bill-payments")
public class BillPayController {

    private final BillPayService service;

    public BillPayController(BillPayService service) {
        this.service = service;
    }

    /** ENTER with a blank confirm (also the first-entry path when an account was preselected). */
    @GetMapping("/{acctId}")
    public ScreenResponse<BillPayScreen> balance(@PathVariable String acctId, SessionUser user) {
        return service.enter(acctId, null);
    }

    /** ENTER with the Acct ID and confirm fields as typed. */
    @PostMapping
    public ScreenResponse<BillPayScreen> pay(@RequestBody BillPayRequest request, SessionUser user) {
        return service.enter(request.acctId(), request.confirm());
    }
}
