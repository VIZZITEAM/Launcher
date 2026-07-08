package pro.gravit.launchserver.auth.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pro.gravit.launcher.base.Launcher;
import pro.gravit.launcher.base.events.request.AuthRequestEvent;
import pro.gravit.launcher.base.request.auth.AuthRequest;
import pro.gravit.launcher.base.request.auth.password.Auth2FAPassword;
import pro.gravit.launcher.base.request.auth.password.AuthMultiPassword;
import pro.gravit.launcher.base.request.auth.password.AuthPlainPassword;
import pro.gravit.launcher.base.request.auth.password.AuthTOTPPassword;
import pro.gravit.launchserver.LaunchServer;
import pro.gravit.launchserver.auth.AuthException;
import pro.gravit.launchserver.config.LaunchServerConfig;
import pro.gravit.launchserver.manangers.LaunchServerGsonManager;
import pro.gravit.launchserver.modules.impl.LaunchServerModulesManager;

import java.nio.file.Path;
import java.util.List;

class SiteHttpAuthCoreProviderTest {
    @TempDir
    static Path modulesDir;
    @TempDir
    static Path configDir;

    @BeforeAll
    static void prepareGson() {
        LaunchServerModulesManager modulesManager = new LaunchServerModulesManager(modulesDir, configDir, null);
        Launcher.gsonManager = new LaunchServerGsonManager(modulesManager);
        Launcher.gsonManager.initGson();
    }

    @Test
    void providerIsRegistered() {
        AuthCoreProvider.registerProviders();

        Assertions.assertEquals(SiteHttpAuthCoreProvider.class, AuthCoreProvider.providers.getClass("alamineHttp"));
        Assertions.assertEquals(SiteHttpAuthCoreProvider.class, AuthCoreProvider.providers.getClass("siteHttp"));
    }

    @Test
    void defaultConfigContainsLauncherApiBlock() {
        LaunchServerConfig config = LaunchServerConfig.getDefault(LaunchServer.LaunchServerEnv.TEST);

        Assertions.assertNotNull(config.launcherApi);
        Assertions.assertEquals("", config.launcherApi.launcherBootstrapUrl);
        Assertions.assertEquals("", config.launcherApi.siteApiBaseUrl);
    }

    @Test
    void extractsPlainPasswordWithoutTotp() throws AuthException {
        var extracted = SiteHttpAuthCoreProvider.extractPassword(new AuthPlainPassword("secret"));

        Assertions.assertEquals("secret", extracted.password());
        Assertions.assertNull(extracted.totp());
    }

    @Test
    void extractsPlainPasswordAndTotpFromTwoFactorPassword() throws AuthException {
        var extracted = SiteHttpAuthCoreProvider.extractPassword(
                new Auth2FAPassword(new AuthPlainPassword("secret"), new AuthTOTPPassword("123456")));

        Assertions.assertEquals("secret", extracted.password());
        Assertions.assertEquals("123456", extracted.totp());
    }

    @Test
    void extractsPlainPasswordAndTotpFromMultiPassword() throws AuthException {
        var password = new AuthMultiPassword();
        password.list = List.of(new AuthPlainPassword("secret"), new AuthTOTPPassword("654321"));

        var extracted = SiteHttpAuthCoreProvider.extractPassword(password);

        Assertions.assertEquals("secret", extracted.password());
        Assertions.assertEquals("654321", extracted.totp());
    }

    @Test
    void rejectsUnsupportedPasswordChain() {
        var password = new AuthMultiPassword();
        password.list = List.of(new AuthPlainPassword("secret"), new AuthPlainPassword("not-totp"));

        Assertions.assertThrows(AuthException.class, () -> SiteHttpAuthCoreProvider.extractPassword(password));
    }

    @Test
    void rejectsMissingPassword() {
        Assertions.assertThrows(AuthException.class, () -> SiteHttpAuthCoreProvider.extractPassword(new EmptyPassword()));
    }

    @Test
    void validatesRefreshTokenAgainstCachedSiteToken() {
        String hash = pro.gravit.launchserver.helper.LegacySessionHelper.makeRefreshTokenFromPassword("Player", "site-token", "salt");
        SiteHttpAuthCoreProvider.SiteHttpUser user = new SiteHttpAuthCoreProvider.SiteHttpUser(
                java.util.UUID.randomUUID(), "Player", "site-token",
                pro.gravit.launcher.base.ClientPermissions.DEFAULT, false);

        Assertions.assertTrue(SiteHttpAuthCoreProvider.isRefreshTokenValid("Player." + hash, user, "salt"));
        Assertions.assertFalse(SiteHttpAuthCoreProvider.isRefreshTokenValid("Player.bad", user, "salt"));
        Assertions.assertFalse(SiteHttpAuthCoreProvider.isRefreshTokenValid("Other." + hash, user, "salt"));
    }

    @Test
    void rejectsHttpErrorBeforeParsingSiteResponse() {
        Assertions.assertThrows(java.io.IOException.class,
                () -> SiteHttpAuthCoreProvider.validateSiteHttpResponse(500, "{\"status\":\"ok\"}"));
    }

    @Test
    void acceptsSiteAuthFailureJsonWithUnauthorizedHttpStatus() {
        Assertions.assertDoesNotThrow(
                () -> SiteHttpAuthCoreProvider.validateSiteHttpResponse(401, "{\"status\":\"invalid_credentials\"}"));
    }

    @Test
    void parsesRateLimitedSiteResponseToAuthStatus() throws java.io.IOException {
        var response = SiteHttpAuthCoreProvider.parseSiteAuthResponse(
                429, "{\"error\":\"RATE_LIMITED\",\"message\":\"Too many requests.\"}");

        Assertions.assertEquals("rate_limited", response.status);
        Assertions.assertEquals("Too many requests.", response.message);
    }

    @Test
    void derivesUserLookupUrlFromAuthUrl() {
        Assertions.assertEquals("https://site.example.test/api/launcher/users/00000000-0000-0000-0000-000000000001",
                SiteHttpAuthCoreProvider.userLookupUrl(
                        "https://site.example.test/api/launcher/auth",
                        java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")));
    }

    @Test
    void mapsSiteAuthFailuresToStableLauncherCodes() {
        Assertions.assertEquals(AuthRequestEvent.WRONG_PASSWORD_ERROR_MESSAGE,
                SiteHttpAuthCoreProvider.mapAuthFailure("invalid_credentials", null).getMessage());
        Assertions.assertEquals("auth.invalidotp",
                SiteHttpAuthCoreProvider.mapAuthFailure("invalid_otp", null).getMessage());
        Assertions.assertEquals(AuthRequestEvent.ACCOUNT_BLOCKED_ERROR_MESSAGE,
                SiteHttpAuthCoreProvider.mapAuthFailure("banned", null).getMessage());
        Assertions.assertEquals("auth.emailnotverified",
                SiteHttpAuthCoreProvider.mapAuthFailure("email_not_verified", null).getMessage());
        Assertions.assertEquals("auth.trylater",
                SiteHttpAuthCoreProvider.mapAuthFailure("rate_limited", null).getMessage());
    }

    private static class EmptyPassword implements AuthRequest.AuthPasswordInterface {
        @Override
        public boolean check() {
            return true;
        }
    }
}
