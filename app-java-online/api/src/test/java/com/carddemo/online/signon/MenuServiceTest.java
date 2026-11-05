package com.carddemo.online.signon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.online.common.CardDemoException;
import com.carddemo.online.common.MessageType;
import com.carddemo.online.common.ScreenResponse;
import com.carddemo.online.session.SessionUser;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Branches the shipped COMEN02Y/COADM02Y tables cannot reach (no 'A' or DUMMY entries). */
class MenuServiceTest {

    private static final SessionUser USER = new SessionUser("USER0001", SessionUser.USER);
    private static final SessionUser ADMIN = new SessionUser("ADMIN001", SessionUser.ADMIN);

    private final MenuService service = new MenuService(
            List.of(new MenuOption(1, "Account View", "COACTVWC", "U"),
                    new MenuOption(2, "Secret Admin Thing", "COUSR00C", "A"),
                    new MenuOption(3, "Future Feature", "DUMMY001", "U")),
            List.of(new MenuOption(1, "User List (Security)", "COUSR00C", null),
                    new MenuOption(2, "Later", "DUMMYADM", null)),
            Set.of("COACTVWC", "COUSR00C"));

    // COMEN01C PROCESS-ENTER-KEY: CDEMO-USRTYP-USER AND CDEMO-MENU-OPT-USRTYPE(WS-OPTION) = 'A'
    @Test
    void adminOnlyOptionRejectedForUser() {
        assertThatThrownBy(() -> service.selectMain(USER, "2"))
                .isInstanceOfSatisfying(CardDemoException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(e.getMessage()).isEqualTo("No access - Admin Only option...");
                    assertThat(e.getField()).isEqualTo("option");
                });
    }

    @Test
    void adminOnlyOptionAllowedForAdmin() {
        assertThat(service.selectMain(ADMIN, "2").nextProgram()).isEqualTo("COUSR00C");
    }

    // COMEN01C PROCESS-ENTER-KEY: PGMNAME(1:5) = 'DUMMY' -> name DELIMITED BY SPACE (no space before 'is')
    @Test
    void dummyMainOptionIsComingSoon() {
        ScreenResponse<MenuScreen> r = service.selectMain(USER, "3");
        assertThat(r.message()).isEqualTo("This option Futureis coming soon ...");
        assertThat(r.messageType()).isEqualTo(MessageType.INFO);
        assertThat(r.nextProgram()).isNull();
    }

    // COADM01C PROCESS-ENTER-KEY: PGMNAME(1:5) = 'DUMMY'
    @Test
    void dummyAdminOptionIsNotInstalled() {
        ScreenResponse<MenuScreen> r = service.selectAdmin("2");
        assertThat(r.message()).isEqualTo("This option is not installed ...");
        assertThat(r.nextProgram()).isNull();
    }

    @Test
    void delimitedByTwoSpacesKeepsSingleSpaces() {
        assertThat(MenuService.delimitedBy("Pending Authorization View", "  ")).isEqualTo("Pending Authorization View");
        assertThat(MenuService.delimitedBy("Pending Authorization View", " ")).isEqualTo("Pending");
    }
}
