package chat.liuxin.liutech.utils;

import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.resp.UserResp;
import org.springframework.beans.BeanUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Bean转换工具类
 * 提供统一的对象转换逻辑，减少代码重复
 *
 * @author 刘鑫
 * @date 2025-01-30
 */
public class BeanConvertUtil {

    /**
     * 将Users实体转换为UserResl响应对象（脱敏）
     * 自动清除敏感信息如密码等
     *
     * @param user 用户实体
     * @return 脱敏后的用户响应对象
     */
    public static UserResp convertToUserResl(Users user) {
        if (user == null) {
            return null;
        }

        UserResp userResp = new UserResp();
        BeanUtils.copyProperties(user, userResp);

        // 清除敏感信息
        userResp.setPasswordHash(null);

        return userResp;
    }

    /**
     * 批量将Users实体列表转换为UserResl响应对象列表（脱敏）
     *
     * @param users 用户实体列表
     * @return 脱敏后的用户响应对象列表
     */
    public static List<UserResp> convertToUserReslList(List<Users> users) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }

        return users.stream()
                .map(BeanConvertUtil::convertToUserResl)
                .collect(Collectors.toList());
    }

    /**
     * 通用对象转换方法
     * 使用Spring的BeanUtils进行属性复制
     *
     * @param source 源对象
     * @param targetClass 目标类型
     * @param <T> 目标类型泛型
     * @return 转换后的对象
     */
    public static <T> T convert(Object source, Class<T> targetClass) {
        if (source == null) {
            return null;
        }

        try {
            T target = targetClass.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(source, target);
            return target;
        } catch (Exception e) {
            throw new RuntimeException("对象转换失败: " + e.getMessage(), e);
        }
    }

    /**
     * 通用列表转换方法
     *
     * @param sourceList 源对象列表
     * @param targetClass 目标类型
     * @param <S> 源类型泛型
     * @param <T> 目标类型泛型
     * @return 转换后的对象列表
     */
    public static <S, T> List<T> convertList(List<S> sourceList, Class<T> targetClass) {
        if (sourceList == null || sourceList.isEmpty()) {
            return List.of();
        }

        return sourceList.stream()
                .map(source -> convert(source, targetClass))
                .collect(Collectors.toList());
    }
}
