package chat.liuxin.liutech.model;

import java.util.Date;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;

import lombok.Data;

/**
 * 公告Excel导入导出数据模型
 * 使用EasyExcel注解定义Excel列映射
 *
 * @author 刘鑫
 */
@Data
public class AnnouncementExcelData {

    @ExcelProperty("ID")
    private Long id;

    @ExcelProperty("标题")
    private String title;

    @ExcelProperty("内容")
    private String content;

    @ExcelProperty("类型")
    private String type;

    @ExcelProperty("优先级")
    private String priority;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("是否置顶")
    private String isTop;

    @ExcelProperty("开始时间")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private Date startTime;

    @ExcelProperty("结束时间")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private Date endTime;

    @ExcelProperty("浏览量")
    private Integer viewCount;

    @ExcelProperty("创建时间")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private Date createdAt;
}
