package com.carddemo.online.session;

import com.carddemo.online.common.ScreenResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Every /api endpoint except /api/auth/** requires a session token. Without one the COBOL programs
 * (EIBCALEN = 0) transfer back to the sign-on program, so a 401 carries {@code nextProgram=COSGN00C}.
 */
@Component
public class SessionAuthFilter extends OncePerRequestFilter {

    public static final String SESSION_USER_ATTR = SessionUser.class.getName();

    private final SessionTokenService tokens;
    private final ObjectMapper mapper;

    public SessionAuthFilter(SessionTokenService tokens, ObjectMapper mapper) {
        this.tokens = tokens;
        this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/") || path.startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        Optional<SessionUser> user = header != null && header.startsWith("Bearer ")
                ? tokens.verify(header.substring(7).trim())
                : Optional.empty();
        if (user.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(),
                    ScreenResponse.error("Please sign on ...", null).withNextProgram("COSGN00C"));
            return;
        }
        request.setAttribute(SESSION_USER_ATTR, user.get());
        chain.doFilter(request, response);
    }
}
