package com.carddemo.online.signon;

import com.carddemo.online.common.ScreenResponse;
import com.carddemo.online.session.SessionUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** COMEN01C (CM00) main menu and COADM01C (CA00) admin menu. */
@RestController
public class MenuController {

    private final MenuService menus;

    public MenuController(MenuService menus) {
        this.menus = menus;
    }

    @GetMapping("/api/menu")
    public ScreenResponse<MenuScreen> mainMenu(SessionUser user) {
        return ScreenResponse.of(menus.mainMenu());
    }

    @PostMapping("/api/menu/select")
    public ScreenResponse<MenuScreen> selectMain(SessionUser user,
            @RequestBody(required = false) MenuSelectRequest request) {
        return menus.selectMain(user, request == null ? null : request.option());
    }

    @GetMapping("/api/admin/menu")
    public ScreenResponse<MenuScreen> adminMenu(SessionUser user) {
        user.requireAdmin(MenuService.MSG_ADMIN_ONLY);
        return ScreenResponse.of(menus.adminMenu());
    }

    @PostMapping("/api/admin/menu/select")
    public ScreenResponse<MenuScreen> selectAdmin(SessionUser user,
            @RequestBody(required = false) MenuSelectRequest request) {
        user.requireAdmin(MenuService.MSG_ADMIN_ONLY);
        return menus.selectAdmin(request == null ? null : request.option());
    }
}
