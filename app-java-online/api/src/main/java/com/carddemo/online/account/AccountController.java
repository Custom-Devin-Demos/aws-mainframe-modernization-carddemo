package com.carddemo.online.account;

import com.carddemo.online.common.ScreenResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** COACTVWC (CAVW) account view and COACTUPC (CAUP) account update. */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    /**
     * Account + customer via the card cross reference. {@code mode=update} applies the COACTUPC account-number
     * edit and prompt; the default applies COACTVWC's.
     */
    @GetMapping({"", "/{acctId}"})
    public ScreenResponse<AccountDetails> get(@PathVariable(required = false) String acctId,
            @RequestParam(defaultValue = "view") String mode) {
        return service.read(acctId,
                "update".equalsIgnoreCase(mode) ? AccountService.Mode.UPDATE : AccountService.Mode.VIEW);
    }

    /** ENTER with {@code validateOnly=true} (edit and ask for PF5), otherwise PF5 save. */
    @PutMapping({"", "/{acctId}"})
    public ScreenResponse<AccountDetails> update(@PathVariable(required = false) String acctId,
            @RequestBody AccountUpdateRequest request,
            @RequestParam(defaultValue = "false") boolean validateOnly) {
        return service.update(acctId, request, validateOnly);
    }
}
