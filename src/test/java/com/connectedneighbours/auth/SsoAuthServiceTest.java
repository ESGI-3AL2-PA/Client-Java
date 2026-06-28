package com.connectedneighbours.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.connectedneighbours.auth.exception.MfaRequiredException;
import com.connectedneighbours.model.User;
import okhttp3.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SsoAuthServiceTest {

    @Test
    void login_success_returnsUserAndStoresSession() throws IOException {
        OkHttpClient client = mock(OkHttpClient.class);
        PersistentCookieJar cookieJar = mock(PersistentCookieJar.class);
        JwtVerifier jwtVerifier = mock(JwtVerifier.class);
        Call call = mock(Call.class);

        String token = validJwt();
        Response response = response(200,
                "{\"access_token\":\"" + token + "\",\"user\":{\"id\":\"u1\",\"email\":\"user@test.com\"}}"
        );

        when(client.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response);

        SsoAuthService service = new SsoAuthService(client, cookieJar, jwtVerifier);

        User user = service.login("user@test.com", "secret");

        assertEquals("u1", user.getId());
        assertEquals("user@test.com", user.getEmail());
        assertTrue(service.isAuthenticated());
        assertEquals(user, service.getCurrentUser());
    }

    @Test
    void login_mfaRequired_throwsExceptionWithMfaToken() throws IOException {
        OkHttpClient client = mock(OkHttpClient.class);
        PersistentCookieJar cookieJar = mock(PersistentCookieJar.class);
        JwtVerifier jwtVerifier = mock(JwtVerifier.class);
        Call call = mock(Call.class);

        when(client.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response(202, "{\"mfa_token\":\"mfa-token\"}"));

        SsoAuthService service = new SsoAuthService(client, cookieJar, jwtVerifier);

        MfaRequiredException ex = assertThrows(
                MfaRequiredException.class,
                () -> service.login("user@test.com", "secret")
        );

        assertEquals("mfa-token", ex.getMfaToken());
    }

    @Test
    void refresh_success_updatesAccessToken() throws IOException {
        OkHttpClient client = mock(OkHttpClient.class);
        PersistentCookieJar cookieJar = mock(PersistentCookieJar.class);
        JwtVerifier jwtVerifier = mock(JwtVerifier.class);
        Call call = mock(Call.class);

        when(cookieJar.findByName("csrf_token")).thenReturn("csrf");
        when(client.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response(200, "{\"access_token\":\"" + validJwt() + "\"}"));

        SsoAuthService service = new SsoAuthService(client, cookieJar, jwtVerifier);

        service.refresh();

        assertTrue(service.isAuthenticated());
    }

    @Test
    void refresh_failure_throwsIOException() throws IOException {
        OkHttpClient client = mock(OkHttpClient.class);
        PersistentCookieJar cookieJar = mock(PersistentCookieJar.class);
        JwtVerifier jwtVerifier = mock(JwtVerifier.class);
        Call call = mock(Call.class);

        when(cookieJar.findByName("csrf_token")).thenReturn("csrf");
        when(client.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(response(401, "{\"error\":\"unauthorized\"}"));

        SsoAuthService service = new SsoAuthService(client, cookieJar, jwtVerifier);

        assertThrows(IOException.class, service::refresh);
    }

    @Test
    void logout_clearsCookiesAndSessionState() throws IOException {
        OkHttpClient client = mock(OkHttpClient.class);
        PersistentCookieJar cookieJar = mock(PersistentCookieJar.class);
        JwtVerifier jwtVerifier = mock(JwtVerifier.class);
        Call call = mock(Call.class);

        String token = validJwt();
        Response loginResponse = response(200,
                "{\"access_token\":\"" + token + "\",\"user\":{\"id\":\"u1\",\"email\":\"user@test.com\"}}"
        );
        Response logoutResponse = response(204, "");

        when(cookieJar.findByName("csrf_token")).thenReturn("csrf");
        when(client.newCall(any())).thenReturn(call);
        when(call.execute()).thenReturn(loginResponse, logoutResponse);

        SsoAuthService service = new SsoAuthService(client, cookieJar, jwtVerifier);
        service.login("user@test.com", "secret");

        service.logout();

        verify(cookieJar).clear();
        assertFalse(service.isAuthenticated());
        assertNull(service.getCurrentUser());
    }

    private static Response response(int code, String body) {
        Request request = new Request.Builder().url("http://localhost/test").build();
        return new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test")
                .body(ResponseBody.create(body, MediaType.get("application/json; charset=utf-8")))
                .build();
    }

    private static String validJwt() {
        return JWT.create()
                .withExpiresAt(Instant.now().plusSeconds(3600))
                .sign(Algorithm.HMAC256("test-secret"));
    }
}
