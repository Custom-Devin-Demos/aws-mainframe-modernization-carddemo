package com.carddemo.online.session;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.online.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class SessionAuthIT extends AbstractIntegrationTest {

    @Test
    void apiWithoutTokenRedirectsToSignOn() throws Exception {
        mvc.perform(get("/api/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.messageType").value("ERROR"))
                .andExpect(jsonPath("$.nextProgram").value("COSGN00C"));
    }

    @Test
    void apiWithTokenReturnsCommareaUser() throws Exception {
        mvc.perform(as(ADMIN, get("/api/session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("ADMIN001"))
                .andExpect(jsonPath("$.data.userType").value("A"));
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
