package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.CommunityMapper;
import chat.liuxin.liutech.mapper.CommentsMapper;
import chat.liuxin.liutech.model.*;
import chat.liuxin.liutech.req.CommunityReq;
import chat.liuxin.liutech.resp.CommunityResp;
import chat.liuxin.liutech.resp.PageResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 网站事实与社区写入权威；AI 只能提议。锁顺序：文章 -> settings -> 角色 -> 评论。
 * settings 单行锁串行化低吞吐社区的计数，避免读后写并发越过全站/角色/文章/链上限。
 * 模型调用永远在 AI 服务、数据库事务外发生。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityService {
    private final CommunityMapper mapper;
    private final CommentsMapper commentsMapper;
    private static final ZoneId DAY_ZONE = ZoneId.of("Asia/Shanghai");

    @Transactional(readOnly=true)
    public List<CommunityBot> bots() { return mapper.bots(); }
    @Transactional(readOnly=true)
    public List<CommunityResp.BotInfo> publicBots() {
        return mapper.bots().stream().filter(b -> Boolean.TRUE.equals(b.getEnabled())).map(CommunityService::botInfo).toList();
    }
    @Transactional(readOnly=true)
    public CommunitySettings settings() { return requireSettings(mapper.settings()); }

    @Transactional(readOnly=true)
    public PageResp<Comments> botCommentsForAdmin(Long botId, int page, int size) {
        requireBot(mapper.botForAdmin(botId));
        if (page < 1 || size < 1 || size > 100)
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "页码必须大于0，每页数量为1至100");
        long offset = ((long) page - 1) * size;
        return new PageResp<>(commentsMapper.selectCommentsByBotForAdmin(botId,offset,size),
            commentsMapper.countCommentsByBotForAdmin(botId),(long)page,(long)size);
    }

    /** 管理端独立审查视图，权限在 Controller；不复用公开可见性裁剪。 */
    @Transactional(readOnly=true)
    public CommunityResp.Thread commentThreadForAdmin(Long commentId) {
        Comments comment = commentsMapper.selectCommentsForAdminById(commentId);
        if (comment == null) throw new BusinessException(ErrorCode.NOT_FOUND,"评论不存在");
        Long rootId = commentsMapper.selectRootCommentIdForAdmin(commentId,comment.getPostId());
        if (rootId == null) throw new BusinessException(ErrorCode.NOT_FOUND,"评论线程不存在");
        long total = commentsMapper.countThreadCommentsForAdmin(rootId,comment.getPostId());
        List<Comments> thread = commentsMapper.selectThreadCommentsForAdmin(rootId,comment.getPostId(),200);
        return new CommunityResp.Thread(comment.getPostId(),comment.getPostTitle(),rootId,total,total>200,thread);
    }

    @Transactional(rollbackFor=Exception.class)
    public CommunityBot saveBot(Long id, CommunityReq.Bot req) {
        mapper.lockSettings();
        CommunityBot bot = id == null ? new CommunityBot() : requireBot(mapper.lockBot(id));
        bot.setName(req.name().trim()); bot.setAvatarUrl(req.avatarUrl()); bot.setPersonality(req.personality());
        bot.setSystemPrompt(req.systemPrompt());
        bot.setBackground(req.background()); bot.setInterests(req.interests()); bot.setEnabled(req.enabled());
        bot.setParticipation(req.participation());
        if (id == null) mapper.insertBot(bot); else mapper.updateBot(bot);
        log.info("社区角色配置已保存 botId={}", bot.getId());
        return mapper.bot(bot.getId());
    }
    @Transactional(rollbackFor=Exception.class)
    public void deleteBot(Long id) {
        mapper.lockSettings(); requireBot(mapper.lockBot(id)); mapper.deleteBot(id);
        log.info("社区角色已软删除 botId={}", id);
    }
    @Transactional(readOnly=true)
    public List<CommunityKnowledge> knowledge(Long botId) {
        requireBot(mapper.bot(botId)); return mapper.knowledge(botId);
    }
    @Transactional(rollbackFor=Exception.class)
    public CommunityKnowledge saveKnowledge(Long botId, Long id, CommunityReq.Knowledge req) {
        mapper.lockSettings(); requireBot(mapper.lockBot(botId));
        if (id == null && mapper.knowledge(botId).size() >= 100)
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "每个角色最多100条知识资料");
        CommunityKnowledge entry = new CommunityKnowledge(); entry.setBotId(botId); entry.setId(id);
        entry.setTitle(req.title().trim()); entry.setContent(req.content());
        if (id == null) mapper.insertKnowledge(entry);
        else if (mapper.updateKnowledge(entry) != 1) throw new BusinessException(ErrorCode.NOT_FOUND, "资料不存在");
        mapper.incrementBotVersion(botId);
        return mapper.knowledge(botId).stream().filter(k -> k.getId().equals(entry.getId())).findFirst().orElseThrow();
    }
    @Transactional(rollbackFor=Exception.class)
    public void deleteKnowledge(Long botId, Long id) {
        mapper.lockSettings(); requireBot(mapper.lockBot(botId));
        if (mapper.deleteKnowledge(botId,id) != 1) throw new BusinessException(ErrorCode.NOT_FOUND, "资料不存在");
        mapper.incrementBotVersion(botId); log.info("社区资料已删除 botId={} knowledgeId={}",botId,id);
    }
    @Transactional(rollbackFor=Exception.class)
    public CommunitySettings saveSettings(CommunityReq.Settings req) {
        if (req.minDelaySeconds() > req.maxDelaySeconds())
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "最短延迟不能大于最长延迟");
        CommunitySettings s = requireSettings(mapper.lockSettings());
        s.setEnabled(req.enabled()); s.setBotDailyCommentLimit(req.botDailyCommentLimit());
        s.setSiteDailyCommentLimit(req.siteDailyCommentLimit()); s.setPostDailyCommentLimit(req.postDailyCommentLimit());
        s.setBotDailyTaskLimit(req.botDailyTaskLimit()); s.setSiteDailyTaskLimit(req.siteDailyTaskLimit());
        s.setMinDelaySeconds(req.minDelaySeconds()); s.setMaxDelaySeconds(req.maxDelaySeconds());
        s.setCooldownSeconds(req.cooldownSeconds()); s.setMaxChainComments(req.maxChainComments());
        mapper.updateSettings(s); log.info("社区互动设置已修改 enabled={}",req.enabled()); return mapper.settings();
    }

    /** 文章保存路径在原事务内调用。旧文章需由管理员显式补评，开关本身不批量发任务。 */
    @Transactional(rollbackFor=Exception.class)
    public void articleSaved(Long postId) {
        Posts post = mapper.lockPost(postId);
        if (post == null || post.getDeletedAt() != null) return;
        mapper.ensurePost(postId);
        if (!"published".equals(post.getStatus()) || mapper.markPublished(postId) == 0) return;
        CommunitySettings s = requireSettings(mapper.lockSettings());
        if (!s.getEnabled() || !mapper.postState(postId).getEnabled()) return;
        enqueueInitialArticle(post, selectBots(post,null,null,2), s);
    }
    @Transactional(readOnly=true)
    public boolean postEnabled(Long id) {
        CommunityPostState state=mapper.postState(id); return state==null || Boolean.TRUE.equals(state.getEnabled());
    }

    @Transactional(rollbackFor=Exception.class)
    public void setPostEnabled(Long id, boolean enabled) {
        if (mapper.lockPost(id) == null) throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        mapper.lockSettings(); mapper.ensurePost(id); mapper.setPostEnabled(id,enabled);
    }
    @Transactional(rollbackFor=Exception.class)
    public CommunityResp.Queued invite(Long id, List<Long> requested) {
        Posts post = requirePublic(mapper.lockPost(id));
        CommunitySettings s = requireSettings(mapper.lockSettings()); mapper.ensurePost(id);
        requireEnabled(s, null, mapper.postState(id));
        List<CommunityBot> bots = requested == null || requested.isEmpty()
            ? selectBots(post,null,null,2) : explicitBots(requested);
        return new CommunityResp.Queued(bots.isEmpty() ? 0 : enqueue(post,null,"MANUAL_INVITE",newRoot(id),bots,s));
    }

    /** 显式小批补评：先按 ID 锁全部文章，再锁全局设置，复用初评事件与文章根链。 */
    @Transactional(rollbackFor=Exception.class, isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CommunityResp.Backfill backfill(CommunityReq.Backfill req) {
        if (req.limit() < 1 || req.limit() > 20)
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"每批补评文章数量为1至20");
        List<Long> ids = mapper.recentPublicPostIds(req.limit());
        List<Posts> posts = new ArrayList<>();
        for (Long id : ids.stream().distinct().sorted().toList()) {
            Posts post = mapper.lockPost(id);
            if (post != null && post.getDeletedAt() == null && "published".equals(post.getStatus())) posts.add(post);
        }
        CommunitySettings s = requireSettings(mapper.lockSettings());
        requireEnabled(s,null,null);
        List<CommunityBot> requested = req.botIds() == null || req.botIds().isEmpty() ? null : explicitBots(req.botIds());
        int queued = 0, skipped = 0;
        for (Posts post : posts) {
            mapper.ensurePost(post.getId());
            // 补评也消费首次公开机会，避免之后一次普通编辑重复触发初评。
            mapper.markPublished(post.getId());
            List<CommunityBot> bots = requested == null ? selectBots(post,null,null,2) : requested;
            if (!Boolean.TRUE.equals(mapper.postState(post.getId()).getEnabled())) {
                skipped += bots.size();
                continue;
            }
            int inserted = enqueueInitialArticle(post,bots,s);
            queued += inserted;
            skipped += bots.size() - inserted;
        }
        log.info("社区近期文章补评已安排 postCount={} queued={} skipped={}",posts.size(),queued,skipped);
        return new CommunityResp.Backfill(queued,skipped,posts.size());
    }
    /** 真人插入前取得文章锁，避免与机器人发布形成 comment -> post 的反向锁序。 */
    @Transactional(rollbackFor=Exception.class)
    public void lockCommentPost(Long postId) { requirePublic(mapper.lockPost(postId)); }

    /** 原真人评论事务中调用，独立根链；直接回复角色与提及使用 stable ID，去重上限2。 */
    @Transactional(rollbackFor=Exception.class)
    public void humanCommentCreated(Comments comment, List<Long> mentionedBotIds) {
        Posts post = requirePublic(mapper.lockPost(comment.getPostId()));
        LinkedHashSet<Long> targetIds = new LinkedHashSet<>();
        if (mentionedBotIds != null) targetIds.addAll(mentionedBotIds);
        if (comment.getParentId() != null) {
            Comments parent = commentsMapper.selectPublicCommentById(comment.getParentId());
            if (parent != null && parent.getBotId() != null) targetIds.add(parent.getBotId());
        }
        if (targetIds.size() > 2) throw new BusinessException(ErrorCode.PARAMS_ERROR,"一次最多邀请两个角色");
        // 显式提及必须存在；历史 AI 评论作者停用或软删不应使普通真人回复回滚。
        if (mentionedBotIds != null) for (Long target : mentionedBotIds) requireBot(mapper.bot(target));
        CommunitySettings s = requireSettings(mapper.lockSettings()); mapper.ensurePost(post.getId());
        if (!s.getEnabled() || !mapper.postState(post.getId()).getEnabled()) return;
        List<CommunityBot> bots = targetIds.isEmpty() ? selectBots(post,comment,null,1)
            : targetIds.stream().map(mapper::bot).filter(Objects::nonNull).filter(b -> b.getEnabled()).toList();
        if (!bots.isEmpty()) enqueue(post,comment.getId(),"HUMAN_COMMENT",newRoot(post.getId()),bots,s);
    }

    @Transactional(rollbackFor=Exception.class)
    public List<CommunityEvent> claim(CommunityReq.Claim req) {
        // 领取不依赖开关；已暂停任务由执行/提交检查安全终结，不积压永久旧事件。
        List<CommunityEvent> events = mapper.claimable(req.limit());
        for (CommunityEvent event : events) {
            String token = UUID.randomUUID().toString(); mapper.lease(event.getId(),token,req.leaseSeconds());
            event.setLeaseToken(token);
        }
        return events;
    }
    @Transactional(rollbackFor=Exception.class)
    public void ack(Long id, String token) { mapper.ack(id,token); }

    /** 内部只读预演允许开关关闭，始终限制公开文章、当前线程和角色自己的资料。 */
    @Transactional(readOnly=true)
    public CommunityResp.Context context(Long botId, Long postId, Long commentId) {
        CommunityBot bot = requireBot(mapper.bot(botId)); Posts post = requirePublic(mapper.publicPost(postId));
        CommunitySettings settings = settings(); CommunityPostState state = mapper.postState(postId);
        if (commentId != null) {
            Comments comment = commentsMapper.selectPublicCommentById(commentId);
            if (comment == null || !postId.equals(comment.getPostId()))
                throw new BusinessException(ErrorCode.PARENT_COMMENT_NOT_FOUND);
        }
        List<Comments> comments = commentId == null ? commentsMapper.selectRecentForCommunity(postId) : mapper.threadComments(postId,commentId);
        List<CommunityKnowledge> knowledge = mapper.knowledge(botId);
        return new CommunityResp.Context(bot,settings,
            new CommunityResp.PostInfo(postId,post.getTitle(),post.getContent(),post.getSummary(),post.getUpdatedAt()),
            comments,knowledge,contextVersion(post,bot,settings,state,commentId,comments), state == null || state.getEnabled());
    }

    @Transactional(rollbackFor=Exception.class, isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public CommunityResp.Attempt authorizeAttempt(CommunityReq.Attempt req) {
        validUuid(req.taskId());
        Posts post = requirePublic(mapper.lockPost(req.postId()));
        CommunitySettings s = requireSettings(mapper.lockSettings()); CommunityBot bot = requireBot(mapper.lockBot(req.botId()));
        CommunityResp.Attempt existing = mapper.attempt(req.taskId(),req.attempt());
        CommunityPostState state = mapper.postState(post.getId());
        String reason = "";
        if (!req.preview() && (!s.getEnabled() || !bot.getEnabled() || state != null && !state.getEnabled())) reason="互动已暂停";
        if (existing != null) return reason.isEmpty() ? existing : new CommunityResp.Attempt(false,reason);
        Date[] day = dayRange(Instant.now());
        if (reason.isEmpty() && (mapper.attemptCount(null,day[0],day[1]) >= s.getSiteDailyTaskLimit()
            || mapper.attemptCount(bot.getId(),day[0],day[1]) >= s.getBotDailyTaskLimit())) reason="今日模型任务额度已用完";
        boolean allowed = reason.isEmpty();
        mapper.recordAttempt(req.taskId(),req.attempt(),req.botId(),req.postId(),allowed,reason);
        return new CommunityResp.Attempt(allowed,reason);
    }

    @Transactional(rollbackFor=Exception.class, isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @CacheEvict(value={"hotPosts","latestPosts","postList"},allEntries=true)
    public CommunityResp.Published publish(CommunityReq.Publish req) {
        validUuid(req.taskId()); validUuid(req.rootEventId());
        // 幂等回执独立于评论保存，即使管理员后来删除评论，也不会重新生成。
        CommunityResp.Published duplicate = mapper.publication(req.taskId());
        if (duplicate != null) return duplicate;
        Posts post = requirePublic(mapper.lockPost(req.postId()));
        CommunitySettings settings = requireSettings(mapper.lockSettings());
        CommunityBot bot = requireBot(mapper.lockBot(req.botId()));
        duplicate = mapper.publication(req.taskId()); if (duplicate != null) return duplicate;
        mapper.ensurePost(post.getId()); requireEnabled(settings,bot,mapper.postState(post.getId()));
        // 锁定文章现有评论及索引范围，让父评论删除、同文章新增与最终版本核验有确定顺序。
        mapper.lockPostComments(post.getId());
        CommunityResp.Context current = context(bot.getId(),post.getId(),req.contextCommentId());
        if (!MessageDigest.isEqual(current.contextVersion().getBytes(StandardCharsets.UTF_8),
                req.contextVersion().getBytes(StandardCharsets.UTF_8))) throw new BusinessException(ErrorCode.COMMUNITY_STALE_CONTEXT);
        Long parentId = req.parentId();
        if (parentId != null) {
            Comments parent = mapper.lockComment(parentId);
            if (parent == null || !post.getId().equals(parent.getPostId())) throw new BusinessException(ErrorCode.PARENT_COMMENT_MISMATCH);
            if (current.comments().stream().noneMatch(c -> c.getId().equals(parentId)))
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"只能回复本轮已读取的评论");
            if (Objects.equals(parent.getBotId(),bot.getId()))
                throw new BusinessException(ErrorCode.PARAMS_ERROR,"角色不能回复自己的评论");
        }
        Integer chainCount = mapper.chainCount(req.rootEventId(),req.postId());
        if (chainCount == null || chainCount >= settings.getMaxChainComments()) throw new BusinessException(ErrorCode.COMMUNITY_CHAIN_LIMIT);
        Date now = new Date(); Date[] day = dayRange(now.toInstant());
        if (mapper.publicationCount(null,null,day[0],day[1]) >= settings.getSiteDailyCommentLimit()
            || mapper.publicationCount(bot.getId(),null,day[0],day[1]) >= settings.getBotDailyCommentLimit()
            || mapper.publicationCount(null,post.getId(),day[0],day[1]) >= settings.getPostDailyCommentLimit())
            throw new BusinessException(ErrorCode.COMMUNITY_QUOTA_EXCEEDED);
        Date last = mapper.lastPublication(bot.getId(),post.getId());
        if (last != null && now.getTime()-last.getTime() < settings.getCooldownSeconds()*1000L)
            throw new BusinessException(ErrorCode.COMMUNITY_COOLDOWN);
        Comments comment = new Comments(); comment.setBotId(bot.getId()); comment.setPostId(post.getId());
        comment.setParentId(parentId); comment.setContent(req.content().trim()); comment.setCommunityTaskId(req.taskId());
        comment.setRootEventId(req.rootEventId()); comment.setCreatedAt(now); comment.setUpdatedAt(now);
        // 明确 SQL 插入，避免 MyMetaObjectHandler 从管理员认证上下文自动填入真人作者。
        commentsMapper.insertCommunityComment(comment);
        mapper.recordPublication(req.taskId(),bot.getId(),post.getId(),comment.getId(),now); mapper.incrementChain(req.rootEventId());
        if (chainCount+1 < settings.getMaxChainComments())
            enqueue(post,comment.getId(),"BOT_COMMENT",req.rootEventId(),selectBots(post,comment,bot.getId(),1),settings);
        log.info("社区角色评论已发布 botId={} postId={} commentId={} taskId={}",bot.getId(),post.getId(),comment.getId(),req.taskId());
        return new CommunityResp.Published(comment.getId(),now,false);
    }

    @Transactional(readOnly=true)
    public CommunityResp.Visibility visibility(CommunityReq.Visibility req) {
        List<Long> posts = req.sourcePostIds().stream().distinct().filter(id -> mapper.publicPost(id) != null).toList();
        List<Long> comments = req.sourceCommentIds().stream().distinct()
            .filter(id -> commentsMapper.selectPublicCommentById(id) != null).toList();
        return new CommunityResp.Visibility(posts,comments);
    }

    /** 公开事实的批量标签；不返回用户资料或隐藏文章/评论，不增加浏览量。 */
    @Transactional(readOnly=true)
    public CommunityResp.Metadata metadata(CommunityReq.Metadata req) {
        List<CommunityResp.PostMetadata> posts = req.postIds().isEmpty() ? List.of() : mapper.publicPostMetadata(req.postIds().stream().distinct().toList())
            .stream().map(post -> new CommunityResp.PostMetadata(post.getId(),post.getTitle())).toList();
        List<CommunityResp.CommentMetadata> comments = req.commentIds().isEmpty() ? List.of() : commentsMapper.selectPublicCommentsByIds(req.commentIds().stream().distinct().toList())
            .stream().map(comment -> new CommunityResp.CommentMetadata(comment.getId(),comment.getContent(),
                comment.getBotId() != null && comment.getBot() != null ? comment.getBot().name()
                    : comment.getUser() == null ? "用户" : comment.getUser().getUsername())).toList();
        return new CommunityResp.Metadata(posts,comments);
    }

    private String newRoot(Long postId) { String root=UUID.randomUUID().toString(); mapper.insertChain(root,postId); return root; }
    /**
     * 已失败或已确认的初评事件也保留去重；删除评论后发布回执仍阻止再次补评。
     * ACK 只表示 AI 已接收，不能当作执行完成，所以已有人工邀请也保守跳过；显式邀请可再试。
     */
    private int enqueueInitialArticle(Posts post, List<CommunityBot> bots, CommunitySettings s) {
        List<CommunityBot> eligible = bots.stream().filter(bot ->
            mapper.lockArticleInvitation(post.getId(),bot.getId()) == null
                && mapper.lockArticlePublication(bot.getId(),post.getId()) == null).toList();
        if (eligible.isEmpty()) return 0;
        String root = mapper.initialArticleRoot(post.getId());
        if (root == null) root = newRoot(post.getId());
        else {
            Integer emitted = mapper.chainCount(root,post.getId());
            if (emitted == null || emitted >= s.getMaxChainComments()) return 0;
        }
        return enqueue(post,null,"ARTICLE_PUBLISHED",root,eligible,s);
    }
    private int enqueue(Posts post, Long commentId, String type, String root, List<CommunityBot> bots, CommunitySettings s) {
        int queued = 0;
        for (CommunityBot bot : bots) {
            CommunityEvent event = new CommunityEvent(); event.setBotId(bot.getId()); event.setPostId(post.getId());
            event.setCommentId(commentId); event.setEventType(type); event.setRootEventId(root);
            int delay = ThreadLocalRandom.current().nextInt(s.getMinDelaySeconds(),s.getMaxDelaySeconds()+1);
            event.setAvailableAt(Date.from(Instant.now().plusSeconds(delay)));
            String key = type+":"+("MANUAL_INVITE".equals(type) ? root : commentId == null ? post.getId() : commentId)+":"+bot.getId();
            queued += mapper.insertEvent(key,event);
        }
        return queued;
    }
    private List<CommunityBot> explicitBots(List<Long> ids) {
        if (ids.size()>2 || ids.stream().anyMatch(id -> id == null || id <= 0))
            throw new BusinessException(ErrorCode.PARAMS_ERROR,"最多邀请两个有效角色");
        return ids.stream().distinct().map(id -> requireBot(mapper.bot(id))).peek(b -> {
            if (!b.getEnabled()) throw new BusinessException(ErrorCode.COMMUNITY_PAUSED);
        }).toList();
    }
    private List<CommunityBot> selectBots(Posts post, Comments comment, Long excluded, int limit) {
        String text=Objects.toString(post.getTitle(),"")+" "+Objects.toString(post.getSummary(),"")+" "+
            Objects.toString(post.getContent(),"")+" "+(comment == null ? "" : comment.getContent());
        return mapper.bots().stream().filter(b -> b.getEnabled() && !Objects.equals(b.getId(),excluded) && b.getParticipation()>0)
            .sorted(Comparator.<CommunityBot>comparingInt(b -> relevance(b,text)).reversed().thenComparing(CommunityBot::getId))
            .limit(limit).toList();
    }
    private static int relevance(CommunityBot bot,String text) {
        String lower=text.toLowerCase(Locale.ROOT);
        int score=bot.getParticipation();
        String interests=Objects.toString(bot.getInterests(),"");
        if (interests.isBlank()) return score;
        int matched=0;
        for (String interest:interests.split("[,，;；\\s]+")) if (!interest.isBlank() && lower.contains(interest.toLowerCase(Locale.ROOT))) matched++;
        return matched*100+score;
    }
    private static CommunitySettings requireSettings(CommunitySettings settings) {
        if (settings==null) throw new BusinessException(ErrorCode.OPERATION_ERROR,"社区数据库尚未初始化"); return settings;
    }
    private static CommunityBot requireBot(CommunityBot bot) {
        if (bot==null) throw new BusinessException(ErrorCode.COMMUNITY_BOT_NOT_FOUND); return bot;
    }
    private static Posts requirePublic(Posts post) {
        if (post==null || post.getDeletedAt()!=null || !"published".equals(post.getStatus())) throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        return post;
    }
    private static void requireEnabled(CommunitySettings settings,CommunityBot bot,CommunityPostState post) {
        if (!settings.getEnabled() || bot!=null && !bot.getEnabled() || post!=null && !post.getEnabled()) throw new BusinessException(ErrorCode.COMMUNITY_PAUSED);
    }
    public static CommunityResp.BotInfo botInfo(CommunityBot bot) { return new CommunityResp.BotInfo(bot.getId(),bot.getName(),bot.getAvatarUrl()); }
    static Date[] dayRange(Instant now) {
        ZonedDateTime start=now.atZone(DAY_ZONE).toLocalDate().atStartOfDay(DAY_ZONE);
        return new Date[]{Date.from(start.toInstant()),Date.from(start.plusDays(1).toInstant())};
    }
    private static void validUuid(String value) {
        try { if (!UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException e) { throw new BusinessException(ErrorCode.PARAMS_ERROR,"任务与触发链ID必须是标准UUID"); }
    }
    private static String contextVersion(Posts post,CommunityBot bot,CommunitySettings settings,CommunityPostState state,
                                         Long contextCommentId,List<Comments> comments) {
        StringBuilder data=new StringBuilder();
        // 长度前缀避免文本分隔符碰撞；不包含会随真人浏览增长的浏览量。
        for (Object value:Arrays.asList(post.getId(),post.getTitle(),post.getContent(),post.getSummary(),post.getStatus(),
                bot.getId(),bot.getVersion(),settings.getVersion(),state==null ? 1L:state.getVersion(),contextCommentId)) append(data,value);
        for (Comments c:comments) for (Object value:Arrays.asList(c.getId(),c.getContent(),c.getParentId(),c.getBotId(),c.getUserId())) append(data,value);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private static void append(StringBuilder data,Object value) {
        String text=value instanceof Date d ? Long.toString(d.getTime()) : Objects.toString(value,"null");
        data.append(text.length()).append(':').append(text);
    }
}
