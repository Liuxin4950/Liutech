# Claude Code AI开发工程流程入口

本文件用于兼容 Claude Code，只做轻量入口，不重复 PRD 细节。

## 加载原则

1. 日常交流、解释概念、头脑风暴、非落地讨论：不要主动加载 PRD、实现 PRD、开发记录或项目架构文档。
2. 用户明确要求生成 PRD、审查 PRD、先设计方案、实现前规划时，参考 `.codex/skills/prd-workflow`。
3. 用户明确要求开始开发、修复 bug、重构、安全整改或性能优化时，参考 `.codex/skills/delivery-workflow`。
4. 触发任一 workflow skill 后，先读取项目适配文件：`.codex/project-adapter.md`。
5. 只读取与当前功能直接相关的过程文档，不批量加载整个 `doc/`。

Codex 默认规则入口：

```text
.codex/rules/ai-development-workflow.md
```

同一功能的过程文件必须使用完全一致的文件名：`功能名_YYYY-MM-DD.md`。
