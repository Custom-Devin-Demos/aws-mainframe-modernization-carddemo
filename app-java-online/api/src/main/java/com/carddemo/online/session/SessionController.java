package com.carddemo.online.session;

import com.carddemo.online.common.ScreenResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
public class SessionController {

    @GetMapping
    public ScreenResponse<SessionUser> current(SessionUser user) {
        return ScreenResponse.of(user);
    }
}
