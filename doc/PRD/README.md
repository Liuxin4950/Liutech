# PRD 目录

本目录存放合并的 PRD 文档（业务需求 + 实现方案），命名格式 `功能名_YYYY-MM-DD.md`。

```text
doc/PRD/功能名_YYYY-MM-DD.md          # 业务需求 + 实现方案合并文档
```

`prd-workflow` skill 负责产出本目录的文档；`delivery-workflow` 引用 PRD 推进编码。

## 何时需要 PRD

满足以下**任一**条件才走完整 PRD：

1. 改动行数超过约 200 行
2. 跨服务调用（涉及 `LiuTech` ↔ `LiuTech-AI` ↔ `Nginx` ↔ 数据库）
3. 触及高风险领域：认证授权、积分/支付/下载权限、数据库结构、上传下载、AI/SSE/TTS、Nginx、Docker、CI/CD、部署
4. 引入新接口或修改现有接口协议
5. 引入新数据库表或迁移脚本

其它小功能可跳过 PRD，直接进入 `delivery-workflow`，只写开发记录。
