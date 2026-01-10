package chat.liuxin.liutech.resp;

import chat.liuxin.liutech.model.Images;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ImageUploadResult {
    private Images image;
    private boolean duplicate;
}

