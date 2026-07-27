package br.com.libertadfacilities.blog.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtService {

    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String TWO_FACTOR_PENDING_TOKEN_TYPE = "2fa-pending";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    public String generateToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSignInKey())
                .compact();
    }

    public String generateTemporaryTwoFactorToken(UserDetails userDetails) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + 5 * 60 * 1000);

        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .claim(TOKEN_TYPE_CLAIM, TWO_FACTOR_PENDING_TOKEN_TYPE)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSignInKey())
                .compact();
    }

    public String extractUsername(String token) throws JwtException {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractUsernameFromAnyToken(String token) {
        return extractUsername(token);
    }

    public boolean isTokenExpired(String token) {
        try {
            return extractClaim(token, Claims::getExpiration)
                    .before(new Date());
        } catch (JwtException exception) {
            return true;
        }
    }

    public boolean isTokenValid(
            String token,
            UserDetails userDetails
    ) {
        try {
            String username = extractUsername(token);

            return username.equals(userDetails.getUsername())
                    && isAccessToken(token);
        } catch (JwtException exception) {
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        try {
            return hasTokenType(token, ACCESS_TOKEN_TYPE)
                    && !isTokenExpired(token);
        } catch (JwtException exception) {
            return false;
        }
    }

    public boolean isTemporaryTwoFactorToken(String token) {
        try {
            return hasTokenType(
                    token,
                    TWO_FACTOR_PENDING_TOKEN_TYPE
            ) && !isTokenExpired(token);
        } catch (JwtException exception) {
            return false;
        }
    }

    private boolean hasTokenType(
            String token,
            String expectedType
    ) {
        String tokenType = extractAllClaims(token)
                .get(TOKEN_TYPE_CLAIM, String.class);

        return expectedType.equals(tokenType);
    }

    private <T> T extractClaim(
            String token,
            Function<Claims, T> resolver
    ) throws JwtException {
        return resolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token)
            throws JwtException {

        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}