package chat.liuxin.ai.common.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类
 * 用于生成、解析和验证JWT token
 * 
 * @author liuxin
 */
@Slf4j
@Component
public class JwtUtil {

    /**
     * JWT密钥 - 从配置文件读取
     */
    @Value("${jwt.secret}")
    private String secretKey;
    
    /**
     * token过期时间 - 从配置文件读取
     */
    @Value("${jwt.expiration}")
    private long expirationTime;
    
    /**
     * 获取签名密钥
     */
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    /**
     * 计算 SHA-256 摘要（十六进制小写字符串）
     * 用于对 passwordHash 做二次摘要后再放入 JWT claims，
     * 避免 BCrypt 哈希值在 token payload 中明文暴露。
     */
    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 生成JWT token
     *
     * @param userId 用户ID
     * @param username 用户名
     * @param role 用户角色 (user/admin)
     * @param passwordHash 密码哈希值（用于token验证，不会明文传输）
     * @return JWT token字符串
     */
    public String generateToken(Long userId, String username, String role, String passwordHash) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", role);
        claims.put("passwordHash", sha256(passwordHash));

        return createToken(claims, username);
    }

    /**
     * 生成JWT token (兼容旧版，无role)
     */
    public String generateToken(Long userId, String username, String passwordHash) {
        return generateToken(userId, username, "user", passwordHash);
    }

    /**
     * 创建token
     * 
     * @param claims 载荷信息
     * @param subject 主题（通常是用户名）
     * @return JWT token
     */
    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationTime);
        
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 从token中提取用户ID
     * 
     * @param token JWT token
     * @return 用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? ((Number) claims.get("userId")).longValue() : null;
    }

    /**
     * 从token中提取用户名
     * 
     * @param token JWT token
     * @return 用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.getSubject() : null;
    }

    /**
     * 从token中提取密码哈希值
     *
     * @param token JWT token
     * @return 密码哈希值
     */
    public String getPasswordHashFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? (String) claims.get("passwordHash") : null;
    }

    /**
     * 从token中提取角色
     *
     * @param token JWT token
     * @return 角色字符串 (user/admin)
     */
    public String getRoleFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return null;
        }
        // 兼容旧版 token：没有 role 字段时返回 null
        Object role = claims.get("role");
        return role != null ? (String) role : null;
    }

    /**
     * 从token中提取过期时间
     * 
     * @param token JWT token
     * @return 过期时间
     */
    public Date getExpirationDateFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.getExpiration() : null;
    }

    /**
     * 从token中提取所有声明信息
     * 
     * @param token JWT token
     * @return 声明信息
     */
    private Claims getClaimsFromToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            log.warn("JWT token已过期: {}", e.getMessage());
            return null;
        } catch (UnsupportedJwtException e) {
            log.warn("不支持的JWT token: {}", e.getMessage());
            return null;
        } catch (MalformedJwtException e) {
            log.warn("JWT token格式错误: {}", e.getMessage());
            return null;
        } catch (SecurityException e) {
            log.warn("JWT token签名验证失败: {}", e.getMessage());
            return null;
        } catch (IllegalArgumentException e) {
            log.warn("JWT token参数错误: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 检查token是否过期
     * 
     * @param token JWT token
     * @return true表示已过期，false表示未过期
     */
    public boolean isTokenExpired(String token) {
        Date expiration = getExpirationDateFromToken(token);
        return expiration != null && expiration.before(new Date());
    }

    /**
     * 验证token是否有效
     * 
     * @param token JWT token
     * @param username 用户名（用于验证token中的用户名是否匹配）
     * @return true表示有效，false表示无效
     */
    public boolean validateToken(String token, String username) {
        String tokenUsername = getUsernameFromToken(token);
        return tokenUsername != null && 
               tokenUsername.equals(username) && 
               !isTokenExpired(token);
    }

    /**
     * 验证token是否有效（不验证用户名）
     * 
     * @param token JWT token
     * @return true表示有效，false表示无效
     */
    public boolean validateToken(String token) {
        return getClaimsFromToken(token) != null && !isTokenExpired(token);
    }

    /**
     * 刷新token（生成新的token）
     *
     * @param token 旧的JWT token
     * @return 新的JWT token
     */
    public String refreshToken(String token) {
        // 从旧 token 提取的 passwordHash 已经是 SHA-256 摘要，
        // 直接写入新 claims，避免 generateToken 重复哈希
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return null;
        }

        Long userId = ((Number) claims.get("userId")).longValue();
        String username = claims.getSubject();
        String role = (String) claims.get("role");
        String passwordHash = (String) claims.get("passwordHash");

        Map<String, Object> newClaims = new HashMap<>();
        newClaims.put("userId", userId);
        newClaims.put("username", username);
        newClaims.put("role", role != null ? role : "user");
        newClaims.put("passwordHash", passwordHash); // 已是 SHA-256 值，直接写入
        return createToken(newClaims, username);
    }
}