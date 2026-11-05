package com.carddemo.online.signon;

/**
 * One CDEMO-MENU-OPT (COMEN02Y) or CDEMO-ADMIN-OPT (COADM02Y) entry.
 *
 * @param userType CDEMO-MENU-OPT-USRTYPE ('U' or 'A'); null for admin menu options, which have none
 */
public record MenuOption(int number, String name, String program, String userType) {
}
