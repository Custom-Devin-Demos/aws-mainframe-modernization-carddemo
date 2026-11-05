package com.carddemo.online.user;

/** User fields shown on the COUSR00-COUSR03 maps; the password is never returned. */
public record UserView(String userId, String firstName, String lastName, String userType) {
}
