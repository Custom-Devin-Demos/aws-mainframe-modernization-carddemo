package com.carddemo.online.session;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies stateless HMAC-SHA256 tokens of the form
 * {@code base64url(userId|userType|expiresEpochSeconds).base64url(hmac)}.
 */
@Service
public class SessionTokenService {

    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();

    private final byte[] key;
    private final long ttlSeconds;
    private final Clock clock;

    @Autowired
    public SessionTokenService(SessionProperties props) {
        this(props, Clock.systemUTC());
    }

    SessionTokenService(SessionProperties props, Clock clock) {
        if (props.secret() == null || props.secret().length() < 32) {
            throw new IllegalStateException("carddemo.session.secret must be at least 32 characters");
        }
        this.key = props.secret().getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = props.ttlMinutes() * 60;
        this.clock = clock;
    }

    public String issue(SessionUser user) {
        long exp = clock.instant().getEpochSecond() + ttlSeconds;
        String payload = ENC.encodeToString(
                (user.userId() + "|" + user.userType() + "|" + exp).getBytes(StandardCharsets.UTF_8));
        return payload + "." + ENC.encodeToString(sign(payload));
    }

    public Optional<SessionUser> verify(String token) {
        if (token == null) {
            return Optional.empty();
        }
        int dot = token.indexOf('.');
        if (dot <= 0) {
            return Optional.empty();
        }
        String payload = token.substring(0, dot);
        byte[] sig;
        String[] parts;
        try {
            sig = DEC.decode(token.substring(dot + 1));
            parts = new String(DEC.decode(payload), StandardCharsets.UTF_8).split("\\|", -1);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (!MessageDigest.isEqual(sig, sign(payload)) || parts.length != 3) {
            return Optional.empty();
        }
        long exp;
        try {
            exp = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (clock.instant().getEpochSecond() > exp) {
            return Optional.empty();
        }
        return Optional.of(new SessionUser(parts[0], parts[1]));
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
