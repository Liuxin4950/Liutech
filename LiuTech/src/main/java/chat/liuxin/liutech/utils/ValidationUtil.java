package chat.liuxin.liutech.utils;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.regex.Pattern;

/**
 * 参数验证工具类
 * 提供统一的参数验证逻辑，减少代码重复
 * 
 * @author 刘鑫
 * @date 2025-01-30
 */
public class ValidationUtil {
    
    /**
     * 邮箱格式正则表达式
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    /**
     * 用户名格式正则表达式（4-20位字母数字下划线）
     */
    private static final Pattern USERNAME_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_]{4,20}$"
    );
    
    /**
     * 验证对象不为空
     * 
     * @param obj 要验证的对象
     * @param fieldName 字段名称
     * @throws BusinessException 当对象为空时抛出异常
     */
    public static void validateNotNull(Object obj, String fieldName) {
        if (obj == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, fieldName + "不能为空");
        }
    }
    
    /**
     * 验证字符串不为空
     * 
     * @param str 要验证的字符串
     * @param fieldName 字段名称
     * @throws BusinessException 当字符串为空时抛出异常
     */
    public static void validateNotBlank(String str, String fieldName) {
        if (!StringUtils.hasText(str)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, fieldName + "不能为空");
        }
    }
    
    /**
     * 验证集合不为空
     * 
     * @param collection 要验证的集合
     * @param fieldName 字段名称
     * @throws BusinessException 当集合为空时抛出异常
     */
    public static void validateNotEmpty(Collection<?> collection, String fieldName) {
        if (collection == null || collection.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, fieldName + "不能为空");
        }
    }
    
    /**
     * 验证ID有效性
     * 
     * @param id 要验证的ID
     * @param fieldName 字段名称
     * @throws BusinessException 当ID无效时抛出异常
     */
    public static void validateId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, fieldName + "无效");
        }
    }
    
    /**
     * 验证邮箱格式
     * 
     * @param email 要验证的邮箱
     * @throws BusinessException 当邮箱格式无效时抛出异常
     */
    public static void validateEmail(String email) {
        if (StringUtils.hasText(email) && !EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱格式不正确");
        }
    }
    
    /**
     * 验证用户名格式
     * 
     * @param username 要验证的用户名
     * @throws BusinessException 当用户名格式无效时抛出异常
     */
    public static void validateUsername(String username) {
        validateNotBlank(username, "用户名");
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名格式不正确，只能包含4-20位字母、数字和下划线");
        }
    }
    
    /**
     * 验证密码强度
     * 
     * @param password 要验证的密码
     * @throws BusinessException 当密码强度不够时抛出异常
     */
    public static void validatePassword(String password) {
        validateNotBlank(password, "密码");
        if (password.length() < 6 || password.length() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度必须在6-20位之间");
        }
    }
    
    /**
     * 验证字符串长度
     * 
     * @param str 要验证的字符串
     * @param fieldName 字段名称
     * @param minLength 最小长度
     * @param maxLength 最大长度
     * @throws BusinessException 当字符串长度不符合要求时抛出异常
     */
    public static void validateLength(String str, String fieldName, int minLength, int maxLength) {
        if (str != null) {
            int length = str.length();
            if (length < minLength || length > maxLength) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, 
                    fieldName + "长度必须在" + minLength + "-" + maxLength + "位之间");
            }
        }
    }
    
    /**
     * 验证数值范围
     * 
     * @param value 要验证的数值
     * @param fieldName 字段名称
     * @param min 最小值
     * @param max 最大值
     * @throws BusinessException 当数值不在范围内时抛出异常
     */
    public static void validateRange(Number value, String fieldName, Number min, Number max) {
        if (value != null) {
            double val = value.doubleValue();
            double minVal = min.doubleValue();
            double maxVal = max.doubleValue();
            
            if (val < minVal || val > maxVal) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, 
                    fieldName + "必须在" + min + "-" + max + "之间");
            }
        }
    }
}