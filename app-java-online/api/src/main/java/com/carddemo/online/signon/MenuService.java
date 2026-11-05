package com.carddemo.online.signon;

import com.carddemo.online.common.CardDemoException;
import com.carddemo.online.common.CobolText;
import com.carddemo.online.common.MessageType;
import com.carddemo.online.common.ScreenResponse;
import com.carddemo.online.session.SessionUser;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** PROCESS-ENTER-KEY of COMEN01C (main menu) and COADM01C (admin menu). */
@Service
public class MenuService {

    static final String MSG_INVALID_OPTION = "Please enter a valid option number...";
    static final String MSG_ADMIN_ONLY = "No access - Admin Only option...";
    static final String MSG_ADMIN_NOT_INSTALLED = "This option is not installed ...";
    static final String FIELD_OPTION = "option";

    private final List<MenuOption> mainOptions;
    private final List<MenuOption> adminOptions;
    private final Set<String> installed;

    public MenuService() {
        this(MenuOptions.MAIN, MenuOptions.ADMIN, MenuOptions.INSTALLED_PROGRAMS);
    }

    MenuService(List<MenuOption> mainOptions, List<MenuOption> adminOptions, Set<String> installed) {
        this.mainOptions = mainOptions;
        this.adminOptions = adminOptions;
        this.installed = installed;
    }

    public MenuScreen mainMenu() {
        return new MenuScreen(mainOptions);
    }

    public MenuScreen adminMenu() {
        return new MenuScreen(adminOptions);
    }

    /** COMEN01C PROCESS-ENTER-KEY. */
    public ScreenResponse<MenuScreen> selectMain(SessionUser user, String rawOption) {
        MenuScreen screen = mainMenu();
        MenuOption option = mainOptions.get(parseOption(rawOption, mainOptions.size(), screen) - 1);

        // IF CDEMO-USRTYP-USER AND CDEMO-MENU-OPT-USRTYPE(WS-OPTION) = 'A'
        if (SessionUser.USER.equals(user.userType()) && "A".equals(option.userType())) {
            throw new CardDemoException(HttpStatus.FORBIDDEN, MSG_ADMIN_ONLY, FIELD_OPTION, screen);
        }

        String program = option.program();
        // WHEN CDEMO-MENU-OPT-PGMNAME(WS-OPTION)(1:5) = 'DUMMY' (green): name DELIMITED BY SPACE
        if (program.startsWith("DUMMY")) {
            return new ScreenResponse<>(screen,
                    "This option " + delimitedBy(option.name(), " ") + "is coming soon ...",
                    MessageType.INFO, FIELD_OPTION, null);
        }
        // WHEN ... = 'COPAUS0C': EXEC CICS INQUIRE PROGRAM, not NORMAL (red): name DELIMITED BY '  '
        if (!installed.contains(program)) {
            return new ScreenResponse<>(screen,
                    "This option " + delimitedBy(option.name(), "  ") + " is not installed...",
                    MessageType.ERROR, FIELD_OPTION, null);
        }
        // WHEN OTHER: XCTL PROGRAM(CDEMO-MENU-OPT-PGMNAME(WS-OPTION))
        return ScreenResponse.of(screen).withNextProgram(program);
    }

    /** COADM01C PROCESS-ENTER-KEY. */
    public ScreenResponse<MenuScreen> selectAdmin(String rawOption) {
        MenuScreen screen = adminMenu();
        MenuOption option = adminOptions.get(parseOption(rawOption, adminOptions.size(), screen) - 1);
        String program = option.program();
        // DUMMY options and XCTL failing with PGMIDERR (PGMIDERR-ERR-PARA) send the same green text
        if (program.startsWith("DUMMY") || !installed.contains(program)) {
            return new ScreenResponse<>(screen, MSG_ADMIN_NOT_INSTALLED, MessageType.INFO, FIELD_OPTION, null);
        }
        return ScreenResponse.of(screen).withNextProgram(program);
    }

    /**
     * Right-trims OPTIONI, moves it into WS-OPTION-X (PIC X(02) JUST RIGHT), replaces spaces by zeros
     * and checks {@code WS-OPTION IS NOT NUMERIC OR WS-OPTION > count OR WS-OPTION = ZEROS}.
     */
    static int parseOption(String raw, int count, MenuScreen screen) {
        String trimmed = CobolText.rtrim(raw).replace('\u0000', ' ');
        if (trimmed.length() <= 2) {
            String x = (" ".repeat(2 - trimmed.length()) + trimmed).replace(' ', '0');
            if (Character.isDigit(x.charAt(0)) && Character.isDigit(x.charAt(1))) {
                int value = Integer.parseInt(x);
                if (value > 0 && value <= count) {
                    return value;
                }
            }
        }
        throw CardDemoException.validation(MSG_INVALID_OPTION, FIELD_OPTION).withData(screen);
    }

    /** STRING ... DELIMITED BY {@code delimiter} on the space-padded PIC X(35) option name. */
    static String delimitedBy(String name, String delimiter) {
        int idx = name.indexOf(delimiter);
        return idx < 0 ? name : name.substring(0, idx);
    }
}
