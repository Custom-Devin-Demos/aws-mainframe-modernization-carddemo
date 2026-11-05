package com.carddemo.online.user;

/** One USRSEC row (CSUSR01Y SEC-USER-DATA) with the password kept as its BCrypt hash. */
record UserRecord(String userId, String firstName, String lastName, String passwordHash, String userType) {

    UserView toView() {
        return new UserView(userId, firstName, lastName, userType);
    }
}
