package com.carddemo.online.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.online.support.AbstractIntegrationTest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The WHEN OTHER (unexpected RESP) branches, simulated with a failing USRSEC store. */
class UserAdminFailureIT extends AbstractIntegrationTest {

    private static final DataAccessResourceFailureException IO_ERROR = new DataAccessResourceFailureException("io");

    @MockitoBean
    UserSecurityStore store;

    @Autowired
    PasswordEncoder passwordEncoder;

    private ResultActions admin(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(as(ADMIN, request));
    }

    private UserRecord user0001() {
        return new UserRecord("USER0001", "LAWRENCE", "THOMAS", passwordEncoder.encode("PASSWORD"), "U");
    }

    @Test
    void browseFailures() throws Exception {
        // STARTBR / READNEXT / READPREV: WHEN OTHER
        when(store.browseFrom(anyString(), anyInt())).thenThrow(IO_ERROR);
        when(store.existsAfter(anyString())).thenThrow(IO_ERROR);
        when(store.browseBefore(anyString(), anyInt())).thenThrow(IO_ERROR);
        for (MockHttpServletRequestBuilder req : new MockHttpServletRequestBuilder[] {get("/api/users"),
                get("/api/users").param("after", "USER0005"),
                get("/api/users").param("before", "USER0001").param("page", "2")}) {
            admin(req)
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Unable to lookup User..."))
                    .andExpect(jsonPath("$.field").value("startUserId"));
        }
    }

    @Test
    void writeFailure() throws Exception {
        // COUSR01C WRITE-USER-SEC-FILE: WHEN OTHER
        doThrow(IO_ERROR).when(store).insert(any());
        admin(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(
                "{\"firstName\":\"A\",\"lastName\":\"B\",\"userId\":\"NEWUSER1\",\"password\":\"P\",\"userType\":\"U\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unable to Add User..."))
                .andExpect(jsonPath("$.field").value("firstName"));
    }

    @Test
    void readFailure() throws Exception {
        // COUSR02C / COUSR03C READ-USER-SEC-FILE: WHEN OTHER
        when(store.readForUpdate(anyString())).thenThrow(IO_ERROR);
        for (MockHttpServletRequestBuilder req : new MockHttpServletRequestBuilder[] {get("/api/users/USER0001"),
                delete("/api/users/USER0001")}) {
            admin(req)
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("Unable to lookup User..."))
                    .andExpect(jsonPath("$.field").value("firstName"));
        }
    }

    @Test
    void rewriteFailureAndNotFound() throws Exception {
        when(store.readForUpdate("USER0001")).thenReturn(Optional.of(user0001()));
        MockHttpServletRequestBuilder req = put("/api/users/USER0001").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"LARRY\",\"lastName\":\"THOMAS\",\"password\":\"PASSWORD\",\"userType\":\"U\"}");
        // UPDATE-USER-SEC-FILE: WHEN OTHER
        when(store.rewrite(any())).thenThrow(IO_ERROR);
        admin(req)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unable to Update User..."))
                .andExpect(jsonPath("$.field").value("firstName"));
        // UPDATE-USER-SEC-FILE: NOTFND
        doReturn(0).when(store).rewrite(any());
        admin(req)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User ID NOT found..."))
                .andExpect(jsonPath("$.field").value("userId"));
    }

    @Test
    void deleteFailureAndNotFound() throws Exception {
        when(store.readForUpdate("USER0001")).thenReturn(Optional.of(user0001()));
        // DELETE-USER-SEC-FILE: WHEN OTHER
        when(store.delete("USER0001")).thenThrow(IO_ERROR);
        admin(delete("/api/users/USER0001"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Unable to Update User..."))
                .andExpect(jsonPath("$.field").value("firstName"));
        // DELETE-USER-SEC-FILE: NOTFND
        doReturn(0).when(store).delete("USER0001");
        admin(delete("/api/users/USER0001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User ID NOT found..."))
                .andExpect(jsonPath("$.field").value("userId"));
    }
}
