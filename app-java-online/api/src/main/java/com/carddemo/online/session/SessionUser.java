package com.carddemo.online.session;

import com.carddemo.online.common.CardDemoException;

/** The user part of CARDDEMO-COMMAREA (CDEMO-USER-ID / CDEMO-USER-TYPE), carried in the session token. */
public record SessionUser(String userId, String userType) {

    public static final String ADMIN = "A";
    public static final String USER = "U";

    public boolean isAdmin() {
        return ADMIN.equals(userType);
    }

    /** Guards admin-only functions; {@code message} should be the COBOL text where one exists. */
    public void requireAdmin(String message) {
        if (!isAdmin()) {
            throw CardDemoException.forbidden(message);
        }
    }
}
