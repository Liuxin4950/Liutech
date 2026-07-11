package chat.liuxin.liutech.controller.admin;

import java.util.List;

import jakarta.validation.Valid;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;

import chat.liuxin.liutech.aspect.OperationLog;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Carousel;
import chat.liuxin.liutech.req.CarouselReq;
import chat.liuxin.liutech.resp.CarouselResp;
import chat.liuxin.liutech.service.CarouselService;

/**
 * 管理端轮播图控制器（类级 @PreAuthorize 保证认证，异常由 GlobalExceptionHandler 统一兜底）
 */
@Slf4j
@RestController
@RequestMapping("/admin/carousels")
@PreAuthorize("hasRole('ADMIN')")
public class CarouselAdminController extends BaseAdminController {

    @Autowired
    private CarouselService carouselService;

    /** 分页查询轮播图列表 */
    @GetMapping
    public Result<IPage<CarouselResp>> getCarouselList(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "false") Boolean includeDeleted) {
        return Result.success(carouselService.getAllCarousels(current, size, status, includeDeleted));
    }

    /** 根据ID获取轮播图详情 */
    @GetMapping("/{id}")
    public Result<CarouselResp> getCarouselById(@PathVariable Long id) {
        return Result.success(carouselService.getCarouselById(id));
    }

    /** 创建轮播图 */
    @OperationLog(action = "create", targetType = "carousel", description = "创建轮播图")
    @PostMapping
    public Result<Long> createCarousel(@Valid @RequestBody CarouselReq req) {
        Carousel carousel = new Carousel();
        org.springframework.beans.BeanUtils.copyProperties(req, carousel);
        return Result.success(carouselService.createCarousel(carousel));
    }

    /** 更新轮播图 */
    @OperationLog(action = "update", targetType = "carousel", description = "更新轮播图", targetName = "#id")
    @PutMapping("/{id}")
    public Result<Boolean> updateCarousel(@PathVariable Long id, @Valid @RequestBody CarouselReq req) {
        req.setId(id);
        Carousel carousel = new Carousel();
        org.springframework.beans.BeanUtils.copyProperties(req, carousel);
        return Result.success(carouselService.updateCarousel(carousel));
    }

    /** 删除轮播图（软删除） */
    @OperationLog(action = "delete", targetType = "carousel", description = "删除轮播图", targetName = "#id")
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteCarousel(@PathVariable Long id) {
        return Result.success(carouselService.deleteCarousel(id));
    }

    /** 批量删除轮播图 */
    @OperationLog(action = "delete", targetType = "carousel", description = "批量删除轮播图", targetName = "#ids")
    @PostMapping("/batch")
    public Result<Boolean> batchDeleteCarousels(@RequestBody List<Long> ids) {
        return Result.success(carouselService.batchDeleteCarousels(ids));
    }

    /** 更新轮播图状态 */
    @OperationLog(action = "update", targetType = "carousel", description = "更新轮播图状态", targetName = "#id")
    @PutMapping("/{id}/status")
    public Result<Boolean> updateCarouselStatus(@PathVariable Long id, @RequestBody StatusUpdateRequest request) {
        return Result.success(carouselService.updateCarouselStatus(id, request.getStatus()));
    }

    /** 批量更新轮播图状态 */
    @OperationLog(action = "update", targetType = "carousel", description = "批量更新轮播图状态", targetName = "#ids")
    @PutMapping("/batch/status")
    public Result<Boolean> batchUpdateCarouselStatus(@RequestBody BatchStatusUpdateRequest request) {
        return Result.success(carouselService.batchUpdateCarouselStatus(request.getIds(), request.getStatus()));
    }

    /** 更新轮播图排序 */
    @OperationLog(action = "update", targetType = "carousel", description = "更新轮播图排序")
    @PutMapping("/{id}/sort")
    public Result<Boolean> updateCarouselSort(@PathVariable Long id, @RequestBody SortUpdateRequest request) {
        return Result.success(carouselService.updateCarouselSort(id, request.getSortOrder()));
    }

    /** 恢复已删除的轮播图 */
    @OperationLog(action = "restore", targetType = "carousel", description = "恢复轮播图", targetName = "#id")
    @PutMapping("/{id}/restore")
    public Result<Boolean> restoreCarousel(@PathVariable Long id) {
        return Result.success(carouselService.restoreCarousel(id));
    }

    /** 彻底删除轮播图（物理删除） */
    @OperationLog(action = "delete", targetType = "carousel", description = "彻底删除轮播图", targetName = "#id")
    @DeleteMapping("/{id}/permanent")
    public Result<Boolean> permanentDeleteCarousel(@PathVariable Long id) {
        return Result.success(carouselService.permanentDeleteCarousel(id));
    }

    /** 批量彻底删除轮播图（物理删除） */
    @OperationLog(action = "delete", targetType = "carousel", description = "批量彻底删除轮播图", targetName = "#ids")
    @PostMapping("/batch/permanent")
    public Result<Boolean> batchPermanentDeleteCarousels(@RequestBody List<Long> ids) {
        return Result.success(carouselService.batchPermanentDeleteCarousels(ids));
    }

    /** 状态更新请求 */
    public static class StatusUpdateRequest {
        private Integer status;
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
    }

    /** 排序更新请求 */
    public static class SortUpdateRequest {
        private Integer sortOrder;
        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }

    /** 批量状态更新请求 */
    public static class BatchStatusUpdateRequest {
        private List<Long> ids;
        private Integer status;
        public List<Long> getIds() { return ids; }
        public void setIds(List<Long> ids) { this.ids = ids; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
    }
}
