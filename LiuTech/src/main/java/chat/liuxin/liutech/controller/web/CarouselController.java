package chat.liuxin.liutech.controller.web;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.CarouselResp;
import chat.liuxin.liutech.service.CarouselService;

/**
 * 轮播图控制器（前台用户）
 * @author 刘鑫
 */
@RestController
@RequestMapping("/carousels")
public class CarouselController {

    @Autowired
    private CarouselService carouselService;

    /**
     * 获取启用的轮播图列表（前台用户）
     * @return 轮播图列表
     */
    @GetMapping
    public Result<List<CarouselResp>> getActiveCarousels() {
        List<CarouselResp> result = carouselService.getActiveCarousels();
        return Result.success(result);
    }
}
