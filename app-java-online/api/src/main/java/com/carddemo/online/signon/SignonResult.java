package com.carddemo.online.signon;

import com.carddemo.online.session.SessionUser;

/** Session token standing in for the CARDDEMO-COMMAREA passed on the XCTL to the menu. */
public record SignonResult(String token, SessionUser user) {
}
