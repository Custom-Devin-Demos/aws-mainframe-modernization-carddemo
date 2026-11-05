package com.carddemo.online.signon;

import java.util.List;

/** Output of BUILD-MENU-OPTIONS: the OPTN001O..OPTN012O lines of COMEN01 / COADM01. */
public record MenuScreen(List<MenuOption> options) {
}
