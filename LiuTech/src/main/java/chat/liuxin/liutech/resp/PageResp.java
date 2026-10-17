package chat.liuxin.liutech.resp;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;

import lombok.Data;

/**
 * 分页响应
 */
@Data
public class PageResp<T> {
    /**
     * 数据列表
     */
    private List<T> records;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 当前页码
     */
    private Long current;

    /**
     * 每页大小
     */
    private Long size;

    /**
     * 总页数
     */
    private Long pages;

    /**
     * 是否有下一页
     */
    private Boolean hasNext;

    /**
     * 是否有上一页
     */
    private Boolean hasPrevious;

    public PageResp() {}

    public PageResp(List<T> records, Long total, Long current, Long size) {
        this.records = records;
        this.total = total;
        this.current = current;
        this.size = size;
        this.pages = (total + size - 1) / size;
        this.hasNext = current < pages;
        this.hasPrevious = current > 1;
    }

    /**
     * 从 MyBatis-Plus 分页对象转换，Controller 统一经由此工厂封装分页响应，避免直接泄漏 IPage
     */
    public static <T> PageResp<T> of(IPage<T> page) {
        return new PageResp<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }
}
