# Swagger API 文档使用说明

## 1. 项目配置

### 1.1 依赖配置
在 `pom.xml` 中已添加 Swagger 依赖：
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.2.0</version>
</dependency>
```

### 1.2 配置类
创建了 `SwaggerConfig.java` 配置类，用于配置 API 文档的基本信息。

## 2. 访问方式

启动项目后，可以通过以下地址访问 Swagger 文档：
- **Swagger UI**: http://localhost:8080/swagger-ui/index.html
- **API 文档 JSON**: http://localhost:8080/v3/api-docs

## 3. 常用 Swagger 注解

### 3.1 Controller 层注解

#### @Tag
用于标记 Controller 类，提供 API 分组信息：
```java
@Tag(name = "用户管理", description = "用户相关的API接口，包括注册、登录、用户CRUD操作")
@RestController
public class UserController {
    // ...
}
```

#### @Operation
用于描述具体的 API 接口：
```java
@Operation(summary = "用户登录", description = "验证用户凭据并返回用户信息，支持用户名和密码登录")
@PostMapping("/login")
public Result<UserResl> login(@Valid @RequestBody LoginReq loginReq) {
    // ...
}
```

#### @ApiResponses 和 @ApiResponse
用于描述 API 的响应状态：
```java
@ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "登录成功"),
    @ApiResponse(responseCode = "400", description = "参数错误"),
    @ApiResponse(responseCode = "401", description = "用户名或密码错误"),
    @ApiResponse(responseCode = "403", description = "账户被禁用"),
    @ApiResponse(responseCode = "500", description = "服务器内部错误")
})
```

#### @Parameter
用于描述方法参数：
```java
public Result<Users> getUserById(
    @Parameter(description = "用户ID", required = true) @PathVariable Long id) {
    // ...
}
```

### 3.2 实体类注解

#### @Schema
用于描述实体类和字段：
```java
@Schema(description = "用户实体")
public class Users {
    
    @Schema(description = "用户ID", example = "1")
    private Long id;
    
    @Schema(description = "用户名", example = "liuxin", required = true)
    private String username;
    
    @Schema(description = "密码哈希值", hidden = true)
    private String passwordHash;
    
    @Schema(description = "用户状态", example = "1", allowableValues = {"0", "1"})
    private Integer status;
}
```

## 4. 注解属性说明

### 4.1 @Schema 常用属性
- `description`: 字段描述
- `example`: 示例值
- `required`: 是否必填
- `hidden`: 是否在文档中隐藏
- `allowableValues`: 允许的值列表
- `format`: 数据格式（如 "date-time"）
- `pattern`: 正则表达式模式

### 4.2 @Operation 常用属性
- `summary`: 接口简要描述
- `description`: 接口详细描述
- `tags`: 接口标签（可覆盖类级别的 @Tag）
- `deprecated`: 标记接口是否已废弃

### 4.3 @Parameter 常用属性
- `description`: 参数描述
- `required`: 是否必填
- `example`: 示例值
- `schema`: 参数的 Schema 信息

## 5. 最佳实践

### 5.1 Controller 注解规范
1. 每个 Controller 类都应该添加 `@Tag` 注解
2. 每个接口方法都应该添加 `@Operation` 注解
3. 为重要的接口添加 `@ApiResponses` 描述可能的响应状态
4. 为路径参数和查询参数添加 `@Parameter` 注解

### 5.2 实体类注解规范
1. 每个实体类都应该添加 `@Schema` 注解描述类的用途
2. 为每个字段添加 `@Schema` 注解，包含描述和示例值
3. 敏感字段（如密码）使用 `hidden = true` 隐藏
4. 枚举字段使用 `allowableValues` 限制可选值
5. 必填字段使用 `required = true` 标记

### 5.3 注释编写建议
1. 描述要简洁明了，避免冗余
2. 示例值要真实可用，便于测试
3. 错误码描述要准确，帮助前端处理异常
4. 参数描述要包含格式要求和限制条件

## 6. 常见问题

### 6.1 Swagger UI 无法访问
- 检查项目是否正常启动
- 确认访问路径是否正确
- 检查是否有安全配置阻止访问

### 6.2 接口不显示在文档中
- 检查 Controller 是否在 Spring 扫描路径下
- 确认方法是否有正确的 HTTP 方法注解
- 检查是否有 `@Hidden` 注解隐藏了接口

### 6.3 参数类型显示不正确
- 检查实体类的 `@Schema` 注解配置
- 确认字段类型是否正确
- 检查是否有自定义的类型转换器

## 7. 扩展功能

### 7.1 添加认证信息
可以在 `SwaggerConfig` 中配置 API 认证信息：
```java
@Bean
public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
        .components(new Components()
            .addSecuritySchemes("bearerAuth", 
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
}
```

### 7.2 分组配置
可以通过配置文件设置不同的 API 分组：
```yaml
springdoc:
  group-configs:
    - group: 'user'
      paths-to-match: '/user/**'
    - group: 'admin'
      paths-to-match: '/admin/**'
```

通过以上配置和注解，你的 API 文档将更加完善和易于使用！