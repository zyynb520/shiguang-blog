package com.example.hello_spring.common;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 上传文件落盘工具。
 *
 * <p>两条安全约定：
 * <ol>
 *   <li>只接受白名单内的扩展名，其余一律拒绝；</li>
 *   <li>文件名用 UUID 重新生成，不使用用户提交的文件名，
 *       既避免 <code>../</code> 路径穿越，也避免同名覆盖。</li>
 * </ol>
 */
public final class FileStorage {

    private FileStorage() {
    }

    /**
     * 保存上传文件。
     *
     * @param file              上传的文件
     * @param uploadDir         上传根目录（相对路径按当前工作目录解析）
     * @param category          子目录名，如 audio / cover
     * @param allowedExtensions 允许的扩展名（小写、不含点）
     * @return 可直接放进 URL 的访问路径，如 <code>/uploads/audio/xxx.mp3</code>；
     *         扩展名不在白名单时返回 <code>null</code>
     */
    public static String save(MultipartFile file, String uploadDir, String category, Set<String> allowedExtensions) {
        String extension = extensionOf(file.getOriginalFilename());
        if (extension == null || !allowedExtensions.contains(extension)) {
            return null;
        }
        try {
            Path dir = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(category);
            Files.createDirectories(dir);
            String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
            file.transferTo(dir.resolve(filename));
            return "/uploads/" + category + "/" + filename;
        } catch (IOException e) {
            throw new IllegalStateException("文件保存失败", e);
        }
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 删除之前通过 {@link #save} 存下来的文件。
     *
     * <p>安全约束：路径必须解析后仍落在 uploads 目录内，
     * 因此形如 <code>/uploads/../../etc/passwd</code> 的输入会被拒绝。
     *
     * @param uploadDir 上传根目录
     * @param url       当初 save() 返回的访问路径，如 <code>/uploads/audio/xxx.mp3</code>
     * @return 真的删掉了返回 true；路径不合法、文件本来就不存在、或删除失败返回 false
     */
    public static boolean delete(String uploadDir, String url) {
        if (url == null || !url.startsWith("/uploads/")) {
            return false;
        }

        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        // 去掉前缀，解析成绝对路径后再规范化，然后确认没跑出 uploads 目录
        Path target = base.resolve(url.substring("/uploads/".length())).normalize();
        if (!target.startsWith(base) || target.equals(base)) {
            return false;
        }

        try {
            return Files.deleteIfExists(target);
        } catch (IOException e) {
            // 删不掉不算致命错误（比如权限问题），记录到调用方决定
            return false;
        }
    }
}
