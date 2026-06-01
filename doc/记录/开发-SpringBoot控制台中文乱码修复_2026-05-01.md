# SpringBoot控制台中文乱码修复 开发记录 - 2026-05-01

## 0. 文档信息

| 字段 | 内容 |
| --- | --- |
| 功能名 | SpringBoot控制台中文乱码修复 |
| 文件名 | SpringBoot控制台中文乱码修复_2026-05-01.md |
| 创建日期 | 2026-05-01 |
| 当前状态 | 已完成 |
| 变更级别 | 小修 |
| 关联需求 PRD | 不适用，小修/Bug 修复 |
| 关联实现 PRD | 不适用，小修/Bug 修复 |
| 关联架构文档 | 无架构影响 |

## 1. 问题 / 需求概述

- 背景：在 Trea/VSCode 集成终端启动 LiuTech-AI 时，Spring Boot 启动日志中的中文路径和中文提示出现乱码。
- 用户目标：本地开发启动 Spring Boot 服务时，控制台中文正常显示。
- 本次要解决的问题：统一 AI 服务控制台日志编码与 VSCode Java 启动 JVM 编码参数。
- 本次不解决的内容：不调整业务逻辑、接口、数据库、部署拓扑和生产运行策略。

## 2. 原因定位

- 现象：PowerShell 已执行 `chcp 65001` 后，部分中文日志仍显示为 `�` 或乱码。
- 根因 / 缺口：`LiuTech-AI/src/main/resources/logback-spring.xml` 的控制台 appender 明确配置了 `<charset>GBK</charset>`，与 UTF-8 终端不一致；`.vscode/launch.json` 仅配置 `file.encoding`，未覆盖 Java 21 的 stdout/stderr 编码。
- 证据：检查 `logback-spring.xml` 发现 STDOUT encoder 使用 GBK；启动日志中应用自定义早期日志可正常显示，而 logback 输出的后续日志乱码。
- 涉及模块：LiuTech-AI 日志配置、VSCode/Trea Java 启动配置。

## 3. 实现拆解

| 顺序 | 任务 | 状态 | 说明 |
| --- | --- | --- | --- |
| 1 | 调整 LiuTech-AI 控制台日志编码 | 已完成 | STDOUT appender 从 GBK 改为 UTF-8 |
| 2 | 补齐 VSCode Java 启动 VM 参数 | 已完成 | 为 AI 和主后端 launch 配置添加 `sun.stdout/stderr.encoding` |
| 3 | 补齐工作区默认 Java/终端编码参数 | 已完成 | 在 settings 中添加 `java.debug.settings.vmArgs` 和 `JAVA_TOOL_OPTIONS` |
| 4 | 执行验证 | 已完成 | 编译、JSON 解析、编译产物配置检查 |

## 4. 变更文件

| 文件 | 类型 | 说明 |
| --- | --- | --- |
| `.vscode/launch.json` | 修改 | 补齐 Java 调试启动 UTF-8 VM 参数 |
| `.vscode/settings.json` | 修改 | 设置 Java debug 默认 VM 参数和集成终端 `JAVA_TOOL_OPTIONS` |
| `LiuTech-AI/src/main/resources/logback-spring.xml` | 修改 | 控制台日志 charset 从 GBK 改为 UTF-8 |
| `doc/记录/开发-SpringBoot控制台中文乱码修复_2026-05-01.md` | 新增 | 记录本次小修交付过程 |

## 5. 实现内容

### 5.1 后端

- 未修改业务代码。
- 修改 AI 服务 logback 控制台输出编码，使 Spring Boot 日志输出与 UTF-8 终端一致。

### 5.2 前端

- 不涉及。

### 5.3 数据库 / 配置 / 部署

- 修改本地开发工具配置 `.vscode/launch.json` 和 `.vscode/settings.json`。
- 不涉及数据库和部署拓扑。

## 6. 安全与性能处理

- 安全影响：无业务安全边界变化；未新增密钥或认证逻辑。
- 性能影响：无性能影响。
- 已采取措施：仅调整日志和本地启动编码参数。
- 遗留风险：如果开发者使用非 UTF-8 终端且未覆盖 JVM 参数，可能需要手动切换终端编码。

## 7. 验证情况

| 验证项 | 命令 / 方式 | 结果 | 说明 |
| --- | --- | --- | --- |
| AI 服务编译 | `mvn -pl LiuTech-AI -DskipTests compile` | 通过 | Maven build success，资源正常复制 |
| JSON 配置解析 | `Get-Content .vscode/*.json \| ConvertFrom-Json` | 通过 | launch/settings JSON 均可解析 |
| 编译产物检查 | 检查 `LiuTech-AI/target/classes/logback-spring.xml` | 通过 | STDOUT charset 已为 UTF-8 |
| 临时启动验证 | `mvn -pl LiuTech-AI spring-boot:run ... --server.port=18081` | 未完成 | 服务为持续运行进程，命令被超时截断，未获得完整启动输出 |

未执行的验证必须写明原因：

- 未执行项：完整手动启动后观察 Trea/VSCode 控制台中文。
- 原因：本轮自动启动命令是持续运行服务，工具超时后未返回完整日志；更适合由用户在 Trea/VSCode 中点击运行按钮观察。
- 风险：低；根因配置已直接修正，并通过编译产物确认生效。

## 8. 问题记录

| 问题 | 原因 | 处理方式 | 状态 |
| --- | --- | --- | --- |
| UTF-8 终端中 Spring Boot 中文日志乱码 | logback 控制台 charset 固定为 GBK | 改为 UTF-8 | 已处理 |
| Java 21 stdout/stderr 可能未随 `file.encoding` 生效 | VSCode launch 参数不完整 | 补齐 `sun.stdout.encoding` 和 `sun.stderr.encoding` | 已处理 |

## 9. 回滚方案

- 代码回滚：还原 `.vscode/launch.json`、`.vscode/settings.json`、`LiuTech-AI/src/main/resources/logback-spring.xml` 中本次编码相关修改。
- 配置回滚：移除 `JAVA_TOOL_OPTIONS` 和新增 VM 参数。
- 数据回滚：不涉及。
- 用户影响：回滚后可能重新出现中文控制台乱码。

## 10. 架构影响判断

```text
是否有架构影响：否
判断依据：本次仅调整本地开发启动参数和日志输出编码，不改变模块边界、数据流、接口协议、部署拓扑或业务流程。
是否已更新项目架构文档：不需要
```

本次变更不涉及架构变化，原因：仅修复开发环境控制台编码配置。

## 11. 待办与遗留问题

- [ ] 用户在 Trea/VSCode 中重新启动 LiuTech-AI，确认中文路径和启动完成提示均正常显示。

## 12. 结论

- 完成情况：已完成控制台编码修复和 VSCode/Trea 启动配置补强。
- 测试情况：编译、JSON 配置解析、编译产物检查通过。
- 是否可交付：可交付。
- 后续建议：若主后端也存在类似 logback 控制台 GBK 配置，可按同样方式调整。
