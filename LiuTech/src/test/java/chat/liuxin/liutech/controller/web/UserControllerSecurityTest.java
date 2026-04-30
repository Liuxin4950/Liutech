package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.model.Users;
import chat.liuxin.liutech.resp.CategoryResp;
import chat.liuxin.liutech.resp.TagResp;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UserControllerSecurityTest {

    @Test
    void legacyUserManagementEndpointsRequireAdminRole() throws Exception {
        assertAdminOnly("getUsers", Long.class, String.class);
        assertAdminOnly("getUserById", Long.class);
        assertAdminOnly("createUser", Users.class);
        assertAdminOnly("updateUser", Long.class, Users.class);
        assertAdminOnly("deleteUser", Long.class);
    }

    @Test
    void globalCategoryAndTagCreationRequireAdminRole() throws Exception {
        assertAdminOnly(CategoriesController.class, "createCategory", CategoryResp.class);
        assertAdminOnly(TagsController.class, "createTag", TagResp.class);
    }

    private void assertAdminOnly(String methodName, Class<?>... parameterTypes) throws Exception {
        assertAdminOnly(UserController.class, methodName, parameterTypes);
    }

    private void assertAdminOnly(Class<?> controllerClass, String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = controllerClass.getMethod(methodName, parameterTypes);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals("hasRole('ADMIN')", preAuthorize.value());
    }
}
