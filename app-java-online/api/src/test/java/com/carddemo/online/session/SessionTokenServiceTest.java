package com.carddemo.online.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SessionTokenServiceTest {

    private static final SessionProperties PROPS =
            new SessionProperties("test-secret-test-secret-test-secret-123", 10);

    @Test
    void roundTrips() {
        SessionTokenService svc = new SessionTokenService(PROPS);
        String token = svc.issue(new SessionUser("ADMIN001", "A"));
        assertThat(svc.verify(token)).contains(new SessionUser("ADMIN001", "A"));
    }

    @Test
    void rejectsTamperedToken() {
        SessionTokenService svc = new SessionTokenService(PROPS);
        String token = svc.issue(new SessionUser("USER0001", "U"));
        String forged = new SessionTokenService(new SessionProperties("another-secret-another-secret-12345", 10))
                .issue(new SessionUser("USER0001", "A"));
        assertThat(svc.verify(token.substring(0, token.indexOf('.')) + forged.substring(forged.indexOf('.'))))
                .isEmpty();
        assertThat(svc.verify("garbage")).isEmpty();
        assertThat(svc.verify(null)).isEmpty();
    }

    @Test
    void rejectsExpiredToken() {
        Instant now = Instant.parse("2024-01-01T00:00:00Z");
        String token = new SessionTokenService(PROPS, Clock.fixed(now, ZoneOffset.UTC))
                .issue(new SessionUser("USER0001", "U"));
        SessionTokenService later =
                new SessionTokenService(PROPS, Clock.fixed(now.plus(Duration.ofMinutes(11)), ZoneOffset.UTC));
        assertThat(later.verify(token)).isEmpty();
    }
}
