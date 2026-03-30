package by.frozzel.backend.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

public final class CookieBuilder {
    private CookieBuilder() {}

    public static void apply(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(false);
        cookie.setMaxAge(maxAgeSeconds);
        response.addCookie(cookie);
    }
}

