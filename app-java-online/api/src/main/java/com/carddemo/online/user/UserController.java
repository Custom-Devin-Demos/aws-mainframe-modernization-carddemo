package com.carddemo.online.user;

import com.carddemo.online.common.CardDemoException;
import com.carddemo.online.common.KeysetPage;
import com.carddemo.online.common.ScreenResponse;
import com.carddemo.online.session.SessionUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** User administration (COUSR00C-COUSR03C), reachable only from the admin menu COADM01C. */
@RestController
@RequestMapping("/api/users")
public class UserController {

    /** COMEN01C's text for admin-only options. */
    static final String MSG_ADMIN_ONLY = "No access - Admin Only option...";

    private final UserListService list;
    private final UserMaintenanceService maintenance;

    public UserController(UserListService list, UserMaintenanceService maintenance) {
        this.list = list;
        this.maintenance = maintenance;
    }

    /**
     * COUSR00C. {@code after}/{@code before} = PF8/PF7 from the current page ({@code page} is the current page
     * number, CDEMO-CU00-PAGE-NUM); otherwise ENTER, with an optional {@code sel}/{@code selUserId} row selection.
     */
    @GetMapping
    public ScreenResponse<KeysetPage<UserView>> list(SessionUser user,
            @RequestParam(required = false) String startUserId,
            @RequestParam(required = false) String after,
            @RequestParam(required = false) String before,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(required = false) String sel,
            @RequestParam(required = false) String selUserId) {
        user.requireAdmin(MSG_ADMIN_ONLY);
        if (after != null && before != null) {
            throw CardDemoException.validation("Specify only one of after or before", null);
        }
        if (after != null) {
            return list.next(after, page);
        }
        if (before != null) {
            return list.previous(before, page);
        }
        return list.enter(startUserId, sel, selUserId);
    }

    /** COUSR01C. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScreenResponse<UserView> add(SessionUser user, @Valid @RequestBody UserAddRequest request) {
        user.requireAdmin(MSG_ADMIN_ONLY);
        return maintenance.add(request);
    }

    /** COUSR02C ({@code mode=update}, default) or COUSR03C ({@code mode=delete}) fetch. */
    @GetMapping("/{userId}")
    public ScreenResponse<UserView> fetch(SessionUser user, @PathVariable String userId,
            @RequestParam(defaultValue = "update") String mode) {
        user.requireAdmin(MSG_ADMIN_ONLY);
        return maintenance.fetch(userId, "delete".equalsIgnoreCase(mode));
    }

    /** COUSR02C PF5 (save). */
    @PutMapping("/{userId}")
    public ScreenResponse<UserView> update(SessionUser user, @PathVariable String userId,
            @Valid @RequestBody UserUpdateRequest request) {
        user.requireAdmin(MSG_ADMIN_ONLY);
        return maintenance.update(userId, request);
    }

    /** COUSR03C PF5 (delete). */
    @DeleteMapping("/{userId}")
    public ScreenResponse<UserView> delete(SessionUser user, @PathVariable String userId) {
        user.requireAdmin(MSG_ADMIN_ONLY);
        return maintenance.delete(userId);
    }
}
