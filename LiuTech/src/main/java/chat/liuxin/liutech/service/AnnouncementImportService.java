package chat.liuxin.liutech.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.model.AnnouncementExcelData;
import chat.liuxin.liutech.model.Announcements;
import chat.liuxin.liutech.req.AnnouncementReq;
import lombok.RequiredArgsConstructor;

/**
 * 公告 Excel 导入导出服务
 * 从 AnnouncementsService 中拆分，职责单一
 *
 * @author 刘鑫
 */
@Service
@RequiredArgsConstructor
public class AnnouncementImportService {

    private final AnnouncementsService announcementsService;

    /**
     * 导出公告数据为Excel
     */
    @Transactional(readOnly = true)
    public void exportToExcel(Integer status, Integer type, String keyword, Boolean includeDeleted, OutputStream outputStream) {
        QueryWrapper<Announcements> queryWrapper = announcementsService.buildAnnouncementQueryWrapper(status, type, keyword, includeDeleted);
        List<Announcements> announcements = announcementsService.list(queryWrapper);

        List<AnnouncementExcelData> excelDataList = announcements.stream()
                .map(this::convertToExcelData)
                .collect(Collectors.toList());

        EasyExcel.write(outputStream, AnnouncementExcelData.class)
                .sheet("公告数据")
                .doWrite(excelDataList);
    }

    /**
     * 从Excel导入公告
     */
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "announcements", allEntries = true)
    public Map<String, Object> importFromExcel(MultipartFile file) {
        List<String> errors = new ArrayList<>();
        int[] counts = {0, 0};

        try (InputStream inputStream = file.getInputStream()) {
            EasyExcel.read(inputStream, AnnouncementExcelData.class, new ReadListener<AnnouncementExcelData>() {
                @Override
                public void invoke(AnnouncementExcelData data, AnalysisContext context) {
                    try {
                        AnnouncementReq req = convertExcelDataToReq(data);
                        announcementsService.createAnnouncement(req);
                        counts[0]++;
                    } catch (Exception e) {
                        counts[1]++;
                        int rowNum = context.readRowHolder().getRowIndex() + 1;
                        errors.add("第" + rowNum + "行: " + e.getMessage());
                    }
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext context) {
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
                try {
                    int val = Integer.parseInt(typeName.trim());
                    if (val >= 1 && val <= 4) return val;
                } catch (NumberFormatException ignored) {}
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "类型无效: " + typeName + "，应为 系统/活动/维护/其他 或 1-4");
        }
    }

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
