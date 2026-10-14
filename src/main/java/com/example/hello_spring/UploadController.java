package com.example.hello_spring;

import com.example.hello_spring.common.Auth;
import com.example.hello_spring.common.FileStorage;
import com.example.hello_spring.common.Result;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 文章正文里的插图上传。
 *
 * <p>返回两个字段：{@code url} 是图片地址，{@code markdown} 是可直接粘进正文的
 * {@code ![](url)}，前端点一下按钮插到光标处即可。
 */
@RestController
@RequestMapping("/upload")
public class UploadController {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    @Value("${app.upload-dir}")
    private String uploadDir;

    @PostMapping("/image")
    public Result<Map<String, String>> image(@RequestParam("image") MultipartFile image,
                                             HttpSession session) {
        if (Auth.current(session) == null) {
            return Result.error("请先登录");
        }
        if (image == null || image.isEmpty()) {
            return Result.error("请选择图片");
        }

        String url = FileStorage.save(image, uploadDir, "image", IMAGE_EXTENSIONS);
        if (url == null) {
            return Result.error("图片格式不支持，请上传 jpg / png / webp / gif");
        }

        Map<String, String> data = new LinkedHashMap<>();
        data.put("url", url);
        data.put("markdown", "![](" + url + ")");
        return Result.success(data);
    }
}
