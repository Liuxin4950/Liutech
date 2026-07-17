package chat.liuxin.liutech.controller.web;

import chat.liuxin.liutech.resp.CategoryResp;
import chat.liuxin.liutech.resp.TagResp;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UserControllerSecurityTest {

    @Test
    void globalCategoryAndTagCreationRequireAdminRole() throws Exception {
        assertAdminOnly(CategoriesController.class, "createCategory", CategoryResp.class);
        assertAdminOnly(TagsController.class, "createTag", TagResp.class);
    }

    private void assertAdminOnly(Class<?> controllerClass, String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = controllerClass.getMethod(methodName, parameterTypes);
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

        assertNotNull(preAuthorize);
        assertEquals("hasRole('ADMIN')", preAuthorize.value());
    }
}
