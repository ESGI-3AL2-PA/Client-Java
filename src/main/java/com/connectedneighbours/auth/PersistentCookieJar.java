package com.connectedneighbours.auth;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * CookieJar persistant base sur java.util.prefs : conserve le refresh_token
 * (cookie HttpOnly) et le csrf_token entre les redémarrages de l'app.
 * <p>
 * L'auth-service pose ces cookies sur le path "/auth" avec sameSite=lax.
 */
public class PersistentCookieJar implements CookieJar {

    private static final Preferences PREFS = Preferences.userNodeForPackage(PersistentCookieJar.class);
    private static final String KEY_COOKIES = "auth.cookies";

    private static boolean sameCookie(Cookie a, Cookie b) {
        return a.name().equals(b.name())
                && a.domain().equals(b.domain())
                && a.path().equals(b.path());
    }

    @Override
    public void saveFromResponse(@NotNull HttpUrl url, List<Cookie> cookies) {
        List<Cookie> current = loadAll();
        for (Cookie incoming : cookies) {
            current.removeIf(c -> sameCookie(c, incoming));
            if (incoming.expiresAt() >= System.currentTimeMillis()) {
                current.add(incoming);
            }
        }
        persist(current);
    }

    @NotNull
    @Override
    public List<Cookie> loadForRequest(@NotNull HttpUrl url) {
        long now = System.currentTimeMillis();
        List<Cookie> valid = new ArrayList<>();
        List<Cookie> all = loadAll();
        for (Cookie c : all) {
            if (c.expiresAt() < now) continue;
            if (c.matches(url)) valid.add(c);
        }
        return valid;
    }

    /**
     * Supprime tous les cookies (logout).
     */
    public void clear() {
        PREFS.remove(KEY_COOKIES);
    }

    /**
     * Extrait la valeur d'un cookie par nom (utile pour récupérer le csrf_token).
     */
    public String findByName(String name) {
        long now = System.currentTimeMillis();
        for (Cookie c : loadAll()) {
            if (c.expiresAt() >= now && c.name().equals(name)) {
                return c.value();
            }
        }
        return null;
    }

    private List<Cookie> loadAll() {
        List<Cookie> list = new ArrayList<>();
        String raw = PREFS.get(KEY_COOKIES, null);
        if (raw == null || raw.isEmpty()) return list;
        for (String part : raw.split("\n")) {
            if (part.isBlank()) continue;
            try {
                Cookie c = Cookie.parse(HttpUrl.parse("http://localhost/"), part);
                if (c != null) list.add(c);
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    private void persist(List<Cookie> cookies) {
        StringBuilder sb = new StringBuilder();
        for (Cookie c : cookies) {
            sb.append(c.toString()).append('\n');
        }
        if (sb.length() == 0) {
            PREFS.remove(KEY_COOKIES);
        } else {
            PREFS.put(KEY_COOKIES, sb.toString());
        }
    }
}
