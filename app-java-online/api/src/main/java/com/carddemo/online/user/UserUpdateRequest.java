package com.carddemo.online.user;

import jakarta.validation.constraints.Size;

/** COUSR2AI input fields (FNAME, LNAME, PASSWD, USRTYPE); the user id is the path variable. */
public record UserUpdateRequest(
        @Size(max = 20, message = "First Name can NOT exceed 20 characters...") String firstName,
        @Size(max = 20, message = "Last Name can NOT exceed 20 characters...") String lastName,
        @Size(max = 8, message = "Password can NOT exceed 8 characters...") String password,
        @Size(max = 1, message = "User Type can NOT exceed 1 character...") String userType) {
}
