package chat.liuxin.liutech.service;

import chat.liuxin.liutech.common.BusinessException;
import chat.liuxin.liutech.common.ErrorCode;
import chat.liuxin.liutech.mapper.PostAttachmentsMapper;
import chat.liuxin.liutech.mapper.ResourcesMapper;
import chat.liuxin.liutech.model.PostAttachments;
import chat.liuxin.liutech.model.Resources;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 上传完成后的短数据库事务；普通上传与分片合并共用，不包含文件 IO。 */
@Service
@RequiredArgsConstructor
public class ResourceRecordService {
    private final ResourcesMapper resourcesMapper;
    private final PostAttachmentsMapper attachmentsMapper;

    @Transactional(rollbackFor = Exception.class)
    public Long save(Resources resource, String draftKey, String type) {
        if (resourcesMapper.insert(resource) != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "资源记录保存失败");
        }
        if (!StringUtils.hasText(draftKey)) return null;
        PostAttachments attachment = new PostAttachments();
        attachment.setDraftKey(draftKey);
        attachment.setResourceId(resource.getId());
        attachment.setType(type != null ? type : "resource");
        if (attachmentsMapper.insert(attachment) != 1) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "附件关联保存失败");
        }
        return attachment.getId();
    }
}
