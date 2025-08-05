package chat.liuxin.liutech.service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
 * 公告服务实现类
 * @author liuxin
 */
@Service
public class AnnouncementsService extends ServiceImpl<AnnouncementsMapper, Announcements> {

    @Autowired
    private AnnouncementsMapper announcementsMapper;

    /**
     * 分页查询有效公告
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
     * 获取置顶公告列表
     * @param limit 限制数量
     * @return 置顶公告列表
     */
    public List<AnnouncementResl> getTopAnnouncements(Integer limit) {
        if (limit == null || limit <= 0) {
            limit = 5; // 默认获取5条
        }
        List<Announcements> announcements = announcementsMapper.selectTopAnnouncements(limit);
        return announcements.stream().map(this::convertToResl).collect(Collectors.toList());
    }

    /**
     * 获取最新公告列表
     * @param limit 限制数量
     * @return 最新公告列表
     */
    public List<AnnouncementResl> getLatestAnnouncements(Integer limit) {
        if (limit == null || limit <= 0) {
            limit = 10; // 默认获取10条
        }
        List<Announcements> announcements = announcementsMapper.selectLatestAnnouncements(limit);
        return announcements.stream().map(this::convertToResl).collect(Collectors.toList());
    }

    /**
     * 根据ID获取公告详情
     * @param id 公告ID
     * @return 公告详情
     */
    public AnnouncementResl getAnnouncementById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "公告ID不能为空");
        }
        
        Announcements announcement = this.getById(id);
        if (announcement == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        
        // 增加查看次数
        announcementsMapper.incrementViewCount(id);
        
        return convertToResl(announcement);
    }

    /**
     * 创建公告
     * @param req 公告请求数据
     * @return 公告ID
     */
    @Transactional
    public Long createAnnouncement(AnnouncementReq req) {
        validateAnnouncementReq(req);
        
        Announcements announcement = new Announcements();
        BeanUtils.copyProperties(req, announcement);
        announcement.setViewCount(0);
        
        if (announcement.getIsTop() == null) {
            announcement.setIsTop(0);
        }
        
        boolean success = this.save(announcement);
        if (!success) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "创建公告失败");
        }
        
        return announcement.getId();
    }

    /**
     * 更新公告
     * @param req 公告请求数据
     * @return 是否成功
     */
    @Transactional
    public boolean updateAnnouncement(AnnouncementReq req) {
        if (req.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "公告ID不能为空");
        }
        
        validateAnnouncementReq(req);
        
        Announcements existingAnnouncement = this.getById(req.getId());
        if (existingAnnouncement == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        
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
    public boolean deleteAnnouncement(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "公告ID不能为空");
        }
        
        Announcements announcement = this.getById(id);
        if (announcement == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        
        return this.removeById(id);
    }

    /**
     * 管理员分页查询所有公告
     * @param current 当前页
     * @param size 每页大小
     * @param status 状态筛选
     * @param type 类型筛选
     * @return 公告分页数据
     */
    public IPage<AnnouncementResl> getAllAnnouncements(long current, long size, Integer status, Integer type) {
        Page<Announcements> page = new Page<>(current, size);
        QueryWrapper<Announcements> queryWrapper = new QueryWrapper<>();
        
        if (status != null) {
            queryWrapper.eq("status", status);
        }
        if (type != null) {
            queryWrapper.eq("type", type);
        }
        
        queryWrapper.orderByDesc("is_top", "priority", "created_at");
        
        IPage<Announcements> announcementPage = this.page(page, queryWrapper);
        return announcementPage.convert(this::convertToResl);
    }

    /**
     * 验证公告请求数据
     * @param req 公告请求数据
     */
    private void validateAnnouncementReq(AnnouncementReq req) {
        if (req.getType() == null || req.getType() < 1 || req.getType() > 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "公告类型无效");
        }
        
        if (req.getPriority() == null || req.getPriority() < 1 || req.getPriority() > 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "优先级无效");
        }
        
        if (req.getStatus() == null || req.getStatus() < 0 || req.getStatus() > 2) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "状态无效");
        }
        
        if (req.getStartTime() != null && req.getEndTime() != null) {
            if (req.getStartTime().after(req.getEndTime())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "开始时间不能晚于结束时间");
            }
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
        
        // 设置类型名称
        resl.setTypeName(getTypeName(announcement.getType()));
        
        // 设置优先级名称
        resl.setPriorityName(getPriorityName(announcement.getPriority()));
        
        // 设置状态名称
        resl.setStatusName(getStatusName(announcement.getStatus()));
        
        // 判断是否有效
        Date now = new Date();
        boolean isValid = announcement.getStatus() == 1 && 
                         (announcement.getStartTime() == null || !announcement.getStartTime().after(now)) &&
                         (announcement.getEndTime() == null || !announcement.getEndTime().before(now));
        resl.setIsValid(isValid);
        
        return resl;
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