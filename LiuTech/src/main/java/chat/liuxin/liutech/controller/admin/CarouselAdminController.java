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
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.model.Carousel;
import chat.liuxin.liutech.req.CarouselReq;
import chat.liuxin.liutech.resp.CarouselResp;
import chat.liuxin.liutech.service.CarouselService;

/**
 * 管理端轮播图控制器
 * 需要管理员权限才能访问
 */
@Slf4j
@RestController
@RequestMapping("/admin/carousels")
@PreAuthorize("hasRole('ADMIN')")
public class CarouselAdminController extends BaseAdminController {

    @Autowired
    private CarouselService carouselService;

    /**
     * 分页查询轮播图列表
     * @param current 当前页
     * @param size 每页大小
     * @param status 状态筛选
     * @param includeDeleted 是否包含已删除的轮播图
     * @return 轮播图分页数据
     */
    @GetMapping
    public Result<IPage<CarouselResp>> getCarouselList(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "false") Boolean includeDeleted) {
        try {
            IPage<CarouselResp> result = carouselService.getAllCarousels(current, size, status, includeDeleted);
            return Result.success(result);
        } catch (Exception e) {
            return handleException(e, "查询轮播图列表");
        }
    }

    /**
     * 根据ID获取轮播图详情
     * @param id 轮播图ID
     * @return 轮播图详情
     */
    @GetMapping("/{id}")
    public Result<CarouselResp> getCarouselById(@PathVariable Long id) {
        try {
            CarouselResp result = carouselService.getCarouselById(id);
            return Result.success(result);
        } catch (Exception e) {
            return handleException(e, "获取轮播图详情");
        }
    }

    /**
     * 创建轮播图
     * @param req 轮播图请求数据
     * @return 轮播图ID
     */
    @OperationLog(action = "create", targetType = "carousel", description = "创建轮播图")
    @PostMapping
    public Result<Long> createCarousel(@Valid @RequestBody CarouselReq req) {
        try {
            Carousel carousel = new Carousel();
            org.springframework.beans.BeanUtils.copyProperties(req, carousel);
            Long id = carouselService.createCarousel(carousel);
            return Result.success(id);
        } catch (Exception e) {
            return handleException(e, "创建轮播图");
        }
    }

    /**
     * 更新轮播图
     * @param id 轮播图ID
     * @param req 轮播图请求数据
     * @return 是否成功
     */
    @OperationLog(action = "update", targetType = "carousel", description = "更新轮播图", targetName = "#id")
    @PutMapping("/{id}")
    public Result<Boolean> updateCarousel(
            @PathVariable Long id,
            @Valid @RequestBody CarouselReq req) {
        try {
            req.setId(id);
            Carousel carousel = new Carousel();
            org.springframework.beans.BeanUtils.copyProperties(req, carousel);
            boolean success = carouselService.updateCarousel(carousel);
            return Result.success(success);
        } catch (Exception e) {
            return handleException(e, "更新轮播图");
        }
    }

    /**
     * 删除轮播图（软删除）
     * @param id 轮播图ID
     * @return 是否成功
     */
    @OperationLog(action = "delete", targetType = "carousel", description = "删除轮播图", targetName = "#id")
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteCarousel(@PathVariable Long id) {
        try {
            boolean success = carouselService.deleteCarousel(id);
            return Result.success(success);
        } catch (Exception e) {
            return handleException(e, "删除轮播图");
        }
    }

    /**
     * 批量删除轮播图
     * @param ids 轮播图ID列表
     * @return 是否成功
     */
    @OperationLog(action = "delete", targetType = "carousel", description = "批量删除轮播图", targetName = "#ids")
    @DeleteMapping("/batch")
    public Result<Boolean> batchDeleteCarousels(@RequestBody List<Long> ids) {
        try {
            boolean success = carouselService.batchDeleteCarousels(ids);
            return Result.success(success);
        } catch (Exception e) {
            return handleException(e, "批量删除轮播图");
        }
    }

    /**
     * 更新轮播图状态
     * @param id 轮播图ID
     * @param request 状态更新请求
     * @return 是否成功
     */
    @OperationLog(action = "update", targetType = "carousel", description = "更新轮播图状态", targetName = "#id")
    @PutMapping("/{id}/status")
    public Result<Boolean> updateCarouselStatus(
            @PathVariable Long id,
            @RequestBody StatusUpdateRequest request) {
        try {
            boolean success = carouselService.updateCarouselStatus(id, request.getStatus());
            return Result.success(success);
        } catch (Exception e) {
            return handleException(e, "更新轮播图状态");
        }
    }

    /**
     * 更新轮播图排序
     * @param id 轮播图ID
     * @param request 排序更新请求
     * @return 是否成功
     */
    @OperationLog(action = "update", targetType = "carousel", description = "更新轮播图排序")
    @PutMapping("/{id}/sort")
    public Result<Boolean> updateCarouselSort(
            @PathVariable Long id,
            @RequestBody SortUpdateRequest request) {
        try {
            boolean success = carouselService.updateCarouselSort(id, request.getSortOrder());
            return Result.success(success);
        } catch (Exception e) {
            return handleException(e, "更新轮播图排序");
        }
    }

    /**
     * 恢复已删除的轮播图
     * @param id 轮播图ID
     * @return 是否成功
     */
    @OperationLog(action = "restore", targetType = "carousel", description = "恢复轮播图", targetName = "#id")
    @PutMapping("/{id}/restore")
    public Result<Boolean> restoreCarousel(@PathVariable Long id) {
        try {
            boolean success = carouselService.restoreCarousel(id);
            return Result.success(success);
        } catch (Exception e) {
            return handleException(e, "恢复轮播图");
        }
    }

    /**
     * 彻底删除轮播图（物理删除）
     * @param id 轮播图ID
     * @return 是否成功
     */
    @OperationLog(action = "delete", targetType = "carousel", description = "彻底删除轮播图", targetName = "#id")
    @DeleteMapping("/{id}/permanent")
    public Result<Boolean> permanentDeleteCarousel(@PathVariable Long id) {
        try {
            boolean success = carouselService.permanentDeleteCarousel(id);
            return Result.success(success);
        } catch (Exception e) {
            return handleException(e, "彻底删除轮播图");
        }
    }

    /**
     * 批量彻底删除轮播图（物理删除）
     * @param ids 轮播图ID列表
     * @return 是否成功
     */
    @OperationLog(action = "delete", targetType = "carousel", description = "批量彻底删除轮播图", targetName = "#ids")
    @DeleteMapping("/batch/permanent")
    public Result<Boolean> batchPermanentDeleteCarousels(@RequestBody List<Long> ids) {
        try {
            boolean success = carouselService.batchPermanentDeleteCarousels(ids);
            return Result.success(success);
        } catch (Exception e) {
            return handleException(e, "批量彻底删除轮播图");
        }
    }

    /**
     * 状态更新请求
     */
    public static class StatusUpdateRequest {
        private Integer status;

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }
    }

    /**
     * 排序更新请求
     */
    public static class SortUpdateRequest {
        private Integer sortOrder;

        public Integer getSortOrder() {
            return sortOrder;
        }

        public void setSortOrder(Integer sortOrder) {
            this.sortOrder = sortOrder;
        }
    }
}
