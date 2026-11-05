package com.carddemo.online.signon;

import com.carddemo.online.common.ScreenResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** COSGN00C (CC00), sign-on screen COSGN00. Public: no session exists yet. */
@RestController
@RequestMapping("/api/auth")
public class SignonController {

    private final SignonService signon;

    public SignonController(SignonService signon) {
        this.signon = signon;
    }

    @PostMapping("/signon")
    public ScreenResponse<SignonResult> signon(@RequestBody(required = false) SignonRequest request) {
        return signon.signon(request);
    }

    @PostMapping("/signoff")
    public ScreenResponse<Void> signoff() {
        return signon.signoff();
    }
}
