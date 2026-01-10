package chat.liuxin.liutech.controller.admin;

import chat.liuxin.liutech.common.Result;
import chat.liuxin.liutech.resp.ImageUsageReconcileResp;
import chat.liuxin.liutech.service.ImageUsageReconcileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/images")
@CrossOrigin(origins = "http://localhost:3000")
@PreAuthorize("hasRole('ADMIN')")
public class ImagesAdminController extends BaseAdminController {

    @Autowired
    private ImageUsageReconcileService imageUsageReconcileService;

    @PostMapping("/reconcile-usage")
    public Result<ImageUsageReconcileResp> reconcileUsageCount() {
        try {
            return Result.success(imageUsageReconcileService.reconcileUsageCount());
        } catch (Exception e) {
            return handleException(e, "图片引用对账");
        }
    }
}

