-- 关于页内容结构迁移：数据库是唯一事实源，不依赖 Java 默认值。
INSERT INTO system_settings (setting_key, setting_value, description)
VALUES (
  'about.content',
  CAST(JSON_OBJECT(
    'motto', '「代码记录自我，热爱成就未来」',
    'introParagraphs', JSON_ARRAY(
      '我叫刘鑫，是软件工程专业的学生，正在努力成为全栈开发工程师。用代码记录时间与成长，用技术创造成果与价值。',
      '最初接触编程只是出于好奇，把它和传说中黑客的网络技术搞混了，但是在学习的过程中却渐渐发现这不冲突——不论网络技术还是软件工程，都是计算机的一部分：相比刷短视频，我更喜欢用学习到的知识来实现一些我感兴趣的功能或项目。',
      '这些年，我从初学者前端开始，逐渐过渡到后端开发、数据库设计、容器化和其他中间件——这个博客就是我为整合所学、并亲手实现一个能和读者交流的 Live2D 看板娘而搭建的。'
    ),
    'socialLinks', JSON_ARRAY(
      JSON_OBJECT('label', 'GitHub', 'value', 'Liuxin4950', 'href', 'https://github.com/Liuxin4950'),
      JSON_OBJECT('label', '邮箱', 'value', 'liuxin4950@gmail.com', 'href', 'mailto:liuxin4950@gmail.com')
    ),
    'skillGroups', JSON_ARRAY(
      JSON_OBJECT('category', '前端开发', 'skills', JSON_ARRAY('Vue 3', 'TypeScript', 'Vite', 'uni-app', 'Flutter', 'ECharts', 'SCSS', 'Ant Design')),
      JSON_OBJECT('category', '后端开发', 'skills', JSON_ARRAY('Spring Boot', 'Java', 'MyBatis-Plus', 'MySQL', 'Redis', 'ThinkPHP', 'Spring Security')),
      JSON_OBJECT('category', '工程化', 'skills', JSON_ARRAY('Docker', 'Compose', 'Nginx', 'Linux', '微服务网关', 'Actions', 'CI/CD')),
      JSON_OBJECT('category', 'AI 探索', 'skills', JSON_ARRAY('OpenClaw', 'Ollama', 'Spring AI', '大模型 API', 'Prompt 工程', 'Live2D', 'Claude Code'))
    ),
    'projects', JSON_ARRAY(
      JSON_OBJECT(
        'name', '名钓九洲',
        'description', '负责钓场小程序与管理后台的全栈开发：B2B 商城迁移（21 个页面精简至 13 个、分包压缩至 2M 以下）、团购/随到随钓子订单与退款审核体系、活动成绩排行榜与自动开杆定时任务、数据看板（20+ 页面 ECharts 可视化）、库存效期管理与导出、战队排名计算、微信 openid 登录重构。',
        'technologies', JSON_ARRAY('uni-app', 'Vue 3', 'Spring Boot', 'MySQL', 'ECharts'),
        'link', NULL
      ),
      JSON_OBJECT(
        'name', '亿家康健健康服务平台',
        'description', '药品商城小程序：实现商品详情、购物车、立即购买与订单售后全流程，对接订单商品客服与服务商交易流水，迁移阿里云短信服务，修复收藏、历史记录等页面功能。',
        'technologies', JSON_ARRAY('小程序', 'Vue', 'Spring Boot', '阿里云'),
        'link', NULL
      ),
      JSON_OBJECT(
        'name', 'AI 落地与团队赋能',
        'description', '把 AI 引入团队并沉淀为可复制的工作方式：指导成员正确使用 AI、厘清 AI 的能力边界（能做什么、不能做什么、如何校验结果），让 AI 提效成为团队共识；同时落地具体业务实践——代码审核智能体（每日检查提交）、药品宣传合规审核、跨设备浏览器自动化，验证 AI 在真实业务中的可行性。',
        'technologies', JSON_ARRAY('AI 指导', 'OpenClaw', 'Claude Code', '业务实践'),
        'link', NULL
      ),
      JSON_OBJECT(
        'name', 'LiuTech 博客',
        'description', '全栈个人博客平台：Spring Boot 微服务 + Vue 3 + Docker Compose 架构，含 AI 聊天、Live2D 看板娘、SSE 流式对话与 TTS 语音合成。',
        'technologies', JSON_ARRAY('Vue 3', 'Spring Boot', 'MySQL', 'Docker'),
        'link', '/'
      )
    ),
    'honors', JSON_OBJECT(
      'summary', '全国职业院校技能大赛团体二等奖、重庆市选拔赛第一名、Web 应用开发一等奖、金砖国家技能大赛三等奖……持续积累中。',
      'imageUrl', NULL
    ),
    'contactText', '有文章内容、项目问题或技术交流，欢迎留言。',
    'bannerDescription', '全栈工程师 & 技术博主 · 专注 Spring Boot、Vue 3 与 AI 应用实践',
    'metaDescription', '关于 LiuTech 作者刘鑫：全栈工程师、技术博主，专注于 Spring Boot、Vue 3、AI 应用与软件工程实践。'
  ) AS CHAR),
  '关于页结构化内容（JSON）'
)
ON DUPLICATE KEY UPDATE
  setting_value = VALUES(setting_value),
  description = VALUES(description);
