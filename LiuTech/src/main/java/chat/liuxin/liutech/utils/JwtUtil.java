package chat.liuxin.liutech.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
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

    // 依赖说明：
    // - 配置属性：jwt.secret（签名密钥）、jwt.expiration（过期毫秒数）
    // - 第三方库：io.jsonwebtoken（JJWT）用于签名与解析
    // - 本类仅封装 JWT 的生成/解析/校验，不做任何业务判定
    
    /**
     * JWT密钥 - 从配置文件中读取
     */
    @Value("${jwt.secret}")
    private String secretKey;
    
    /**
     * token过期时间 - 从配置文件中读取
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
     * 生成JWT token
     * 
     * @param userId 用户ID
     * @param username 用户名
     * @param passwordHash 密码哈希值（用于token验证，不会明文传输）
     * @return JWT token字符串
     */
    public String generateToken(Long userId, String username, String passwordHash) {
        // 说明：当前 claims 包含 userId/username/passwordHash
        // 提示：passwordHash 仅用于校验旧密码变更导致的失效；不建议长期保留于token，后续可替换为 tokenVersion 或 lastPasswordChangeAt
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("passwordHash", passwordHash);
        return createToken(claims, username);
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
            // 过期：返回 null，由调用方决定是否提示重新登录
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
        // 强校验：同时匹配用户名与未过期
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
        // 弱校验：只校验签名与未过期（不校验用户名）
        return getClaimsFromToken(token) != null && !isTokenExpired(token);
    }

    /**
     * 刷新token（生成新的token）
     * 
     * @param token 旧的JWT token
     * @return 新的JWT token
     */
    public String refreshToken(String token) {
        // 简单刷新：沿用旧claims生成新token（过期时间更新）
        Claims claims = getClaimsFromToken(token);
        if (claims == null) {
            return null;
        }
        Long userId = ((Number) claims.get("userId")).longValue();
        String username = claims.getSubject();
        String passwordHash = (String) claims.get("passwordHash");
        return generateToken(userId, username, passwordHash);
    }
}