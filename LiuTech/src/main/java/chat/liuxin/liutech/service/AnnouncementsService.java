package chat.liuxin.liutech.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.AnnouncementsMapper;
import chat.liuxin.liutech.model.AnnouncementExcelData;
import chat.liuxin.liutech.model.Announcements;
import chat.liuxin.liutech.req.AnnouncementReq;
import chat.liuxin.liutech.resp.AnnouncementResp;

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
@RequiredArgsConstructor
 extends ServiceImpl<AnnouncementsMapper, Announcements> {

    private final AnnouncementsMapper announcementsMapper;

    /**
     * 获取有效公告（分页）
     * @param current 当前页
     * @param size 每页大小
     * @return 公告分页数据
     */
    @Transactional(readOnly = true)
    public IPage<AnnouncementResp> getValidAnnouncements(long current, long size) {
        Page<Announcements> page = new Page<>(current, size);
        IPage<Announcements> announcementPage = announcementsMapper.selectValidAnnouncements(page);
        return announcementPage.convert(this::convertToResl);
    }

    /**
     * 获取置顶公告
     * @param limit 限制数量
     * @return 置顶公告列表
     */
    @Transactional(readOnly = true)
    public List<AnnouncementResp> getTopAnnouncements(Integer limit) {
        Integer validLimit = validateAndSetDefaultLimit(limit, 5);
        List<Announcements> announcements = announcementsMapper.selectTopAnnouncements(validLimit);
        return convertAnnouncementsList(announcements);
    }

    /**
     * 获取最新公告
     * @param limit 限制数量
     * @return 最新公告列表
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "announcements", key = "#limit", unless = "#result == null || #result.isEmpty()")
    public List<AnnouncementResp> getLatestAnnouncements(Integer limit) {
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
    public AnnouncementResp getAnnouncementById(Long id) {
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
    @Transactional(readOnly = true)
    public IPage<AnnouncementResp> getAllAnnouncements(long current, long size, Integer status, Integer type, Boolean includeDeleted) {
        return getAllAnnouncements(current, size, status, type, null, includeDeleted);
    }

    @Transactional(readOnly = true)
    public IPage<AnnouncementResp> getAllAnnouncements(long current, long size, Integer status, Integer type, String keyword, Boolean includeDeleted) {
        Page<Announcements> page = new Page<>(current, size);
        QueryWrapper<Announcements> queryWrapper = buildAnnouncementQueryWrapper(status, type, keyword, includeDeleted);
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

        Announcements announcement = announcementsMapper.selectAllById(id);
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
     * 物理删除单条公告（绕过 @TableLogic，直接 DELETE）
     * @param id 公告ID
     * @return 是否成功
     */
    @Transactional
    @CacheEvict(value = "announcements", allEntries = true)
    public boolean permanentDeleteAnnouncement(Long id) {
        validateAnnouncementId(id);

        Announcements announcement = announcementsMapper.selectAllById(id);
        if (announcement == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }

        int result = announcementsMapper.permanentDeleteById(id);
        return result > 0;
    }

    /**
     * 批量物理删除公告（绕过 @TableLogic，直接 DELETE）
     * @param ids 公告ID列表
     * @return 是否成功
     */
    @Transactional
    @CacheEvict(value = "announcements", allEntries = true)
    public boolean batchPermanentDeleteAnnouncements(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "公告ID列表不能为空");
        }

        int result = announcementsMapper.batchPermanentDeleteByIds(ids);
        return result > 0;
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
     * 批量置顶/取消置顶公告
     * @param ids 公告ID列表
     * @param isTop 是否置顶(0否,1是)
     * @return 是否成功
     */
    @Transactional
    @CacheEvict(value = "announcements", allEntries = true)
    public boolean batchToggleAnnouncementTop(List<Long> ids, Integer isTop) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "公告ID列表不能为空");
        }
        if (isTop == null || (isTop != 0 && isTop != 1)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "置顶状态参数错误");
        }

        for (Long id : ids) {
            validateAnnouncementExistsAndNotDeleted(id);
        }

        LambdaUpdateWrapper<Announcements> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.in(Announcements::getId, ids)
                .set(Announcements::getIsTop, isTop);
        return this.update(updateWrapper);
    }

    /**
     * 导出公告数据为Excel
     * @param status 状态筛选
     * @param type 类型筛选
     * @param keyword 关键词筛选
     * @param includeDeleted 是否包含已删除
     * @param outputStream 输出流
     */
    @Transactional(readOnly = true)
    public void exportToExcel(Integer status, Integer type, String keyword, Boolean includeDeleted, OutputStream outputStream) {
        QueryWrapper<Announcements> queryWrapper = buildAnnouncementQueryWrapper(status, type, keyword, includeDeleted);
        List<Announcements> announcements = this.list(queryWrapper);

        List<AnnouncementExcelData> excelDataList = announcements.stream()
                .map(this::convertToExcelData)
                .collect(Collectors.toList());

        EasyExcel.write(outputStream, AnnouncementExcelData.class)
                .sheet("公告数据")
                .doWrite(excelDataList);
    }

    /**
     * 从Excel导入公告
     * @param file 上传的Excel文件
     * @return 导入结果 [成功数, 失败数, 错误信息列表]
     */
    @Transactional
    @CacheEvict(value = "announcements", allEntries = true)
    public Map<String, Object> importFromExcel(MultipartFile file) {
        List<String> errors = new ArrayList<>();
        int[] counts = {0, 0}; // [success, failed]

        try (InputStream inputStream = file.getInputStream()) {
            EasyExcel.read(inputStream, AnnouncementExcelData.class, new ReadListener<AnnouncementExcelData>() {
                @Override
                public void invoke(AnnouncementExcelData data, AnalysisContext context) {
                    try {
                        AnnouncementReq req = convertExcelDataToReq(data);
                        validateAnnouncementReq(req);
                        Announcements announcement = buildAnnouncementFromReq(req);
                        saveAnnouncementWithValidation(announcement);
                        counts[0]++;
                    } catch (Exception e) {
                        counts[1]++;
                        int rowNum = context.readRowHolder().getRowIndex() + 1;
                        errors.add("第" + rowNum + "行: " + e.getMessage());
                    }
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
                    // 读取完成
                }
            }).sheet().doRead();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "读取Excel文件失败: " + e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", counts[0]);
        result.put("failed", counts[1]);
        result.put("errors", errors);
        return result;
    }

    /**
     * 将公告实体转换为Excel数据模型
     * @param announcement 公告实体
     * @return Excel数据模型
     */
    private AnnouncementExcelData convertToExcelData(Announcements announcement) {
        AnnouncementExcelData data = new AnnouncementExcelData();
        data.setId(announcement.getId());
        data.setTitle(announcement.getTitle());
        data.setContent(announcement.getContent());
        data.setType(getTypeName(announcement.getType()));
        data.setPriority(getPriorityName(announcement.getPriority()));
        data.setStatus(getStatusName(announcement.getStatus()));
        data.setIsTop(announcement.getIsTop() != null && announcement.getIsTop() == 1 ? "是" : "否");
        data.setStartTime(announcement.getStartTime());
        data.setEndTime(announcement.getEndTime());
        data.setViewCount(announcement.getViewCount());
        data.setCreatedAt(announcement.getCreatedAt());
        return data;
    }

    /**
     * 将Excel数据模型转换为公告请求
     * @param data Excel数据模型
     * @return 公告请求
     */
    private AnnouncementReq convertExcelDataToReq(AnnouncementExcelData data) {
        AnnouncementReq req = new AnnouncementReq();
        req.setTitle(data.getTitle());
        req.setContent(data.getContent());
        req.setType(parseType(data.getType()));
        req.setPriority(parsePriority(data.getPriority()));
        req.setStatus(parseStatus(data.getStatus()));
        req.setIsTop(parseIsTop(data.getIsTop()));
        req.setStartTime(data.getStartTime());
        req.setEndTime(data.getEndTime());
        return req;
    }

    /**
     * 解析类型名称为类型值
     * @param typeName 类型名称
     * @return 类型值
     */
    private Integer parseType(String typeName) {
        if (typeName == null || typeName.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "类型不能为空");
        }
        switch (typeName.trim()) {
            case "系统": return 1;
            case "活动": return 2;
            case "维护": return 3;
            case "其他": return 4;
            default:
                // 尝试解析数字
                try {
                    int val = Integer.parseInt(typeName.trim());
                    if (val >= 1 && val <= 4) return val;
                } catch (NumberFormatException ignored) {}
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "类型无效: " + typeName + "，应为 系统/活动/维护/其他 或 1-4");
        }
    }

    /**
     * 解析优先级名称为优先级值
     * @param priorityName 优先级名称
     * @return 优先级值
     */
    private Integer parsePriority(String priorityName) {
        if (priorityName == null || priorityName.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "优先级不能为空");
        }
        switch (priorityName.trim()) {
            case "低": return 1;
            case "中": return 2;
            case "高": return 3;
            case "紧急": return 4;
            default:
                try {
                    int val = Integer.parseInt(priorityName.trim());
                    if (val >= 1 && val <= 4) return val;
                } catch (NumberFormatException ignored) {}
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "优先级无效: " + priorityName + "，应为 低/中/高/紧急 或 1-4");
        }
    }

    /**
     * 解析状态名称为状态值
     * @param statusName 状态名称
     * @return 状态值
     */
    private Integer parseStatus(String statusName) {
        if (statusName == null || statusName.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "状态不能为空");
        }
        switch (statusName.trim()) {
            case "草稿": return 0;
            case "发布": return 1;
            case "下线": return 2;
            default:
                try {
                    int val = Integer.parseInt(statusName.trim());
                    if (val >= 0 && val <= 2) return val;
                } catch (NumberFormatException ignored) {}
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "状态无效: " + statusName + "，应为 草稿/发布/下线 或 0-2");
        }
    }

    /**
     * 解析置顶状态
     * @param isTopStr 置顶字符串
     * @return 置顶值 (0或1)
     */
    private Integer parseIsTop(String isTopStr) {
        if (isTopStr == null || isTopStr.trim().isEmpty()) {
            return 0;
        }
        switch (isTopStr.trim()) {
            case "是": return 1;
            case "否": return 0;
            default:
                try {
                    int val = Integer.parseInt(isTopStr.trim());
                    return (val == 1) ? 1 : 0;
                } catch (NumberFormatException ignored) {}
                return 0;
        }
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
    private List<AnnouncementResp> convertAnnouncementsList(List<Announcements> announcements) {
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
     * 根据ID获取已发布公告（公开访问，只返回 status=1 的公告）
     * @param id 公告ID
     * @return 公告实体
     */
    private Announcements getValidAnnouncementById(Long id) {
        Announcements announcement = this.getById(id);
        if (announcement == null || announcement.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        if (announcement.getStatus() != null && announcement.getStatus() != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        return announcement;
    }

    /**
     * 管理员根据ID获取公告详情（不限状态，可查看草稿/下线公告）
     * @param id 公告ID
     * @return 公告详情
     */
    @Transactional(readOnly = true)
    public AnnouncementResp getAnnouncementByIdForAdmin(Long id) {
        validateAnnouncementId(id);
        Announcements announcement = this.getById(id);
        if (announcement == null || announcement.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        return convertToResl(announcement);
    }

    /**
     * 增加浏览量（原子操作，避免并发竞态）
     * @param announcement 公告实体
     */
    private void incrementViewCount(Announcements announcement) {
        announcementsMapper.incrementViewCount(announcement.getId());
    }

    /**
     * 从请求构建公告实体
     * @param req 公告请求
     * @return 公告实体
     */
    private Announcements buildAnnouncementFromReq(AnnouncementReq req) {
        Announcements announcement = new Announcements();
        if (req != null) {
            BeanUtils.copyProperties(req, announcement);
        }
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
    private QueryWrapper<Announcements> buildAnnouncementQueryWrapper(Integer status, Integer type, String keyword, Boolean includeDeleted) {
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
        if (org.springframework.util.StringUtils.hasText(keyword)) {
            String trimmedKeyword = keyword.trim();
            queryWrapper.and(wrapper -> wrapper
                    .like("title", trimmedKeyword)
                    .or()
                    .like("content", trimmedKeyword));
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
    private AnnouncementResp convertToResl(Announcements announcement) {
        AnnouncementResp resl = new AnnouncementResp();
        if (announcement != null) {
            BeanUtils.copyProperties(announcement, resl);
        }

        setAnnouncementNames(resl, announcement);
        setAnnouncementValidity(resl, announcement);

        return resl;
    }

    /**
     * 设置公告名称信息
     * @param resl 响应对象
     * @param announcement 公告实体
     */
    private void setAnnouncementNames(AnnouncementResp resl, Announcements announcement) {
        resl.setTypeName(getTypeName(announcement.getType()));
        resl.setPriorityName(getPriorityName(announcement.getPriority()));
        resl.setStatusName(getStatusName(announcement.getStatus()));
    }

    /**
     * 设置公告有效性
     * @param resl 响应对象
     * @param announcement 公告实体
     */
    private void setAnnouncementValidity(AnnouncementResp resl, Announcements announcement) {
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
