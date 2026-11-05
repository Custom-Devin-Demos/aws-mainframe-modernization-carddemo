package com.carddemo.online.signon;

import java.util.List;
import java.util.Set;

/** Option tables from copybooks COMEN02Y (main menu) and COADM02Y (admin menu). */
public final class MenuOptions {

    private MenuOptions() {
    }

    /** COMEN02Y CARDDEMO-MAIN-MENU-OPTIONS (CDEMO-MENU-OPT-COUNT = 11). */
    public static final List<MenuOption> MAIN = List.of(
            new MenuOption(1, "Account View", "COACTVWC", "U"),
            new MenuOption(2, "Account Update", "COACTUPC", "U"),
            new MenuOption(3, "Credit Card List", "COCRDLIC", "U"),
            new MenuOption(4, "Credit Card View", "COCRDSLC", "U"),
            new MenuOption(5, "Credit Card Update", "COCRDUPC", "U"),
            new MenuOption(6, "Transaction List", "COTRN00C", "U"),
            new MenuOption(7, "Transaction View", "COTRN01C", "U"),
            new MenuOption(8, "Transaction Add", "COTRN02C", "U"),
            new MenuOption(9, "Transaction Reports", "CORPT00C", "U"),
            new MenuOption(10, "Bill Payment", "COBIL00C", "U"),
            new MenuOption(11, "Pending Authorization View", "COPAUS0C", "U"));

    /** COADM02Y CARDDEMO-ADMIN-MENU-OPTIONS (CDEMO-ADMIN-OPT-COUNT = 6). */
    public static final List<MenuOption> ADMIN = List.of(
            new MenuOption(1, "User List (Security)", "COUSR00C", null),
            new MenuOption(2, "User Add (Security)", "COUSR01C", null),
            new MenuOption(3, "User Update (Security)", "COUSR02C", null),
            new MenuOption(4, "User Delete (Security)", "COUSR03C", null),
            new MenuOption(5, "Transaction Type List/Update (Db2)", "COTRTLIC", null),
            new MenuOption(6, "Transaction Type Maintenance (Db2)", "COTRTUPC", null));

    /**
     * Programs defined to "CICS" in this re-platform (the 17 online programs). Anything else answers
     * like EXEC CICS INQUIRE PROGRAM / HANDLE CONDITION PGMIDERR would for an uninstalled program.
     */
    public static final Set<String> INSTALLED_PROGRAMS = Set.of(
            "COSGN00C", "COMEN01C", "COADM01C",
            "COACTVWC", "COACTUPC", "COCRDLIC", "COCRDSLC", "COCRDUPC",
            "COTRN00C", "COTRN01C", "COTRN02C", "COBIL00C", "CORPT00C",
            "COUSR00C", "COUSR01C", "COUSR02C", "COUSR03C");
}
