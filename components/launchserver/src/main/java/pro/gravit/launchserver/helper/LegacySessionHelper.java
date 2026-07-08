package pro.gravit.launchserver.helper;

import io.jsonwebtoken.Jwts;
import pro.gravit.launchserver.auth.core.User;
import pro.gravit.utils.helper.SecurityHelper;

import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

public class LegacySessionHelper {
    public static String makeAccessJwtTokenFromString(User user, LocalDateTime expirationTime, ECPrivateKey privateKey) {
        return Jwts.builder()
                .setIssuer("LaunchServer")
                .setSubject(user.getUsername())
                .claim("uuid", user.getUUID().toString())
                .claim("permissions", user.getPermissions().getPerms())
                .setExpiration(Date.from(expirationTime
                        .toInstant(ZoneOffset.UTC)))
                .signWith(privateKey)
                .compact();
    }

    public static JwtTokenInfo getJwtInfoFromAccessToken(String token, ECPublicKey publicKey) {
        var parser = Jwts.parser()
                .requireIssuer("LaunchServer")
                .clock(() -> new Date(Clock.systemUTC().millis()))
                .verifyWith(publicKey)
                .build();
        var claims = parser.parseSignedClaims(token);
        var uuid = UUID.fromString(claims.getPayload().get("uuid", String.class));
        var username = claims.getPayload().getSubject();
        return new JwtTokenInfo(username, uuid);
    }

    public static String makeRefreshTokenFromPassword(String username, String rawPassword, String secretSalt) {
        if (rawPassword == null) {
            rawPassword = "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest("%s.%s.%s.%s".formatted(secretSalt, username, rawPassword, secretSalt)
                    .getBytes(StandardCharsets.UTF_8));
            return SecurityHelper.toHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is unavailable", e);
        }
    }

    public record JwtTokenInfo(String username, UUID uuid) {
    }
}
