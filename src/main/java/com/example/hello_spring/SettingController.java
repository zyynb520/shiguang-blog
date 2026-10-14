package com.example.hello_spring;

import com.example.hello_spring.common.Auth;
import com.example.hello_spring.common.ColorExtractor;
import com.example.hello_spring.common.FileStorage;
import com.example.hello_spring.common.Result;
import com.example.hello_spring.entity.SiteSetting;
import com.example.hello_spring.mapper.SiteSettingMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 站点外观配置：背景图 + 主题配色。
 * 前端每个页面加载时都会拉一次 /setting/site；读不到就用默认值。
 */
@RestController
@RequestMapping("/setting")
public class SettingController {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    private static final String KEY_BG_IMAGE = "bgImage";
    private static final String KEY_BG_DIM = "bgDim";
    private static final String KEY_ACCENT = "accent";
    private static final String KEY_ACCENT2 = "accent2";
    private static final String KEY_ACCENT3 = "accent3";
    private static final String KEY_THEME_PRESET = "themePreset";

    /** 只允许这几个键，避免这个接口被用来往数据库塞任意内容 */
    private static final Set<String> ALLOWED_KEYS = Set.of(
            KEY_BG_IMAGE, KEY_BG_DIM, KEY_ACCENT, KEY_ACCENT2, KEY_ACCENT3, KEY_THEME_PRESET);

    /** 默认遮罩深浅（0~100）。实测 60 是折中值：背景照片看得清，文字也不吃力 */
    private static final int DEFAULT_DIM = 60;
    private static final String DEFAULT_ACCENT = "#ff7ac6";
    private static final String DEFAULT_ACCENT2 = "#8b5cf6";
    private static final String DEFAULT_ACCENT3 = "#38bdf8";
    private static final String DEFAULT_PRESET = "sakura";

    private static final Pattern HEX_COLOR = Pattern.compile("^#?[0-9a-fA-F]{6}$");

    /** 站点外观是站级设置，只有管理员能改 */
    private static final String ADMIN_ONLY = "只有站长可以修改站点设置";

    private static boolean notAdmin(HttpSession session) {
        return !Auth.isAdmin(Auth.current(session));
    }

    /** 只接受本站上传目录下的背景图，防止被用来读取服务器上的任意文件 */
    private static final Pattern BG_URL = Pattern.compile(
            "^/uploads/bg/[0-9a-f]{32}\\.(jpg|jpeg|png|gif|webp)$");

    @Autowired
    private SiteSettingMapper siteSettingMapper;

    @Value("${app.upload-dir}")
    private String uploadDir;

    // ---------------- 读接口（公开） ----------------

    @GetMapping("/site")
    public Result<Map<String, String>> site() {
        return Result.success(currentSettings());
    }

    // ---------------- 写接口（需登录，见 WebConfig） ----------------

    /** 上传背景图 */
    @PostMapping("/bg")
    public Result<Map<String, String>> uploadBg(@RequestParam("image") MultipartFile image,
                                                HttpSession session) {
        if (notAdmin(session)) {
            return Result.error(ADMIN_ONLY);
        }
        if (image == null || image.isEmpty()) {
            return Result.error("请选择要上传的图片");
        }
        String url = FileStorage.save(image, uploadDir, "bg", IMAGE_EXTENSIONS);
        if (url == null) {
            return Result.error("图片格式不支持，请上传 jpg / png / webp / gif");
        }

        // 换背景图时把旧文件删掉，否则每换一次就多留一个文件在磁盘上
        String oldUrl = siteSettingMapper.findValue(KEY_BG_IMAGE);
        if (oldUrl != null && !oldUrl.equals(url)) {
            FileStorage.delete(uploadDir, oldUrl);
        }

        siteSettingMapper.upsert(KEY_BG_IMAGE, url);
        return Result.success(currentSettings());
    }

    /** 调整背景遮罩深浅，值会被夹在 0~100 */
    @PostMapping("/dim")
    public Result<String> setDim(@RequestParam Integer value, HttpSession session) {
        if (notAdmin(session)) {
            return Result.error(ADMIN_ONLY);
        }
        int clamped = Math.max(0, Math.min(100, value));
        siteSettingMapper.upsert(KEY_BG_DIM, String.valueOf(clamped));
        return Result.success("遮罩深浅已设为 " + clamped + "%");
    }

    /** 恢复默认极光背景（只清背景图，不影响主题配色），同时删掉背景图文件 */
    @PostMapping("/clearBg")
    public Result<String> clearBg(HttpSession session) {
        if (notAdmin(session)) {
            return Result.error(ADMIN_ONLY);
        }
        String oldUrl = siteSettingMapper.findValue(KEY_BG_IMAGE);
        siteSettingMapper.delete(KEY_BG_IMAGE);

        boolean removed = oldUrl == null || FileStorage.delete(uploadDir, oldUrl);
        return Result.success(removed
                ? "已恢复默认背景，原背景图文件已删除"
                : "已恢复默认背景（原文件删除失败，可能是已被手动清理）");
    }

    /** 保存主题配色 */
    @PostMapping("/theme")
    public Result<Map<String, String>> saveTheme(@RequestParam String accent,
                                                 @RequestParam String accent2,
                                                 @RequestParam String accent3,
                                                 @RequestParam(required = false) String preset,
                                                 HttpSession session) {
        if (notAdmin(session)) {
            return Result.error(ADMIN_ONLY);
        }
        String a1 = normalizeHex(accent);
        String a2 = normalizeHex(accent2);
        String a3 = normalizeHex(accent3);
        if (a1 == null || a2 == null || a3 == null) {
            return Result.error("颜色格式应为 #rrggbb");
        }
        siteSettingMapper.upsert(KEY_ACCENT, a1);
        siteSettingMapper.upsert(KEY_ACCENT2, a2);
        siteSettingMapper.upsert(KEY_ACCENT3, a3);
        siteSettingMapper.upsert(KEY_THEME_PRESET,
                (preset == null || preset.isBlank()) ? "custom" : preset.trim());
        return Result.success(currentSettings());
    }

    /** 从当前背景图里自动提取一套配色并保存 */
    @PostMapping("/theme/extract")
    public Result<Map<String, String>> extractTheme(HttpSession session) {
        if (notAdmin(session)) {
            return Result.error(ADMIN_ONLY);
        }
        String bg = siteSettingMapper.findValue(KEY_BG_IMAGE);
        if (bg == null || !BG_URL.matcher(bg).matches()) {
            return Result.error("请先上传背景图，再从图中提取配色");
        }

        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        File file = base.resolve(bg.substring("/uploads/".length())).toFile();
        if (!file.isFile()) {
            return Result.error("背景图文件不存在，请重新上传");
        }

        String[] colors;
        try {
            colors = ColorExtractor.extract(file);
        } catch (IOException e) {
            return Result.error("读取背景图失败：" + e.getMessage());
        }

        siteSettingMapper.upsert(KEY_ACCENT, colors[0]);
        siteSettingMapper.upsert(KEY_ACCENT2, colors[1]);
        siteSettingMapper.upsert(KEY_ACCENT3, colors[2]);
        siteSettingMapper.upsert(KEY_THEME_PRESET, "fromImage");
        return Result.success(currentSettings());
    }

    /** 恢复默认配色 */
    @PostMapping("/theme/reset")
    public Result<Map<String, String>> resetTheme(HttpSession session) {
        if (notAdmin(session)) {
            return Result.error(ADMIN_ONLY);
        }
        siteSettingMapper.delete(KEY_ACCENT);
        siteSettingMapper.delete(KEY_ACCENT2);
        siteSettingMapper.delete(KEY_ACCENT3);
        siteSettingMapper.upsert(KEY_THEME_PRESET, DEFAULT_PRESET);
        return Result.success(currentSettings());
    }

    // ---------------- 内部工具 ----------------

    /** 默认值打底，再用数据库里的值覆盖 */
    private Map<String, String> currentSettings() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(KEY_BG_IMAGE, "");
        map.put(KEY_BG_DIM, String.valueOf(DEFAULT_DIM));
        map.put(KEY_ACCENT, DEFAULT_ACCENT);
        map.put(KEY_ACCENT2, DEFAULT_ACCENT2);
        map.put(KEY_ACCENT3, DEFAULT_ACCENT3);
        map.put(KEY_THEME_PRESET, DEFAULT_PRESET);

        List<SiteSetting> all = siteSettingMapper.findAll();
        for (SiteSetting s : all) {
            if (s.getSettingKey() != null
                    && ALLOWED_KEYS.contains(s.getSettingKey())
                    && s.getSettingValue() != null) {
                map.put(s.getSettingKey(), s.getSettingValue());
            }
        }
        return map;
    }

    /** 统一成 #rrggbb 小写；格式不对返回 null */
    private static String normalizeHex(String raw) {
        if (raw == null || !HEX_COLOR.matcher(raw.trim()).matches()) {
            return null;
        }
        String v = raw.trim();
        return (v.startsWith("#") ? v : "#" + v).toLowerCase();
    }
}
