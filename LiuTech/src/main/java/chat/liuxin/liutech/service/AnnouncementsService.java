package chat.liuxin.liutech.service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.AnnouncementsMapper;
import chat.liuxin.liutech.model.Announcements;
import chat.liuxin.liutech.req.AnnouncementReq;
import chat.liuxin.liutech.resl.AnnouncementResl;

/**
 * 公告服务类 - 重构优化版本
 * 
 * 主要功能：
 * 1. 公告的增删改查操作
 * 2. 公告有效性验证和时间范围检查
 * 3. 缓存管理和软删除支持
 * 4. 数据转换和业务逻辑处理
 * 
 * 重构优化：
 * - 将复杂函数拆分为更小的单一职责函数
 * - 提取公共验证逻辑，减少代码重复
 * - 优化查询条件构建和数据转换流程
 * - 增强代码可读性和可维护性
 * 
 * @author 刘鑫
 * @version 2.0 - 重构优化版本
 */
@Service
public class AnnouncementsService extends ServiceImpl<AnnouncementsMapper, Announcements> {

    @Autowired
    private AnnouncementsMapper announcementsMapper;

    /**
     * 获取有效公告（分页）
     * @param current 当前页
     * @param size 每页大小
     * @return 公告分页数据
     */
    public IPage<AnnouncementResl> getValidAnnouncements(long current, long size) {
        Page<Announcements> page = new Page<>(current, size);
        IPage<Announcements> announcementPage = announcementsMapper.selectValidAnnouncements(page);
        return announcementPage.convert(this::convertToResl);
    }

    /**
     * 获取置顶公告
     * @param limit 限制数量
     * @return 置顶公告列表
     */
    public List<AnnouncementResl> getTopAnnouncements(Integer limit) {
        Integer validLimit = validateAndSetDefaultLimit(limit, 5);
        List<Announcements> announcements = announcementsMapper.selectTopAnnouncements(validLimit);
        return convertAnnouncementsList(announcements);
    }

    /**
     * 获取最新公告
     * @param limit 限制数量
     * @return 最新公告列表
     */
    @Cacheable(value = "announcements", key = "'latest_' + #limit", unless = "#result == null || #result.isEmpty()")
    public List<AnnouncementResl> getLatestAnnouncements(Integer limit) {
        Integer validLimit = validateAndSetDefaultLimit(limit, 10);
        List<Announcements> announcements = announcementsMapper.selectLatestAnnouncements(validLimit);
        return convertAnnouncementsList(announcements);
    }

    /**
     * 根据ID获取公告详情
     * @param id 公告ID
     * @return 公告详情
     */
    @Transactional(rollbackFor = Exception.class)
    public AnnouncementResl getAnnouncementById(Long id) {
        validateAnnouncementId(id);
        Announcements announcement = getValidAnnouncementById(id);
        incrementViewCount(announcement);
        return convertToResl(announcement);
    }

    /**
     * 创建公告
     * @param req 公告请求数据
     * @return 公告ID
     */
    @Transactional
    @CacheEvict(value = "announcements", allEntries = true)
    public Long createAnnouncement(AnnouncementReq req) {
        validateAnnouncementReq(req);
        Announcements announcement = buildAnnouncementFromReq(req);
        saveAnnouncementWithValidation(announcement);
        return announcement.getId();
    }

    /**
     * 更新公告
     * @param req 公告请求数据
     * @return 是否成功
     */
    @Transactional
    @CacheEvict(value = "announcements", allEntries = true)
    public boolean updateAnnouncement(AnnouncementReq req) {
        validateAnnouncementId(req.getId());
        validateAnnouncementReq(req);
        validateAnnouncementExists(req.getId());
        
        Announcements announcement = new Announcements();
        BeanUtils.copyProperties(req, announcement);
        return this.updateById(announcement);
    }

    /**
     * 删除公告（软删除）
     * @param id 公告ID
     * @return 是否成功
     */
    @Transactional
    @CacheEvict(value = "announcements", allEntries = true)
    public boolean deleteAnnouncement(Long id) {
        validateAnnouncementId(id);
        validateAnnouncementExistsAndNotDeleted(id);
        return performSoftDelete(id);
    }

    /**
     * 管理员分页查询所有公告
     * @param current 当前页
     * @param size 每页大小
     * @param status 状态筛选
     * @param type 类型筛选
     * @param includeDeleted 是否包含已删除的公告
     * @return 公告分页数据
     */
    public IPage<AnnouncementResl> getAllAnnouncements(long current, long size, Integer status, Integer type, Boolean includeDeleted) {
        Page<Announcements> page = new Page<>(current, size);
        QueryWrapper<Announcements> queryWrapper = buildAnnouncementQueryWrapper(status, type, includeDeleted);
        IPage<Announcements> announcementPage = this.page(page, queryWrapper);
        return announcementPage.convert(this::convertToResl);
    }

    /**
     * 批量删除公告
     * @param ids 公告ID列表
     * @return 是否成功
     */
    @Transactional
    @CacheEvict(value = "announcements", allEntries = true)
    public boolean batchDeleteAnnouncements(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "公告ID列表不能为空");
        }
        
        // 验证所有公告都存在且未删除
        for (Long id : ids) {
            validateAnnouncementExistsAndNotDeleted(id);
        }
        
        // 批量软删除
        LambdaUpdateWrapper<Announcements> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.in(Announcements::getId, ids)
                .set(Announcements::getDeletedAt, new Date());
        return this.update(updateWrapper);
    }

    /**
     * 更新公告状态
     * @param id 公告ID
     * @param status 新状态
     * @return 是否成功
     */
    @Transactional
    @CacheEvict(value = "announcements", allEntries = true)
    public boolean updateAnnouncementStatus(Long id, Integer status) {
        validateAnnouncementId(id);
        validateAnnouncementStatus(status);
        validateAnnouncementExistsAndNotDeleted(id);
        
        LambdaUpdateWrapper<Announcements> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Announcements::getId, id)
                .set(Announcements::getStatus, status);
        return this.update(updateWrapper);
    }

    /**
     * 批量更新公告状态
     * @param ids 公告ID列表
     * @param status 新状态
     * @return 是否成功
     */
    @Transactional
    @CacheEvict(value = "announcements", allEntries = true)
    public boolean batchUpdateAnnouncementStatus(List<Long> ids, Integer status) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "公告ID列表不能为空");
        }
        validateAnnouncementStatus(status);
        
        // 验证所有公告都存在且未删除
        for (Long id : ids) {
            validateAnnouncementExistsAndNotDeleted(id);
        }
        
        LambdaUpdateWrapper<Announcements> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.in(Announcements::getId, ids)
                .set(Announcements::getStatus, status);
        return this.update(updateWrapper);
    }

    /**
     * 恢复已删除的公告
     * @param id 公告ID
     * @return 是否成功
     */
    @Transactional
    @CacheEvict(value = "announcements", allEntries = true)
    public boolean restoreAnnouncement(Long id) {
        validateAnnouncementId(id);
        
        Announcements announcement = this.getById(id);
        if (announcement == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        if (announcement.getDeletedAt() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "公告未被删除，无需恢复");
        }
        
        LambdaUpdateWrapper<Announcements> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Announcements::getId, id)
                .set(Announcements::getDeletedAt, null);
        return this.update(updateWrapper);
    }

    /**
     * 置顶/取消置顶公告
     * @param id 公告ID
     * @param isTop 是否置顶(0否,1是)
     * @return 是否成功
     */
    @Transactional
    @CacheEvict(value = "announcements", allEntries = true)
    public boolean toggleAnnouncementTop(Long id, Integer isTop) {
        validateAnnouncementId(id);
        if (isTop == null || (isTop != 0 && isTop != 1)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "置顶状态参数错误");
        }
        validateAnnouncementExistsAndNotDeleted(id);
        
        LambdaUpdateWrapper<Announcements> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Announcements::getId, id)
                .set(Announcements::getIsTop, isTop);
        return this.update(updateWrapper);
    }

    /**
     * 验证并设置默认限制数量
     * @param limit 输入的限制数量
     * @param defaultValue 默认值
     * @return 有效的限制数量
     */
    private Integer validateAndSetDefaultLimit(Integer limit, Integer defaultValue) {
        return (limit == null || limit <= 0) ? defaultValue : limit;
    }

    /**
     * 转换公告列表
     * @param announcements 公告实体列表
     * @return 公告响应列表
     */
    private List<AnnouncementResl> convertAnnouncementsList(List<Announcements> announcements) {
        return announcements.stream().map(this::convertToResl).collect(Collectors.toList());
    }

    /**
     * 验证公告ID
     * @param id 公告ID
     */
    private void validateAnnouncementId(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "公告ID不能为空");
        }
    }

    /**
     * 根据ID获取有效公告
     * @param id 公告ID
     * @return 公告实体
     */
    private Announcements getValidAnnouncementById(Long id) {
        Announcements announcement = this.getById(id);
        if (announcement == null || announcement.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        return announcement;
    }

    /**
     * 增加浏览量
     * @param announcement 公告实体
     */
    private void incrementViewCount(Announcements announcement) {
        announcement.setViewCount(announcement.getViewCount() + 1);
        this.updateById(announcement);
    }

    /**
     * 从请求构建公告实体
     * @param req 公告请求
     * @return 公告实体
     */
    private Announcements buildAnnouncementFromReq(AnnouncementReq req) {
        Announcements announcement = new Announcements();
        BeanUtils.copyProperties(req, announcement);
        announcement.setViewCount(0);
        
        if (announcement.getIsTop() == null) {
            announcement.setIsTop(0);
        }
        
        return announcement;
    }

    /**
     * 保存公告并验证结果
     * @param announcement 公告实体
     */
    private void saveAnnouncementWithValidation(Announcements announcement) {
        boolean success = this.save(announcement);
        if (!success) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建公告失败");
        }
    }

    /**
     * 验证公告是否存在
     * @param id 公告ID
     */
    private void validateAnnouncementExists(Long id) {
        Announcements existingAnnouncement = this.getById(id);
        if (existingAnnouncement == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }
    }

    /**
     * 验证公告存在且未删除
     * @param id 公告ID
     */
    private void validateAnnouncementExistsAndNotDeleted(Long id) {
        Announcements announcement = this.getById(id);
        if (announcement == null || announcement.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }
    }

    /**
     * 执行软删除
     * @param id 公告ID
     * @return 是否成功
     */
    private boolean performSoftDelete(Long id) {
        LambdaUpdateWrapper<Announcements> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(Announcements::getId, id)
                .set(Announcements::getDeletedAt, new Date());
        return this.update(updateWrapper);
    }

    /**
     * 构建公告查询条件
     * @param status 状态筛选
     * @param type 类型筛选
     * @param includeDeleted 是否包含已删除的公告
     * @return 查询条件
     */
    private QueryWrapper<Announcements> buildAnnouncementQueryWrapper(Integer status, Integer type, Boolean includeDeleted) {
        QueryWrapper<Announcements> queryWrapper = new QueryWrapper<>();
        
        // 如果不包含已删除的公告，则只查询未删除的
        if (includeDeleted == null || !includeDeleted) {
            queryWrapper.isNull("deleted_at");
        }
        
        if (status != null) {
            queryWrapper.eq("status", status);
        }
        if (type != null) {
            queryWrapper.eq("type", type);
        }
        
        queryWrapper.orderByDesc("is_top", "priority", "created_at");
        return queryWrapper;
    }


    /**
     * 验证公告请求数据
     * @param req 公告请求数据
     */
    private void validateAnnouncementReq(AnnouncementReq req) {
        validateAnnouncementType(req.getType());
        validateAnnouncementPriority(req.getPriority());
        validateAnnouncementStatus(req.getStatus());
        validateAnnouncementTimeRange(req.getStartTime(), req.getEndTime());
    }

    /**
     * 验证公告类型
     * @param type 公告类型
     */
    private void validateAnnouncementType(Integer type) {
        if (type == null || type < 1 || type > 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "公告类型无效");
        }
    }

    /**
     * 验证公告优先级
     * @param priority 优先级
     */
    private void validateAnnouncementPriority(Integer priority) {
        if (priority == null || priority < 1 || priority > 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "优先级无效");
        }
    }

    /**
     * 验证公告状态
     * @param status 状态
     */
    private void validateAnnouncementStatus(Integer status) {
        if (status == null || status < 0 || status > 2) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "状态无效");
        }
    }

    /**
     * 验证公告时间范围
     * @param startTime 开始时间
     * @param endTime 结束时间
     */
    private void validateAnnouncementTimeRange(Date startTime, Date endTime) {
        if (startTime != null && endTime != null && startTime.after(endTime)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "开始时间不能晚于结束时间");
        }
    }

    /**
     * 转换为响应数据
     * @param announcement 公告实体
     * @return 公告响应数据
     */
    private AnnouncementResl convertToResl(Announcements announcement) {
        AnnouncementResl resl = new AnnouncementResl();
        BeanUtils.copyProperties(announcement, resl);
        
        setAnnouncementNames(resl, announcement);
        setAnnouncementValidity(resl, announcement);
        
        return resl;
    }

    /**
     * 设置公告名称信息
     * @param resl 响应对象
     * @param announcement 公告实体
     */
    private void setAnnouncementNames(AnnouncementResl resl, Announcements announcement) {
        resl.setTypeName(getTypeName(announcement.getType()));
        resl.setPriorityName(getPriorityName(announcement.getPriority()));
        resl.setStatusName(getStatusName(announcement.getStatus()));
    }

    /**
     * 设置公告有效性
     * @param resl 响应对象
     * @param announcement 公告实体
     */
    private void setAnnouncementValidity(AnnouncementResl resl, Announcements announcement) {
        Date now = new Date();
        boolean isValid = isAnnouncementValid(announcement, now);
        resl.setIsValid(isValid);
    }

    /**
     * 判断公告是否有效
     * @param announcement 公告实体
     * @param now 当前时间
     * @return 是否有效
     */
    private boolean isAnnouncementValid(Announcements announcement, Date now) {
        return announcement.getStatus() == 1 && 
               isTimeRangeValid(announcement.getStartTime(), announcement.getEndTime(), now);
    }

    /**
     * 判断时间范围是否有效
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param now 当前时间
     * @return 是否有效
     */
    private boolean isTimeRangeValid(Date startTime, Date endTime, Date now) {
        boolean startValid = startTime == null || !startTime.after(now);
        boolean endValid = endTime == null || !endTime.before(now);
        return startValid && endValid;
    }

    /**
     * 获取类型名称
     * @param type 类型
     * @return 类型名称
     */
    private String getTypeName(Integer type) {
        if (type == null) return "未知";
        switch (type) {
            case 1: return "系统";
            case 2: return "活动";
            case 3: return "维护";
            case 4: return "其他";
            default: return "未知";
        }
    }

    /**
     * 获取优先级名称
     * @param priority 优先级
     * @return 优先级名称
     */
    private String getPriorityName(Integer priority) {
        if (priority == null) return "未知";
        switch (priority) {
            case 1: return "低";
            case 2: return "中";
            case 3: return "高";
            case 4: return "紧急";
            default: return "未知";
        }
    }

    /**
     * 获取状态名称
     * @param status 状态
     * @return 状态名称
     */
    private String getStatusName(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "草稿";
            case 1: return "发布";
            case 2: return "下线";
            default: return "未知";
        }
    }
}