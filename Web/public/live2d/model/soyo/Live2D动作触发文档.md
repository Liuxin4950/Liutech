# Live2D模型动作与触发配置文档

## 模型信息
- **模型名称**: soyo
- **配置文件**: soyo.model.json
- **物理文件**: soyo_other-25.physics.json

## 触摸区域配置 (hit_areas)

### 头部区域
| 区域名称 | ID | 触发动作 |
|---------|-----|----------|
| 头部 | D_PSD.01 | idle:空闲01 |
| 左脸 | D_PSD.02 | angry:生气01 |
| 右脸 | D_PSD.03 | smile:微笑01 |
| 左眼 | D_PSD.04 | wink:眨眼01 |
| 右眼 | D_PSD.05 | surprised:惊讶01 |
| 嘴巴 | D_PSD.06 | cry:哭泣01 |
| 左耳 | D_PSD.09 | serious:认真01 |
| 右耳 | D_PSD.10 | shame:害羞01 |
| 左眉毛 | D_PSD.11 | thinking:思考01 |
| 右眉毛 | D_PSD.12 | kandou:感动01 |
| 额头 | D_PSD.13 | kime:決め01 |
| 下巴 | D_PSD.14 | bye:再见01 |
| 鼻子 | D_PSD.30 | sneeze:打喷嚏01 |

### 身体区域
| 区域名称 | ID | 触发动作 |
|---------|-----|----------|
| 身体 | D_PSD.07 | kandou:感动01 |
| 胸口 | D_PSD.17 | cry:哭泣02 |
| 背部 | D_PSD.18 | sad:悲伤02 |
| 腰部 | D_PSD.19 | serious:认真02 |
| 肚子 | D_PSD.28 | hungry:饥饿01 |
| 脖子 | D_PSD.29 | cold:寒冷01 |

### 四肢区域
| 区域名称 | ID | 触发动作 |
|---------|-----|----------|
| 左手 | D_PSD.07 | idle:握手微笑 |
| 右手 | D_PSD.08 | smile:微笑02 |
| 左肩 | D_PSD.15 | angry:生气02 |
| 右肩 | D_PSD.16 | smile:微笑03 |
| 左脚 | D_PSD.22 | idle:空闲02 |
| 右脚 | D_PSD.23 | happy:开心01 |
| 左爪子 | D_PSD.26 | sleepy:困倦01 |
| 右爪子 | D_PSD.27 | curious:好奇01 |

### 特殊区域
| 区域名称 | ID | 触发动作 |
|---------|-----|----------|
| 尾巴根部 | D_PSD.24 | relaxed:放松01 |
| 尾巴尖端 | D_PSD.25 | excited:兴奋01 |
| 测试完成 | D_PSD.31 | idle:空闲03 |

## 动作类型配置 (motions)

### 基础动作
- **idle** (空闲)
  - 空闲01 (mtn/idle01.mtn)
  - 握手微笑 (mtn/ando01.mtn)

### 情绪动作
- **angry** (生气) - 6个动作
  - 生气01-06 (mtn/angry01.mtn - mtn/angry06.mtn)
- **smile** (微笑) - 6个动作
  - 微笑01-06 (mtn/smile01.mtn - mtn/smile06.mtn)
- **sad** (悲伤) - 2个动作
  - 悲伤02-03 (mtn/sad02.mtn - mtn/sad03.mtn)
- **cry** (哭泣) - 2个动作
  - 哭泣01-02 (mtn/cry01.mtn - mtn/cry02.mtn)
- **shame** (害羞) - 2个动作
  - 害羞01-02 (mtn/shame01.mtn - mtn/shame02.mtn)
- **scared** (害怕) - 1个动作
  - 害怕01 (mtn/scared01.mtn)
- **surprised** (惊讶) - 1个动作
  - 惊讶01 (mtn/surprised01.mtn)

### 行为动作
- **bye** (再见) - 2个动作
  - 再见01-02 (mtn/bye01.mtn - mtn/bye02.mtn)
- **thinking** (思考) - 2个动作
  - 思考01-02 (mtn/thinking01.mtn - mtn/thinking02.mtn)
- **serious** (认真) - 4个动作
  - 认真01-04 (mtn/serious01.mtn - mtn/serious04.mtn)
- **wink** (眨眼) - 1个动作
  - 眨眼01 (mtn/wink01.mtn)

### 特殊动作
- **kandou** (感动) - 1个动作
  - 感动01 (mtn/kandou01.mtn)
- **kime** (決め) - 1个动作
  - 決め01 (mtn/kime01.mtn)
- **nf** 系列 - 5个动作
  - nf01-05 (mtn/nf01.mtn - mtn/nf05.mtn)
- **nf_left** (nf左) - 1个动作
  - nf左01 (mtn/nf_left01.mtn)
- **nf_right** (nf右) - 1个动作
  - nf右01 (mtn/nf_right01.mtn)
- **nnf** 系列 - 5个动作
  - nnf01-05 (mtn/nnf01.mtn - mtn/nnf05.mtn)
- **nnf_left** (nnf左) - 1个动作
  - nnf左01 (mtn/nnf_left01.mtn)
- **nnf_right** (nnf右) - 1个动作
  - nnf右01 (mtn/nnf_right01.mtn)
- **odoodo** - 1个动作
  - odoodo01 (mtn/odoodo01.mtn)

## 表情配置 (expressions)

### 基础表情
- ando01.exp (exp/ando01.exp.json)
- default.exp (exp/default.exp.json)
- idle01.exp (exp/idle01.exp.json)

### 情绪表情
- angry01-04.exp (exp/angry01.exp.json - exp/angry04.exp.json)
- sad01-03.exp (exp/sad01.exp.json - exp/sad03.exp.json)
- cry01-02.exp (exp/cry01.exp.json - exp/cry02.exp.json)
- shame01-02.exp (exp/shame01.exp.json - exp/shame02.exp.json)
- smile01-06.exp (exp/smile01.exp.json - exp/smile06.exp.json)
- serious01-04.exp (exp/serious01.exp.json - exp/serious04.exp.json)

### 行为表情
- bye01-02.exp (exp/bye01.exp.json - exp/bye02.exp.json)
- wink01.exp (exp/wink01.exp.json)
- surprised01.exp (exp/surprised01.exp.json)
- thinking01-02.exp (exp/thinking01.exp.json - exp/thinking02.exp.json)

### 特殊表情
- kandou01.exp (exp/kandou01.exp.json)
- kime01.exp (exp/kime01.exp.json)
- odoodo01.exp (exp/odoodo01.exp.json)

## 触发方式说明

### 触摸触发
通过点击不同的触摸区域触发对应的动作，格式为：`动作类型:动作名称`

例如：
- 点击头部 → 触发 `idle:空闲01`
- 点击左眼 → 触发 `wink:眨眼01`
- 点击右手 → 触发 `smile:微笑02`

### 动作调用
可以通过编程方式调用动作：
- 随机播放：`startMotion("angry")` - 随机播放生气系列动作
- 指定动作：`startMotion("angry", 0)` - 播放生气01
- 指定动作：`startMotion("angry:生气02")` - 播放生气02

### 表情切换
可以通过编程方式切换表情：
- `setExpression("smile01.exp")` - 切换到微笑表情
- `setExpression("default.exp")` - 切换到默认表情

## 文件结构
```
soyo/
├── soyo.model.json          # 主配置文件
├── soyo_other-25.physics.json  # 物理文件
├── mtn/                     # 动作文件目录
│   ├── idle01.mtn
│   ├── ando01.mtn
│   ├── angry01.mtn - angry06.mtn
│   ├── smile01.mtn - smile06.mtn
│   ├── sad02.mtn - sad03.mtn
│   ├── cry01.mtn - cry02.mtn
│   ├── shame01.mtn - shame02.mtn
│   ├── scared01.mtn
│   ├── surprised01.mtn
│   ├── bye01.mtn - bye02.mtn
│   ├── thinking01.mtn - thinking02.mtn
│   ├── serious01.mtn - serious04.mtn
│   ├── wink01.mtn
│   ├── kandou01.mtn
│   ├── kime01.mtn
│   ├── nf01.mtn - nf05.mtn
│   ├── nf_left01.mtn
│   ├── nf_right01.mtn
│   ├── nnf01.mtn - nnf05.mtn
│   ├── nnf_left01.mtn
│   ├── nnf_right01.mtn
│   └── odoodo01.mtn
└── exp/                     # 表情文件目录
    ├── ando01.exp.json
    ├── default.exp.json
    ├── idle01.exp.json
    ├── angry01.exp.json - angry04.exp.json
    ├── sad01.exp.json - sad03.exp.json
    ├── cry01.exp.json - cry02.exp.json
    ├── shame01.exp.json - shame02.exp.json
    ├── smile01.exp.json - smile06.exp.json
    ├── serious01.exp.json - serious04.exp.json
    ├── bye01.exp.json - bye02.exp.json
    ├── wink01.exp.json
    ├── surprised01.exp.json
    ├── thinking01.exp.json - thinking02.exp.json
    ├── kandou01.exp.json
    ├── kime01.exp.json
    └── odoodo01.exp.json
```

## 开发建议

1. **测试触摸区域**：在Live2D开发工具中测试所有触摸区域的响应
2. **动作组合**：可以尝试组合不同的动作和表情创造更丰富的表现
3. **自定义动作**：可以在mtn目录中添加新的动作文件，并在配置文件中相应添加
4. **性能优化**：过多的触摸区域可能影响性能，建议根据实际需求调整
5. **错误处理**：确保动作文件存在，避免触发不存在的动作

---
*文档生成时间：银月为您整理* 
*最后更新：请查看文件修改时间*