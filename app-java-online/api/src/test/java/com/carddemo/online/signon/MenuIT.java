package com.carddemo.online.signon;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.online.session.SessionUser;
import com.carddemo.online.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class MenuIT extends AbstractIntegrationTest {

    private ResultActions select(SessionUser user, String path, String option) throws Exception {
        String body = option == null ? "{}" : "{\"option\":\"" + option + "\"}";
        return mvc.perform(as(user, post(path).contentType(MediaType.APPLICATION_JSON).content(body)));
    }

    // EIBCALEN = 0 -> RETURN-TO-SIGNON-SCREEN
    @Test
    void menusRequireSession() throws Exception {
        mvc.perform(get("/api/menu"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.nextProgram").value("COSGN00C"));
        mvc.perform(post("/api/admin/menu/select"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.nextProgram").value("COSGN00C"));
    }

    // COMEN01C SEND-MENU-SCREEN / BUILD-MENU-OPTIONS (COMEN02Y, 11 options)
    @Test
    void mainMenuListsCopybookOptions() throws Exception {
        mvc.perform(as(USER, get("/api/menu")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.options.length()").value(11))
                .andExpect(jsonPath("$.data.options[0].number").value(1))
                .andExpect(jsonPath("$.data.options[0].name").value("Account View"))
                .andExpect(jsonPath("$.data.options[0].program").value("COACTVWC"))
                .andExpect(jsonPath("$.data.options[10].name").value("Pending Authorization View"))
                .andExpect(jsonPath("$.data.options[10].userType").value("U"));
    }

    // COMEN01C PROCESS-ENTER-KEY: WHEN OTHER -> XCTL CDEMO-MENU-OPT-PGMNAME(WS-OPTION)
    @ParameterizedTest
    @CsvSource({"1,COACTVWC", "2,COACTUPC", "3,COCRDLIC", "4,COCRDSLC", "5,COCRDUPC", "6,COTRN00C",
        "7,COTRN01C", "8,COTRN02C", "9,CORPT00C", "10,COBIL00C", "01,COACTVWC", "' 3',COCRDLIC", "'3 ',COCRDLIC"})
    void mainMenuOptionTransfers(String option, String program) throws Exception {
        select(USER, "/api/menu/select", option)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextProgram").value(program))
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    // COMEN01C PROCESS-ENTER-KEY: CDEMO-USRTYP-USER check only blocks 'U' users; admins pass through
    @Test
    void adminCanUseMainMenu() throws Exception {
        select(ADMIN, "/api/menu/select", "1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextProgram").value("COACTVWC"));
    }

    // COMEN01C PROCESS-ENTER-KEY: WHEN 'COPAUS0C', INQUIRE PROGRAM not NORMAL -> 'is not installed...'
    @Test
    void pendingAuthorizationIsNotInstalled() throws Exception {
        select(USER, "/api/menu/select", "11")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("This option Pending Authorization View is not installed..."))
                .andExpect(jsonPath("$.messageType").value("ERROR"))
                .andExpect(jsonPath("$.field").value("option"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist())
                .andExpect(jsonPath("$.data.options.length()").value(11));
    }

    // COMEN01C PROCESS-ENTER-KEY: NOT NUMERIC OR > CDEMO-MENU-OPT-COUNT OR = ZEROS
    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "0", "00", "12", "99", "A", "1A", "-1", "123"})
    void mainMenuInvalidOption(String option) throws Exception {
        select(USER, "/api/menu/select", option)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please enter a valid option number..."))
                .andExpect(jsonPath("$.messageType").value("ERROR"))
                .andExpect(jsonPath("$.field").value("option"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist())
                .andExpect(jsonPath("$.data.options.length()").value(11));
    }

    // COMEN01C PROCESS-ENTER-KEY: OPTIONI = LOW-VALUES (option missing)
    @Test
    void mainMenuMissingOption() throws Exception {
        select(USER, "/api/menu/select", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please enter a valid option number..."));
    }

    // COADM01C SEND-MENU-SCREEN / BUILD-MENU-OPTIONS (COADM02Y, 6 options)
    @Test
    void adminMenuListsCopybookOptions() throws Exception {
        mvc.perform(as(ADMIN, get("/api/admin/menu")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.options.length()").value(6))
                .andExpect(jsonPath("$.data.options[0].name").value("User List (Security)"))
                .andExpect(jsonPath("$.data.options[0].program").value("COUSR00C"))
                .andExpect(jsonPath("$.data.options[5].name").value("Transaction Type Maintenance (Db2)"));
    }

    // Admin menu is only reachable for CDEMO-USRTYP-ADMIN (COSGN00C XCTLs regular users to COMEN01C)
    @Test
    void adminMenuForbiddenForRegularUser() throws Exception {
        mvc.perform(as(USER, get("/api/admin/menu")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No access - Admin Only option..."));
        select(USER, "/api/admin/menu/select", "1")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No access - Admin Only option..."));
    }

    // COADM01C PROCESS-ENTER-KEY: XCTL CDEMO-ADMIN-OPT-PGMNAME(WS-OPTION)
    @ParameterizedTest
    @CsvSource({"1,COUSR00C", "2,COUSR01C", "3,COUSR02C", "4,COUSR03C", "04,COUSR03C"})
    void adminMenuOptionTransfers(String option, String program) throws Exception {
        select(ADMIN, "/api/admin/menu/select", option)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextProgram").value(program))
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    // COADM01C PGMIDERR-ERR-PARA: COTRTLIC / COTRTUPC are not installed
    @ParameterizedTest
    @ValueSource(strings = {"5", "6"})
    void adminDb2OptionsAreNotInstalled(String option) throws Exception {
        select(ADMIN, "/api/admin/menu/select", option)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("This option is not installed ..."))
                .andExpect(jsonPath("$.messageType").value("INFO"))
                .andExpect(jsonPath("$.field").value("option"))
                .andExpect(jsonPath("$.nextProgram").doesNotExist());
    }

    // COADM01C PROCESS-ENTER-KEY: NOT NUMERIC OR > CDEMO-ADMIN-OPT-COUNT OR = ZEROS
    @ParameterizedTest
    @ValueSource(strings = {"", "0", "7", "10", "X"})
    void adminMenuInvalidOption(String option) throws Exception {
        select(ADMIN, "/api/admin/menu/select", option)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please enter a valid option number..."))
                .andExpect(jsonPath("$.field").value("option"))
                .andExpect(jsonPath("$.data.options.length()").value(6));
    }
}
