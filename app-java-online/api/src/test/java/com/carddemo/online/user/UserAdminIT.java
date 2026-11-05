package com.carddemo.online.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.online.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * COUSR00C-COUSR03C against the sample USRSEC data (ADMIN001-ADMIN005, USER0001-USER0005; every sample password
 * is PASSWORD: the trailing A/U in DUSRSECJ is SEC-USR-TYPE).
 */
class UserAdminIT extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void reset() {
        resetSampleData();
    }

    private ResultActions admin(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(as(ADMIN, request));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String addBody(String fname, String lname, String userId, String pwd, String type) {
        return "{\"firstName\":" + q(fname) + ",\"lastName\":" + q(lname) + ",\"userId\":" + q(userId)
                + ",\"password\":" + q(pwd) + ",\"userType\":" + q(type) + "}";
    }

    private static String updateBody(String fname, String lname, String pwd, String type) {
        return "{\"firstName\":" + q(fname) + ",\"lastName\":" + q(lname) + ",\"password\":" + q(pwd)
                + ",\"userType\":" + q(type) + "}";
    }

    private static String q(String v) {
        return v == null ? "null" : "\"" + v + "\"";
    }

    private void insertUsers(String prefix, int count) {
        for (int i = 1; i <= count; i++) {
            jdbc.update("INSERT INTO user_security (usr_id, first_name, last_name, password_hash, usr_type)"
                    + " VALUES (?, 'F', 'L', 'x', 'U')", prefix + String.format("%03d", i));
        }
    }

    @Nested
    class Authorization {

        @Test
        void regularUserIsRejectedOnEveryEndpoint() throws Exception {
            for (MockHttpServletRequestBuilder req : new MockHttpServletRequestBuilder[] {get("/api/users"),
                    json(post("/api/users"), addBody("A", "B", "NEWUSER1", "PWD", "U")),
                    get("/api/users/USER0001"), json(put("/api/users/USER0001"), updateBody("A", "B", "C", "U")),
                    delete("/api/users/USER0001")}) {
                mvc.perform(as(USER, req))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.message").value("No access - Admin Only option..."));
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_security", Integer.class)).isEqualTo(10);
        }

        @Test
        void noSessionGoesToSignOn() throws Exception {
            // MAIN-PARA: EIBCALEN = 0
            mvc.perform(get("/api/users"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.nextProgram").value("COSGN00C"));
        }
    }

    @Nested
    class ListUsers {

        @Test
        void enterShowsFirstTenUsersAndHitsBottomOnLookAhead() throws Exception {
            // PROCESS-PAGE-FORWARD: 10 rows read, look-ahead READNEXT gets ENDFILE
            admin(get("/api/users"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(10))
                    .andExpect(jsonPath("$.data.items[0].userId").value("ADMIN001"))
                    .andExpect(jsonPath("$.data.items[0].firstName").value("MARGARET"))
                    .andExpect(jsonPath("$.data.items[0].lastName").value("GOLD"))
                    .andExpect(jsonPath("$.data.items[0].userType").value("A"))
                    .andExpect(jsonPath("$.data.items[0].password").doesNotExist())
                    .andExpect(jsonPath("$.data.items[0].passwordHash").doesNotExist())
                    .andExpect(jsonPath("$.data.items[9].userId").value("USER0005"))
                    .andExpect(jsonPath("$.data.pageNumber").value(1))
                    .andExpect(jsonPath("$.data.firstKey").value("ADMIN001"))
                    .andExpect(jsonPath("$.data.lastKey").value("USER0005"))
                    .andExpect(jsonPath("$.data.hasPrevPage").value(false))
                    .andExpect(jsonPath("$.data.hasNextPage").value(false))
                    .andExpect(jsonPath("$.message").value("You have reached the bottom of the page..."))
                    .andExpect(jsonPath("$.field").value("startUserId"));
        }

        @Test
        void enterWithSearchUserIdStartsAtThatKey() throws Exception {
            // PROCESS-ENTER-KEY: USRIDINI -> SEC-USR-ID, STARTBR GTEQ; ENDFILE while filling the screen
            admin(get("/api/users").param("startUserId", "USER0003"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(3))
                    .andExpect(jsonPath("$.data.items[0].userId").value("USER0003"))
                    .andExpect(jsonPath("$.data.pageNumber").value(1))
                    .andExpect(jsonPath("$.message").value("You have reached the bottom of the page..."));
            admin(get("/api/users").param("startUserId", "B"))
                    .andExpect(jsonPath("$.data.items[0].userId").value("USER0001"));
        }

        @Test
        void enterPastLastKeyIsNotFound() throws Exception {
            // STARTBR-USER-SEC-FILE: NOTFND
            admin(get("/api/users").param("startUserId", "ZZZZZZZZ"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(0))
                    .andExpect(jsonPath("$.data.pageNumber").value(0))
                    .andExpect(jsonPath("$.data.hasNextPage").value(false))
                    .andExpect(jsonPath("$.message").value("You are at the top of the page..."))
                    .andExpect(jsonPath("$.field").value("startUserId"));
        }

        @Test
        void pf8WithoutNextPageSaysAlreadyAtBottom() throws Exception {
            // PROCESS-PF8-KEY: NEXT-PAGE-NO
            admin(get("/api/users").param("after", "USER0005").param("page", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(jsonPath("$.message").value("You are already at the bottom of the page..."))
                    .andExpect(jsonPath("$.field").value("startUserId"));
        }

        @Test
        void pf7OnFirstPageSaysAlreadyAtTop() throws Exception {
            // PROCESS-PF7-KEY: CDEMO-CU00-PAGE-NUM not > 1 (even if keys exist before the search start)
            admin(get("/api/users").param("before", "USER0003").param("page", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(jsonPath("$.message").value("You are already at the top of the page..."));
        }

        @Test
        void pagesForwardAndBackwardTenAtATime() throws Exception {
            insertUsers("TEST", 15); // ADMIN001-005, TEST001-015, USER0001-0005 = 25 users
            // PROCESS-PAGE-FORWARD: 10 rows and an 11th exists -> NEXT-PAGE-YES, no message
            admin(get("/api/users"))
                    .andExpect(jsonPath("$.data.items.length()").value(10))
                    .andExpect(jsonPath("$.data.lastKey").value("TEST005"))
                    .andExpect(jsonPath("$.data.pageNumber").value(1))
                    .andExpect(jsonPath("$.data.hasNextPage").value(true))
                    .andExpect(jsonPath("$.message").doesNotExist());
            // PROCESS-PF8-KEY -> PROCESS-PAGE-FORWARD from CDEMO-CU00-USRID-LAST (exclusive)
            admin(get("/api/users").param("after", "TEST005").param("page", "1"))
                    .andExpect(jsonPath("$.data.items[0].userId").value("TEST006"))
                    .andExpect(jsonPath("$.data.items[9].userId").value("TEST015"))
                    .andExpect(jsonPath("$.data.pageNumber").value(2))
                    .andExpect(jsonPath("$.data.hasPrevPage").value(true))
                    .andExpect(jsonPath("$.data.hasNextPage").value(true))
                    .andExpect(jsonPath("$.message").doesNotExist());
            // READNEXT-USER-SEC-FILE: ENDFILE while filling page 3
            admin(get("/api/users").param("after", "TEST015").param("page", "2"))
                    .andExpect(jsonPath("$.data.items.length()").value(5))
                    .andExpect(jsonPath("$.data.items[0].userId").value("USER0001"))
                    .andExpect(jsonPath("$.data.pageNumber").value(3))
                    .andExpect(jsonPath("$.data.hasNextPage").value(false))
                    .andExpect(jsonPath("$.message").value("You have reached the bottom of the page..."));
            // PROCESS-PAGE-BACKWARD: 10 rows and an 11th before them -> page - 1, no message
            admin(get("/api/users").param("before", "USER0001").param("page", "3"))
                    .andExpect(jsonPath("$.data.items.length()").value(10))
                    .andExpect(jsonPath("$.data.items[0].userId").value("TEST006"))
                    .andExpect(jsonPath("$.data.items[9].userId").value("TEST015"))
                    .andExpect(jsonPath("$.data.pageNumber").value(2))
                    .andExpect(jsonPath("$.data.hasNextPage").value(true))
                    .andExpect(jsonPath("$.message").doesNotExist());
            // PROCESS-PAGE-BACKWARD: exactly 10 rows, look-ahead READPREV gets ENDFILE -> page 1
            admin(get("/api/users").param("before", "TEST006").param("page", "2"))
                    .andExpect(jsonPath("$.data.items[0].userId").value("ADMIN001"))
                    .andExpect(jsonPath("$.data.items[9].userId").value("TEST005"))
                    .andExpect(jsonPath("$.data.pageNumber").value(1))
                    .andExpect(jsonPath("$.data.hasPrevPage").value(false))
                    .andExpect(jsonPath("$.message").value("You have reached the top of the page..."));
        }

        @Test
        void pf7WithFewerThanTenPreviousKeepsPageNumber() throws Exception {
            // READPREV-USER-SEC-FILE: ENDFILE while filling the screen (page number not recomputed)
            admin(get("/api/users").param("before", "ADMIN003").param("page", "2"))
                    .andExpect(jsonPath("$.data.items.length()").value(2))
                    .andExpect(jsonPath("$.data.items[0].userId").value("ADMIN001"))
                    .andExpect(jsonPath("$.data.items[1].userId").value("ADMIN002"))
                    .andExpect(jsonPath("$.data.pageNumber").value(2))
                    .andExpect(jsonPath("$.message").value("You have reached the top of the page..."));
        }

        @Test
        void selectionUAndDTransferToUpdateAndDeletePrograms() throws Exception {
            // PROCESS-ENTER-KEY: CDEMO-CU00-USR-SEL-FLG 'U'/'u' -> XCTL COUSR02C, 'D'/'d' -> XCTL COUSR03C
            for (String sel : new String[] {"U", "u"}) {
                admin(get("/api/users").param("sel", sel).param("selUserId", "USER0001"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.nextProgram").value("COUSR02C"))
                        .andExpect(jsonPath("$.data").doesNotExist());
            }
            for (String sel : new String[] {"D", "d"}) {
                admin(get("/api/users").param("sel", sel).param("selUserId", "USER0001"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.nextProgram").value("COUSR03C"));
            }
        }

        @Test
        void invalidSelectionKeepsListAndShowsMessage() throws Exception {
            // PROCESS-ENTER-KEY: WHEN OTHER -> 'Invalid selection. Valid values are U and D'
            admin(get("/api/users").param("sel", "X").param("selUserId", "USER0001"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Invalid selection. Valid values are U and D"))
                    .andExpect(jsonPath("$.messageType").value("ERROR"))
                    .andExpect(jsonPath("$.field").value("startUserId"))
                    .andExpect(jsonPath("$.nextProgram").doesNotExist())
                    .andExpect(jsonPath("$.data.items.length()").value(10));
        }

        @Test
        void selectionOnEmptyRowIsIgnored() throws Exception {
            // PROCESS-ENTER-KEY: CDEMO-CU00-USR-SELECTED = SPACES -> no transfer, list re-read
            admin(get("/api/users").param("sel", "U").param("selUserId", " "))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nextProgram").doesNotExist())
                    .andExpect(jsonPath("$.data.items.length()").value(10));
        }
    }

    @Nested
    class AddUser {

        @Test
        void addsUserWithHashedPassword() throws Exception {
            // WRITE-USER-SEC-FILE: NORMAL
            admin(json(post("/api/users"), addBody("JANE", "DOE", "NEWUSER1", "SECRET1", "U")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.message").value("User NEWUSER1 has been added ..."))
                    .andExpect(jsonPath("$.messageType").value("SUCCESS"))
                    .andExpect(jsonPath("$.field").value("firstName"))
                    .andExpect(jsonPath("$.data.userId").value("NEWUSER1"))
                    .andExpect(jsonPath("$.data.password").doesNotExist());
            String hash = jdbc.queryForObject("SELECT password_hash FROM user_security WHERE usr_id = 'NEWUSER1'",
                    String.class);
            assertThat(hash).isNotEqualTo("SECRET1");
            assertThat(passwordEncoder.matches("SECRET1", hash)).isTrue();
            assertThat(jdbc.queryForObject("SELECT first_name || '|' || last_name || '|' || usr_type"
                    + " FROM user_security WHERE usr_id = 'NEWUSER1'", String.class)).isEqualTo("JANE|DOE|U");
        }

        @Test
        void validatesFieldsInCobolOrder() throws Exception {
            // PROCESS-ENTER-KEY EVALUATE: FNAME, LNAME, USERID, PASSWD, USRTYPE
            expectAddError(addBody(null, null, null, null, null), "First Name can NOT be empty...", "firstName");
            expectAddError(addBody("  ", "DOE", "NEWUSER1", "PWD", "U"), "First Name can NOT be empty...",
                    "firstName");
            expectAddError(addBody("JANE", "", "NEWUSER1", "PWD", "U"), "Last Name can NOT be empty...", "lastName");
            expectAddError(addBody("JANE", "DOE", " ", "PWD", "U"), "User ID can NOT be empty...", "userId");
            expectAddError(addBody("JANE", "DOE", "NEWUSER1", "", "U"), "Password can NOT be empty...", "password");
            expectAddError(addBody("JANE", "DOE", "NEWUSER1", "PWD", " "), "User Type can NOT be empty...",
                    "userType");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_security", Integer.class)).isEqualTo(10);
        }

        @Test
        void rejectsDuplicateUserId() throws Exception {
            // WRITE-USER-SEC-FILE: DUPKEY / DUPREC
            admin(json(post("/api/users"), addBody("JANE", "DOE", "ADMIN001", "PWD", "A")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("User ID already exist..."))
                    .andExpect(jsonPath("$.field").value("userId"));
            assertThat(jdbc.queryForObject("SELECT first_name FROM user_security WHERE usr_id = 'ADMIN001'",
                    String.class)).isEqualTo("MARGARET");
        }

        @Test
        void rejectsValuesLongerThanTheMapField() throws Exception {
            // BMS field lengths (USERID/PASSWD 8, FNAME/LNAME 20, USRTYPE 1)
            expectAddError(addBody("JANE", "DOE", "NEWUSER12", "PWD", "U"), "User ID can NOT exceed 8 characters...",
                    "userId");
            expectAddError(addBody("JANE", "DOE", "NEWUSER1", "PWD", "UU"),
                    "User Type can NOT exceed 1 character...", "userType");
        }

        private void expectAddError(String body, String message, String field) throws Exception {
            admin(json(post("/api/users"), body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(message))
                    .andExpect(jsonPath("$.messageType").value("ERROR"))
                    .andExpect(jsonPath("$.field").value(field));
        }
    }

    @Nested
    class FetchUser {

        @Test
        void fetchForUpdate() throws Exception {
            // COUSR02C READ-USER-SEC-FILE: NORMAL
            admin(get("/api/users/USER0001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").value("USER0001"))
                    .andExpect(jsonPath("$.data.firstName").value("LAWRENCE"))
                    .andExpect(jsonPath("$.data.lastName").value("THOMAS"))
                    .andExpect(jsonPath("$.data.userType").value("U"))
                    .andExpect(jsonPath("$.data.password").doesNotExist())
                    .andExpect(jsonPath("$.message").value("Press PF5 key to save your updates ..."))
                    .andExpect(jsonPath("$.messageType").value("INFO"));
        }

        @Test
        void fetchForDelete() throws Exception {
            // COUSR03C READ-USER-SEC-FILE: NORMAL
            admin(get("/api/users/ADMIN002").param("mode", "delete"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.firstName").value("RUSSELL"))
                    .andExpect(jsonPath("$.message").value("Press PF5 key to delete this user ..."));
        }

        @Test
        void unknownUser() throws Exception {
            // READ-USER-SEC-FILE: NOTFND
            for (String mode : new String[] {"update", "delete"}) {
                admin(get("/api/users/NOBODY").param("mode", mode))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.message").value("User ID NOT found..."))
                        .andExpect(jsonPath("$.field").value("userId"));
            }
        }

        @Test
        void blankUserId() throws Exception {
            // PROCESS-ENTER-KEY: USRIDINI = SPACES
            admin(get("/api/users/{id}", " "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("User ID can NOT be empty..."))
                    .andExpect(jsonPath("$.field").value("userId"));
        }
    }

    @Nested
    class UpdateUser {

        @Test
        void updatesNamesAndTypeKeepingPassword() throws Exception {
            // UPDATE-USER-SEC-FILE: NORMAL
            admin(json(put("/api/users/USER0001"), updateBody("LARRY", "THOMAS", "PASSWORD", "A")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("User USER0001 has been updated ..."))
                    .andExpect(jsonPath("$.messageType").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.firstName").value("LARRY"))
                    .andExpect(jsonPath("$.data.userType").value("A"))
                    .andExpect(jsonPath("$.data.password").doesNotExist());
            assertThat(passwordEncoder.matches("PASSWORD", hashOf("USER0001"))).isTrue();
        }

        @Test
        void passwordOnlyChangeCountsAsModified() throws Exception {
            // UPDATE-USER-INFO: PASSWDI NOT = SEC-USR-PWD
            admin(json(put("/api/users/USER0002"), updateBody("AJITH", "KUMAR", "NEWPASS", "U")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("User USER0002 has been updated ..."));
            assertThat(passwordEncoder.matches("NEWPASS", hashOf("USER0002"))).isTrue();
        }

        @Test
        void unchangedUserMustBeModified() throws Exception {
            // UPDATE-USER-INFO: USR-MODIFIED-NO
            String before = hashOf("USER0001");
            admin(json(put("/api/users/USER0001"), updateBody("LAWRENCE", "THOMAS", "PASSWORD", "U")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Please modify to update ..."))
                    .andExpect(jsonPath("$.messageType").value("ERROR"))
                    .andExpect(jsonPath("$.field").value("firstName"))
                    .andExpect(jsonPath("$.data.userId").value("USER0001"));
            assertThat(hashOf("USER0001")).isEqualTo(before);
        }

        @Test
        void validatesFieldsInCobolOrder() throws Exception {
            // UPDATE-USER-INFO EVALUATE: USRIDIN, FNAME, LNAME, PASSWD, USRTYPE
            expectUpdateError(" ", updateBody("A", "B", "C", "U"), "User ID can NOT be empty...", "userId");
            expectUpdateError("USER0001", updateBody(" ", " ", " ", " "), "First Name can NOT be empty...",
                    "firstName");
            expectUpdateError("USER0001", updateBody("A", "", "C", "U"), "Last Name can NOT be empty...", "lastName");
            expectUpdateError("USER0001", updateBody("A", "B", null, "U"), "Password can NOT be empty...",
                    "password");
            expectUpdateError("USER0001", updateBody("A", "B", "C", ""), "User Type can NOT be empty...", "userType");
            assertThat(jdbc.queryForObject("SELECT first_name FROM user_security WHERE usr_id = 'USER0001'",
                    String.class)).isEqualTo("LAWRENCE");
        }

        @Test
        void unknownUser() throws Exception {
            // UPDATE-USER-INFO -> READ-USER-SEC-FILE: NOTFND
            admin(json(put("/api/users/NOBODY"), updateBody("A", "B", "C", "U")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("User ID NOT found..."))
                    .andExpect(jsonPath("$.field").value("userId"));
        }

        private void expectUpdateError(String userId, String body, String message, String field) throws Exception {
            admin(json(put("/api/users/{id}", userId), body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(message))
                    .andExpect(jsonPath("$.field").value(field));
        }

        private String hashOf(String userId) {
            return jdbc.queryForObject("SELECT password_hash FROM user_security WHERE usr_id = ?", String.class,
                    userId);
        }
    }

    @Nested
    class DeleteUser {

        @Test
        void deletesUser() throws Exception {
            // DELETE-USER-SEC-FILE: NORMAL
            admin(delete("/api/users/USER0005"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("User USER0005 has been deleted ..."))
                    .andExpect(jsonPath("$.messageType").value("SUCCESS"))
                    .andExpect(jsonPath("$.field").value("userId"))
                    .andExpect(jsonPath("$.data").doesNotExist());
            admin(get("/api/users/USER0005")).andExpect(status().isNotFound());
        }

        @Test
        void unknownUser() throws Exception {
            // DELETE-USER-INFO -> READ-USER-SEC-FILE: NOTFND
            admin(delete("/api/users/NOBODY"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("User ID NOT found..."))
                    .andExpect(jsonPath("$.field").value("userId"));
        }

        @Test
        void blankUserId() throws Exception {
            // DELETE-USER-INFO: USRIDINI = SPACES
            admin(delete("/api/users/{id}", " "))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("User ID can NOT be empty..."))
                    .andExpect(jsonPath("$.field").value("userId"));
        }
    }
}
