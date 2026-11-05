package com.carddemo.online.signon;

import com.carddemo.online.common.CardDemoException;
import com.carddemo.online.common.CobolText;
import com.carddemo.online.common.ScreenResponse;
import com.carddemo.online.domain.UserSecurity;
import com.carddemo.online.domain.UserSecurityRepository;
import com.carddemo.online.session.SessionTokenService;
import com.carddemo.online.session.SessionUser;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** COSGN00C PROCESS-ENTER-KEY and READ-USER-SEC-FILE. */
@Service
public class SignonService {

    static final String MSG_ENTER_USER_ID = "Please enter User ID ...";
    static final String MSG_ENTER_PASSWORD = "Please enter Password ...";
    static final String MSG_WRONG_PASSWORD = "Wrong Password. Try again ...";
    static final String MSG_USER_NOT_FOUND = "User not found. Try again ...";
    static final String MSG_UNABLE_TO_VERIFY = "Unable to verify the User ...";
    /** CCDA-MSG-THANK-YOU (CSMSG01Y), trailing padding removed. */
    static final String MSG_THANK_YOU = "Thank you for using CardDemo application...";

    private final UserSecurityRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SessionTokenService tokens;

    public SignonService(UserSecurityRepository users, PasswordEncoder passwordEncoder, SessionTokenService tokens) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    public ScreenResponse<SignonResult> signon(SignonRequest request) {
        String userIdIn = request == null ? null : request.userId();
        String passwordIn = request == null ? null : request.password();

        // PROCESS-ENTER-KEY: USERIDI = SPACES OR LOW-VALUES
        if (CobolText.isBlank(userIdIn)) {
            throw CardDemoException.validation(MSG_ENTER_USER_ID, "userId");
        }
        // PROCESS-ENTER-KEY: PASSWDI = SPACES OR LOW-VALUES
        if (CobolText.isBlank(passwordIn)) {
            throw CardDemoException.validation(MSG_ENTER_PASSWORD, "password");
        }

        // MOVE FUNCTION UPPER-CASE(USERIDI / PASSWDI) TO WS-USER-ID / WS-USER-PWD
        String userId = CobolText.rtrim(userIdIn).toUpperCase(Locale.ROOT);
        String password = CobolText.rtrim(passwordIn).toUpperCase(Locale.ROOT);

        Optional<UserSecurity> found;
        try {
            found = users.findById(userId);
        } catch (DataAccessException e) {
            // READ-USER-SEC-FILE: WHEN OTHER
            throw CardDemoException.failure(MSG_UNABLE_TO_VERIFY, "userId");
        }
        // READ-USER-SEC-FILE: WHEN 13 (NOTFND)
        UserSecurity sec = found.orElseThrow(() -> CardDemoException.notFound(MSG_USER_NOT_FOUND, "userId"));

        // READ-USER-SEC-FILE: SEC-USR-PWD NOT = WS-USER-PWD
        if (!passwordEncoder.matches(password, sec.getPasswordHash())) {
            throw new CardDemoException(HttpStatus.UNAUTHORIZED, MSG_WRONG_PASSWORD, "password", null);
        }

        SessionUser user = new SessionUser(sec.getUsrId(), sec.getUsrType());
        // IF CDEMO-USRTYP-ADMIN XCTL COADM01C ELSE XCTL COMEN01C
        String next = user.isAdmin() ? "COADM01C" : "COMEN01C";
        return ScreenResponse.of(new SignonResult(tokens.issue(user), user)).withNextProgram(next);
    }

    /** MAIN-PARA WHEN DFHPF3: SEND TEXT CCDA-MSG-THANK-YOU, then RETURN without TRANSID. */
    public ScreenResponse<Void> signoff() {
        return ScreenResponse.of(null, MSG_THANK_YOU);
    }
}
