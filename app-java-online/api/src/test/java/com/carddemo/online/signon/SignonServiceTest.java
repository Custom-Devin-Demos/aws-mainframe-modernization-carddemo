package com.carddemo.online.signon;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carddemo.online.common.CardDemoException;
import com.carddemo.online.domain.UserSecurityRepository;
import com.carddemo.online.session.SessionProperties;
import com.carddemo.online.session.SessionTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class SignonServiceTest {

    // READ-USER-SEC-FILE: WHEN OTHER (any RESP other than NORMAL / NOTFND)
    @Test
    void unreadableUserFile() {
        UserSecurityRepository repo = mock(UserSecurityRepository.class);
        when(repo.findById(anyString())).thenThrow(new DataAccessResourceFailureException("down"));
        SignonService service = new SignonService(repo, new BCryptPasswordEncoder(),
                new SessionTokenService(new SessionProperties("x".repeat(32), 60)));

        assertThatThrownBy(() -> service.signon(new SignonRequest("USER0001", "PASSWORD")))
                .isInstanceOfSatisfying(CardDemoException.class, e -> {
                    org.assertj.core.api.Assertions.assertThat(e.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    org.assertj.core.api.Assertions.assertThat(e.getMessage()).isEqualTo("Unable to verify the User ...");
                    org.assertj.core.api.Assertions.assertThat(e.getField()).isEqualTo("userId");
                });
    }
}
