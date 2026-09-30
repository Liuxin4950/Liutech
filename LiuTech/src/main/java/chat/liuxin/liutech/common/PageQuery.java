package chat.liuxin.liutech.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/** 页码入口统一校验；SQL 偏移必须在 long 范围内计算。 */
public record PageQuery(long current, long size) {
    public static final int MAX_SIZE = 500;

    public static PageQuery of(Integer page, Integer size) {
        return new PageQuery(page == null ? 1 : page, size == null ? 10 : size);
    }

    public static PageQuery of(Integer page, Integer size, int maxSize) {
        PageQuery query = of(page, size);
        if (query.size > maxSize) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分页参数不正确，每页最多" + maxSize + "条");
        }
        return query;
    }

    public PageQuery {
        if (current < 1 || current > Integer.MAX_VALUE || size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "分页参数不正确，每页最多500条");
        }
    }

    public long offset() {
        return Math.multiplyExact(current - 1, size);
    }

    public <T> Page<T> toPage() {
        return new Page<>(current, size);
    }
}
