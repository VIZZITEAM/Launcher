package pro.gravit.launchserver.auth.core;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pro.gravit.launcher.base.ClientPermissions;
import pro.gravit.launcher.base.Launcher;
import pro.gravit.launcher.base.events.request.AuthRequestEvent;
import pro.gravit.launcher.base.events.request.GetAvailabilityAuthRequestEvent;
import pro.gravit.launcher.base.request.auth.AuthRequest;
import pro.gravit.launcher.base.request.auth.details.AuthPasswordDetails;
import pro.gravit.launcher.base.request.auth.details.AuthTotpDetails;
import pro.gravit.launcher.base.request.auth.password.Auth2FAPassword;
import pro.gravit.launcher.base.request.auth.password.AuthMultiPassword;
import pro.gravit.launcher.base.request.auth.password.AuthPlainPassword;
import pro.gravit.launcher.base.request.auth.password.AuthTOTPPassword;
import pro.gravit.launchserver.LaunchServer;
import pro.gravit.launchserver.auth.AuthException;
import pro.gravit.launchserver.auth.AuthProviderPair;
import pro.gravit.launchserver.auth.core.interfaces.provider.AuthSupportSudo;
import pro.gravit.launchserver.helper.LegacySessionHelper;
import pro.gravit.launchserver.manangers.AuthManager;
import pro.gravit.launchserver.socket.Client;
import pro.gravit.launchserver.socket.response.auth.AuthResponse;
import pro.gravit.utils.helper.SecurityHelper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class SiteHttpAuthCoreProvider extends AuthCoreProvider implements AuthSupportSudo {
    private transient final Logger logger = LogManager.getLogger(getClass());

    public String url;
    public String bearerToken;
    public long expireSeconds = 3600;
    public int connectTimeoutMillis = 5000;
    public int requestTimeoutMillis = 10000;

    private transient HttpClient httpClient;
    private transient ConcurrentMap<String, SiteHttpUser> usersByUsername;
    private transient ConcurrentMap<UUID, SiteHttpUser> usersByUuid;
    private transient ConcurrentMap<String, SiteHttpUser> usersByMinecraftToken;

    @Override
    public void init(LaunchServer server, AuthProviderPair pair) {
        super.init(server, pair);
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMillis))
                .build();
        usersByUsername = new ConcurrentHashMap<>();
        usersByUuid = new ConcurrentHashMap<>();
        usersByMinecraftToken = new ConcurrentHashMap<>();
        if (url == null || url.isBlank()) {
            logger.error("siteHttp url cannot be empty");
        }
        if (bearerToken == null || bearerToken.isBlank()) {
            logger.error("siteHttp bearerToken cannot be empty");
        }
    }

    @Override
    public User getUserByUsername(String username) {
        if (username == null || usersByUsername == null) return null;
        return usersByUsername.get(username.toLowerCase());
    }

    @Override
    public User getUserByUUID(UUID uuid) {
        if (uuid == null || usersByUuid == null) return null;
        return usersByUuid.get(uuid);
    }

    @Override
    public List<GetAvailabilityAuthRequestEvent.AuthAvailabilityDetails> getDetails(Client client) {
        return List.of(new AuthPasswordDetails(), new AuthTotpDetails("HMAC", 6));
    }

    @Override
    public UserSession getUserSessionByOAuthAccessToken(String accessToken) throws OAuthAccessTokenExpired {
        if (accessToken == null) return null;
        try {
            var info = LegacySessionHelper.getJwtInfoFromAccessToken(accessToken, server.keyAgreementManager.ecdsaPublicKey);
            var user = usersByUuid.get(info.uuid());
            if (user == null) {
                user = fetchUserByUuid(info.uuid());
            }
            return user == null ? null : new SiteHttpUserSession(user, user.minecraftAccessToken, expireSeconds);
        } catch (ExpiredJwtException e) {
            throw new OAuthAccessTokenExpired();
        } catch (JwtException e) {
            return null;
        }
    }

    @Override
    public AuthManager.AuthReport refreshAccessToken(String refreshToken, AuthResponse.AuthContext context) {
        if (refreshToken == null || usersByUsername == null) return null;
        String[] parts = refreshToken.split("\\.", 2);
        if (parts.length != 2) return null;
        var user = usersByUsername.get(parts[0].toLowerCase());
        if (!isRefreshTokenValid(refreshToken, user, server.keyAgreementManager.legacySalt)) return null;
        return buildAuthReport(user, false);
    }

    @Override
    public AuthManager.AuthReport authorize(String login, AuthResponse.AuthContext context, AuthRequest.AuthPasswordInterface password, boolean minecraftAccess) throws IOException {
        if (login == null) throw AuthException.userNotFound();
        var extracted = extractPassword(password);
        var response = requestAuth(login, extracted.password(), extracted.totp(), minecraftAccess);
        return switch (response.status) {
            case "ok" -> {
                if (response.user == null) throw new IOException("Launcher auth API returned ok without user");
                yield buildAuthReport(cacheUser(response.user), minecraftAccess);
            }
            case "two_factor_required" -> throw AuthException.need2FA();
            case "invalid_credentials", "invalid_otp", "banned", "email_not_verified", "rate_limited" ->
                    throw mapAuthFailure(response.status, response.message);
            default -> throw new IOException("Unknown launcher auth status: " + response.status);
        };
    }

    @Override
    public User checkServer(Client client, String username, String serverID) {
        var user = (SiteHttpUser) getUserByUsername(username);
        if (user == null || serverID == null) return null;
        return username.equals(user.username) && serverID.equals(user.serverId) ? user : null;
    }

    @Override
    public boolean joinServer(Client client, String username, UUID uuid, String accessToken, String serverID) {
        if (client == null || !(client.getUser() instanceof SiteHttpUser user)) return false;
        boolean identityMatch = uuid == null ? user.username.equals(username) : user.uuid.equals(uuid);
        if (!identityMatch || !user.minecraftAccessToken.equals(accessToken)) return false;
        user.serverId = serverID;
        return true;
    }

    @Override
    public AuthManager.AuthReport sudo(User user, boolean shadow) throws IOException {
        return buildAuthReport((SiteHttpUser) user, true);
    }

    @Override
    public void close() {
    }

    static ExtractedPassword extractPassword(AuthRequest.AuthPasswordInterface password) throws AuthException {
        if (password instanceof AuthPlainPassword plain) {
            return new ExtractedPassword(plain.password, null);
        }
        if (password instanceof Auth2FAPassword twoFa) {
            return extractTwoFactor(twoFa.firstPassword, twoFa.secondPassword);
        }
        if (password instanceof AuthMultiPassword multi) {
            if (multi.list == null || multi.list.isEmpty()) throw AuthException.wrongPassword();
            if (multi.list.size() == 1) return extractPassword(multi.list.get(0));
            return extractTwoFactor(multi.list.get(0), multi.list.get(1));
        }
        throw AuthException.wrongPassword();
    }

    private static ExtractedPassword extractTwoFactor(AuthRequest.AuthPasswordInterface first, AuthRequest.AuthPasswordInterface second) throws AuthException {
        if (!(first instanceof AuthPlainPassword plain)) throw AuthException.wrongPassword();
        if (!(second instanceof AuthTOTPPassword totp)) throw AuthException.wrongPassword();
        return new ExtractedPassword(plain.password, totp.totp);
    }

    private SiteAuthResponse requestAuth(String login, String password, String totp, boolean minecraftAccess) throws IOException {
        if (httpClient == null) {
            httpClient = HttpClient.newHttpClient();
        }
        var body = Launcher.gsonManager.gson.toJson(new SiteAuthRequest(login, password, totp, minecraftAccess));
        var request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(requestTimeoutMillis))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + bearerToken)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return parseSiteAuthResponse(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Launcher auth API request interrupted", e);
        }
    }

    private SiteHttpUser fetchUserByUuid(UUID uuid) {
        if (uuid == null || url == null || url.isBlank()) {
            return null;
        }
        if (httpClient == null) {
            httpClient = HttpClient.newHttpClient();
        }
        var request = HttpRequest.newBuilder(URI.create(userLookupUrl(url, uuid)))
                .timeout(Duration.ofMillis(requestTimeoutMillis))
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + bearerToken)
                .GET()
                .build();
        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            var parsed = parseSiteAuthResponse(response.statusCode(), response.body());
            return parsed.user == null ? null : cacheUser(parsed.user);
        } catch (Exception e) {
            logger.warn("Failed to restore launcher user {} from site API: {}", uuid, e.toString());
            return null;
        }
    }

    static String userLookupUrl(String authUrl, UUID uuid) {
        String normalized = authUrl == null ? "" : authUrl.trim();
        if (normalized.endsWith("/auth")) {
            normalized = normalized.substring(0, normalized.length() - "/auth".length());
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized + "/users/" + uuid;
    }

    static boolean isRefreshTokenValid(String refreshToken, SiteHttpUser user, String salt) {
        if (refreshToken == null || user == null || user.siteAccessToken == null) return false;
        String[] parts = refreshToken.split("\\.", 2);
        if (parts.length != 2) return false;
        if (!parts[0].equals(user.username)) return false;
        String expected = LegacySessionHelper.makeRefreshTokenFromPassword(
                user.username, user.siteAccessToken, salt);
        return expected.equals(parts[1]);
    }

    static void validateSiteHttpResponse(int statusCode, String body) throws IOException {
        if (body == null || body.isBlank()) {
            throw new IOException("Launcher auth API returned empty response");
        }
        if (statusCode >= 500 || statusCode < 200) {
            throw new IOException("Launcher auth API returned HTTP " + statusCode);
        }
    }

    static SiteAuthResponse parseSiteAuthResponse(int statusCode, String body) throws IOException {
        validateSiteHttpResponse(statusCode, body);
        var parsed = Launcher.gsonManager.gson.fromJson(body, SiteAuthResponse.class);
        if (parsed != null && parsed.status != null) {
            return parsed;
        }
        if (statusCode == 429) {
            var error = Launcher.gsonManager.gson.fromJson(body, SiteErrorResponse.class);
            if (error != null && ("RATE_LIMITED".equals(error.error) || "rate_limited".equals(error.status))) {
                var rateLimited = new SiteAuthResponse();
                rateLimited.status = "rate_limited";
                rateLimited.message = error.message;
                rateLimited.requestId = error.requestId;
                return rateLimited;
            }
        }
        throw new IOException("Launcher auth API returned invalid JSON with HTTP " + statusCode);
    }

    static AuthException mapAuthFailure(String status, String message) {
        return switch (status) {
            case "invalid_credentials" -> AuthException.wrongPassword();
            case "invalid_otp" -> new AuthException("auth.invalidotp");
            case "banned" -> new AuthException(AuthRequestEvent.ACCOUNT_BLOCKED_ERROR_MESSAGE);
            case "email_not_verified" -> new AuthException("auth.emailnotverified");
            case "rate_limited" -> new AuthException("auth.trylater");
            default -> new AuthException(message == null || message.isBlank() ? "auth.trylater" : message);
        };
    }

    private SiteHttpUser cacheUser(SiteUser siteUser) {
        var user = new SiteHttpUser(
                UUID.fromString(siteUser.uuid),
                siteUser.nickname,
                siteUser.accessToken,
                new ClientPermissions(
                        siteUser.roles == null ? List.of() : siteUser.roles,
                        siteUser.permissions == null ? List.of() : siteUser.permissions),
                siteUser.banned);
        usersByUsername.put(user.username.toLowerCase(), user);
        usersByUuid.put(user.uuid, user);
        return user;
    }

    private AuthManager.AuthReport buildAuthReport(SiteHttpUser user, boolean minecraftAccess) {
        String accessToken = LegacySessionHelper.makeAccessJwtTokenFromString(
                user,
                LocalDateTime.now().plusSeconds(expireSeconds),
                server.keyAgreementManager.ecdsaPrivateKey);
        String refreshToken = user.username + "." + LegacySessionHelper.makeRefreshTokenFromPassword(
                user.username,
                user.siteAccessToken,
                server.keyAgreementManager.legacySalt);
        if (minecraftAccess) {
            user.minecraftAccessToken = SecurityHelper.randomStringToken();
            usersByMinecraftToken.put(user.minecraftAccessToken, user);
            var session = new SiteHttpUserSession(user, user.minecraftAccessToken, expireSeconds);
            return AuthManager.AuthReport.ofOAuthWithMinecraft(user.minecraftAccessToken, accessToken, refreshToken, expireSeconds, session);
        }
        var session = new SiteHttpUserSession(user, user.minecraftAccessToken, expireSeconds);
        return AuthManager.AuthReport.ofOAuth(accessToken, refreshToken, expireSeconds, session);
    }

    record ExtractedPassword(String password, String totp) {
    }

    private record SiteAuthRequest(String login, String password, String otp, boolean minecraftAccess) {
    }

    static class SiteAuthResponse {
        public String status;
        public String message;
        public String requestId;
        public SiteUser user;
    }

    private static class SiteErrorResponse {
        public String error;
        public String status;
        public String message;
        public String requestId;
    }

    private static class SiteUser {
        public String uuid;
        public String nickname;
        public String accessToken;
        public List<String> permissions;
        public List<String> roles;
        public boolean banned;
    }

    public static class SiteHttpUser implements User {
        private final UUID uuid;
        private final String username;
        private final String siteAccessToken;
        private final ClientPermissions permissions;
        private final boolean banned;
        private String minecraftAccessToken;
        private String serverId;

        public SiteHttpUser(UUID uuid, String username, String siteAccessToken, ClientPermissions permissions, boolean banned) {
            this.uuid = uuid;
            this.username = username;
            this.siteAccessToken = siteAccessToken;
            this.permissions = permissions;
            this.banned = banned;
            this.minecraftAccessToken = SecurityHelper.randomStringToken();
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public UUID getUUID() {
            return uuid;
        }

        @Override
        public ClientPermissions getPermissions() {
            return permissions;
        }

        @Override
        public boolean isBanned() {
            return banned;
        }

        @Override
        public String toString() {
            return "SiteHttpUser{uuid=%s, username='%s', permissions=%s}".formatted(uuid, username, permissions);
        }
    }

    public static class SiteHttpUserSession implements UserSession {
        private final String id;
        private final SiteHttpUser user;
        private final String minecraftAccessToken;
        private final long expireIn;

        public SiteHttpUserSession(SiteHttpUser user, String minecraftAccessToken, long expireIn) {
            this.id = SecurityHelper.randomStringToken();
            this.user = user;
            this.minecraftAccessToken = minecraftAccessToken;
            this.expireIn = expireIn;
        }

        @Override
        public String getID() {
            return id;
        }

        @Override
        public User getUser() {
            return user;
        }

        @Override
        public String getMinecraftAccessToken() {
            return minecraftAccessToken;
        }

        @Override
        public long getExpireIn() {
            return expireIn;
        }
    }
}
