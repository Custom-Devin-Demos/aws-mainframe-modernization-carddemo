package com.carddemo.online.user;

import com.carddemo.online.common.CardDemoException;
import com.carddemo.online.common.CobolText;
import com.carddemo.online.common.ScreenResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** COUSR01C (add), COUSR02C (update) and COUSR03C (delete). */
@Service
public class UserMaintenanceService {

    static final String MSG_FNAME_EMPTY = "First Name can NOT be empty...";
    static final String MSG_LNAME_EMPTY = "Last Name can NOT be empty...";
    static final String MSG_USERID_EMPTY = "User ID can NOT be empty...";
    static final String MSG_PASSWORD_EMPTY = "Password can NOT be empty...";
    static final String MSG_USRTYPE_EMPTY = "User Type can NOT be empty...";
    static final String MSG_ALREADY_EXISTS = "User ID already exist...";
    static final String MSG_ADD_FAILED = "Unable to Add User...";
    static final String MSG_NOT_FOUND = "User ID NOT found...";
    static final String MSG_LOOKUP_FAILED = "Unable to lookup User...";
    static final String MSG_UPDATE_FAILED = "Unable to Update User...";
    static final String MSG_PRESS_PF5_UPDATE = "Press PF5 key to save your updates ...";
    static final String MSG_PRESS_PF5_DELETE = "Press PF5 key to delete this user ...";
    static final String MSG_NOT_MODIFIED = "Please modify to update ...";

    private final UserSecurityStore store;
    private final PasswordEncoder passwordEncoder;

    public UserMaintenanceService(UserSecurityStore store, PasswordEncoder passwordEncoder) {
        this.store = store;
        this.passwordEncoder = passwordEncoder;
    }

    /** COUSR01C PROCESS-ENTER-KEY + WRITE-USER-SEC-FILE. */
    @Transactional
    public ScreenResponse<UserView> add(UserAddRequest req) {
        requireFilled(req.firstName(), MSG_FNAME_EMPTY, "firstName");
        requireFilled(req.lastName(), MSG_LNAME_EMPTY, "lastName");
        requireFilled(req.userId(), MSG_USERID_EMPTY, "userId");
        requireFilled(req.password(), MSG_PASSWORD_EMPTY, "password");
        requireFilled(req.userType(), MSG_USRTYPE_EMPTY, "userType");
        UserRecord user = new UserRecord(CobolText.rtrim(req.userId()), CobolText.rtrim(req.firstName()),
                CobolText.rtrim(req.lastName()), passwordEncoder.encode(CobolText.rtrim(req.password())),
                CobolText.rtrim(req.userType()));
        try {
            store.insert(user);
        } catch (DuplicateKeyException e) {
            // WRITE-USER-SEC-FILE: DUPKEY / DUPREC
            throw CardDemoException.conflict(MSG_ALREADY_EXISTS, "userId");
        } catch (DataAccessException e) {
            throw CardDemoException.failure(MSG_ADD_FAILED, "firstName");
        }
        return ScreenResponse.success(user.toView(), "User " + delimitedBySpace(user.userId()) + " has been added ...")
                .withField("firstName");
    }

    /** COUSR02C / COUSR03C PROCESS-ENTER-KEY: fetch the user for update or delete. */
    @Transactional
    public ScreenResponse<UserView> fetch(String userId, boolean forDelete) {
        requireFilled(userId, MSG_USERID_EMPTY, "userId");
        UserRecord user = read(CobolText.rtrim(userId));
        return ScreenResponse.of(user.toView(), forDelete ? MSG_PRESS_PF5_DELETE : MSG_PRESS_PF5_UPDATE)
                .withField("userId");
    }

    /** COUSR02C UPDATE-USER-INFO + UPDATE-USER-SEC-FILE. */
    @Transactional
    public ScreenResponse<UserView> update(String userId, UserUpdateRequest req) {
        requireFilled(userId, MSG_USERID_EMPTY, "userId");
        requireFilled(req.firstName(), MSG_FNAME_EMPTY, "firstName");
        requireFilled(req.lastName(), MSG_LNAME_EMPTY, "lastName");
        requireFilled(req.password(), MSG_PASSWORD_EMPTY, "password");
        requireFilled(req.userType(), MSG_USRTYPE_EMPTY, "userType");
        UserRecord current = read(CobolText.rtrim(userId));
        String firstName = CobolText.rtrim(req.firstName());
        String lastName = CobolText.rtrim(req.lastName());
        String password = CobolText.rtrim(req.password());
        String userType = CobolText.rtrim(req.userType());
        boolean passwordChanged = !passwordEncoder.matches(password, current.passwordHash());
        boolean modified = !firstName.equals(current.firstName()) || !lastName.equals(current.lastName())
                || passwordChanged || !userType.equals(current.userType());
        if (!modified) {
            throw CardDemoException.validation(MSG_NOT_MODIFIED, "firstName").withData(current.toView());
        }
        UserRecord updated = new UserRecord(current.userId(), firstName, lastName,
                passwordChanged ? passwordEncoder.encode(password) : current.passwordHash(), userType);
        int count;
        try {
            count = store.rewrite(updated);
        } catch (DataAccessException e) {
            throw CardDemoException.failure(MSG_UPDATE_FAILED, "firstName");
        }
        if (count == 0) {
            throw CardDemoException.notFound(MSG_NOT_FOUND, "userId");
        }
        return ScreenResponse.success(updated.toView(),
                "User " + delimitedBySpace(updated.userId()) + " has been updated ...").withField("firstName");
    }

    /** COUSR03C DELETE-USER-INFO + DELETE-USER-SEC-FILE. */
    @Transactional
    public ScreenResponse<UserView> delete(String userId) {
        requireFilled(userId, MSG_USERID_EMPTY, "userId");
        UserRecord user = read(CobolText.rtrim(userId));
        int count;
        try {
            count = store.delete(user.userId());
        } catch (DataAccessException e) {
            throw CardDemoException.failure(MSG_UPDATE_FAILED, "firstName");
        }
        if (count == 0) {
            throw CardDemoException.notFound(MSG_NOT_FOUND, "userId");
        }
        return ScreenResponse.<UserView>success(null, "User " + delimitedBySpace(user.userId())
                + " has been deleted ...").withField("userId");
    }

    /** READ-USER-SEC-FILE (READ ... UPDATE). */
    private UserRecord read(String userId) {
        try {
            return store.readForUpdate(userId)
                    .orElseThrow(() -> CardDemoException.notFound(MSG_NOT_FOUND, "userId"));
        } catch (DataAccessException e) {
            throw CardDemoException.failure(MSG_LOOKUP_FAILED, "firstName");
        }
    }

    private static void requireFilled(String value, String message, String field) {
        if (CobolText.isBlank(value)) {
            throw CardDemoException.validation(message, field);
        }
    }

    /** {@code STRING SEC-USR-ID DELIMITED BY SPACE}. */
    private static String delimitedBySpace(String value) {
        int space = value.indexOf(' ');
        return space < 0 ? value : value.substring(0, space);
    }
}
