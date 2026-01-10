package chat.liuxin.liutech.resp;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ImageUsageReconcileResp {
    private int resetRows;
    private int referencedPaths;
    private int updatedImages;
    private int missingImages;
}

