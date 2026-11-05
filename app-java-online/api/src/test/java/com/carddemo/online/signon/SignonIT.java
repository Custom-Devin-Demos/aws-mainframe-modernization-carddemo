package com.carddemo.online.signon;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.online.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class SignonIT extends AbstractIntegrationTest {

    @Autowired
    ObjectMapper mapper;

    private ResultActions signon(String body) throws Exception {
        return mvc.perform(post("/api/auth/signon").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // READ-USER-SEC-FILE: WHEN 0, password matches, CDEMO-USRTYP-ADMIN -> XCTL COADM01C
    @Test
    void adminSignsOnToAdminMenu() throws Exception {
        String json = signon("{\"userId\":\"ADMIN001\",\"password\":\"PASSWORD\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextProgram").value("COADM01C"))
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.data.user.userId").value("ADMIN001"))
                .andExpect(jsonPath("$.data.user.userType").value("A"))
                .andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(json).at("/data/token").asText();
        mvc.perform(get("/api/session").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value("ADMIN001"));
    }

    // READ-USER-SEC-FILE: WHEN 0, password matches, regular user -> XCTL COMEN01C
    @Test
    void userSignsOnToMainMenu() throws Exception {
        signon("{\"userId\":\"USER0001\",\"password\":\"PASSWORD\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextProgram").value("COMEN01C"))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.user.userType").value("U"));
    }

    // PROCESS-ENTER-KEY: FUNCTION UPPER-CASE on USERIDI and PASSWDI
    @Test
    void userIdAndPasswordAreUpperCased() throws Exception {
        JsonNode body = mapper.readTree(signon("{\"userId\":\"user0001\",\"password\":\"password\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextProgram").value("COMEN01C"))
                .andReturn().getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(body.at("/data/user/userId").asText()).isEqualTo("USER0001");
    }

    // PROCESS-ENTER-KEY: USERIDI = SPACES OR LOW-VALUES
    @Test
    void blankUserId() throws Exception {
        signon("{\"userId\":\"   \",\"password\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please enter User ID ..."))
                .andExpect(jsonPath("$.messageType").value("ERROR"))
                .andExpect(jsonPath("$.field").value("userId"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist());
    }

    // PROCESS-ENTER-KEY: USERIDI = SPACES OR LOW-VALUES (field absent / no body)
    @Test
    void missingBody() throws Exception {
        mvc.perform(post("/api/auth/signon"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please enter User ID ..."))
                .andExpect(jsonPath("$.field").value("userId"));
    }

    // PROCESS-ENTER-KEY: PASSWDI = SPACES OR LOW-VALUES
    @Test
    void blankPassword() throws Exception {
        signon("{\"userId\":\"USER0001\",\"password\":\" \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please enter Password ..."))
                .andExpect(jsonPath("$.field").value("password"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist());
    }

    // READ-USER-SEC-FILE: WHEN 0, SEC-USR-PWD NOT = WS-USER-PWD
    @Test
    void wrongPassword() throws Exception {
        signon("{\"userId\":\"USER0001\",\"password\":\"WRONG\"}")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Wrong Password. Try again ..."))
                .andExpect(jsonPath("$.field").value("password"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.nextProgram").doesNotExist());
    }

    // READ-USER-SEC-FILE: WHEN 13 (NOTFND)
    @Test
    void userNotFound() throws Exception {
        signon("{\"userId\":\"NOBODY\",\"password\":\"PASSWORD\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found. Try again ..."))
                .andExpect(jsonPath("$.field").value("userId"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist());
    }

    // MAIN-PARA: WHEN DFHPF3 -> SEND TEXT CCDA-MSG-THANK-YOU
    @Test
    void signoffSendsThankYou() throws Exception {
        mvc.perform(post("/api/auth/signoff"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Thank you for using CardDemo application..."))
                .andExpect(jsonPath("$.messageType").value("INFO"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist());
    }
}
