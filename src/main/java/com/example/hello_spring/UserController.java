package com.example.hello_spring;

import com.example.hello_spring.common.Auth;
import com.example.hello_spring.common.FileStorage;
import com.example.hello_spring.common.PasswordUtil;
import com.example.hello_spring.common.RequestSecurity;
import com.example.hello_spring.common.Result;
import com.example.hello_spring.entity.User;
import com.example.hello_spring.mapper.ArticleMapper;
import com.example.hello_spring.mapper.SongMapper;
import com.example.hello_spring.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/user")
public class UserController {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    /** 开启强制 HTTPS 后，明文请求登录/注册时给用户的提示 */
    private static final String HTTPS_REQUIRED = "本站已开启安全登录，请把地址改成 https:// 开头后重试";

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ArticleMapper articleMapper;

    @Autowired
    private SongMapper songMapper;

    @Value("${app.upload-dir}")
    private String uploadDir;

    /** 每人文章上限 */
    @Value("${app.max-articles-per-user:10}")
    private int maxArticlesPerUser;

    /** 每人歌曲上限 */
    @Value("${app.max-songs-per-user:10}")
    private int maxSongsPerUser;

    /** 是否强制登录 / 注册走 HTTPS（服务器上开；本地开发默认关，不影响 localhost:8080） */
    @Value("${app.require-https-login:false}")
    private boolean requireHttpsLogin;

    /**
     * 是否开放登录。
     *
     * <p>备案审核期间设为 false，让整站变成纯只读：不开放注册、登录、评论，
     * 页面上也不留任何入口按钮（前端靠 /user/quota 返回的 loginEnabled 判断）。
     * 审核通过后改回 true 重启即可恢复后台管理。
     */
    @Value("${app.login-enabled:true}")
    private boolean loginEnabled;

    /**
     * 允许登录的 IP 白名单（逗号分隔；留空表示不限制）。
     *
     * <p>备案审核期间的推荐做法：{@code LOGIN_ENABLED} 保持 false（整站只读、
     * 页面上没有任何入口），同时把自己的 IP 加进这个白名单 ——
     * 这样站长本人能照常登录发文，公开访客点不到入口、也用不了登录。
     */
    @Value("${app.login-allowed-ips:}")
    private String loginAllowedIpsRaw;

    /** 这个 IP 是否在白名单里（白名单为空则恒为 false） */
    private boolean ipWhitelisted(String ip) {
        if (loginAllowedIpsRaw == null || loginAllowedIpsRaw.isBlank()) {
            return false;
        }
        for (String part : loginAllowedIpsRaw.split(",")) {
            if (part.trim().equals(ip)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 这次请求是否允许登录。
     *
     * <p>白名单内的 IP <b>始终</b>允许（不受总开关影响）；其余访客看总开关。
     * 这样「对外只读」和「自己能管站」可以同时成立。
     */
    private boolean loginAllowedFor(HttpServletRequest request) {
        if (ipWhitelisted(RequestSecurity.clientIp(request))) {
            return true;
        }
        return loginEnabled;
    }

    @PostMapping("/login")
    public Result<User> login(@RequestBody User user, HttpSession session, HttpServletRequest request) {
        // 只读模式：除白名单 IP 外一律不开放登录（备案审核期间靠这个开关）
        if (!loginAllowedFor(request)) {
            return loginEnabled
                    ? Result.error("当前网络不允许登录")
                    : Result.error("站点当前为只读模式，暂不开放登录");
        }
        // 明文 HTTP 下不允许提交密码，避免在公共 WiFi 这类链路上被嗅探
        if (requireHttpsLogin && !RequestSecurity.isSecure(request)) {
            return Result.error(HTTPS_REQUIRED);
        }
        if (isBlank(user.getUsername()) || isBlank(user.getPassword())) {
            return Result.error("用户名和密码不能为空");
        }
        User existingUser = userMapper.findByUsername(user.getUsername().trim());
        // 用户不存在与密码错误返回同一提示，避免暴露哪些用户名已注册
        if (existingUser == null || !PasswordUtil.matches(user.getPassword(), existingUser.getPassword())) {
            return Result.error("用户名或密码错误");
        }
        // 本站只允许站长登录（对外不提供任何注册渠道）。
        // 放在密码校验之后判断，避免通过提示反推出库里有哪些账号。
        if (!Auth.isAdmin(existingUser)) {
            return Result.error("本站只允许站长登录");
        }

        // 登录成功，把用户信息存进 Session（剔除密码摘要，避免随会话外泄）
        existingUser.setPassword(null);
        session.setAttribute("loginUser", existingUser);
        return Result.success(existingUser);
    }

    /** 当前登录状态：未登录时 data 为 null，前端据此决定是否展示后台入口 */
    @GetMapping("/me")
    public Result<User> me(HttpSession session) {
        // 注意：这里不能因为 loginEnabled=false 就返回 null ——
        // 白名单 IP 的站长是能正常登录的，一旦这里返回 null，前端会以为没登录，
        // 登录成功的界面又会退回登录框。控制「谁能登录」的闸门在 /user/login，
        // 会话里既然有 loginUser，就说明这个人确实通过了登录校验。
        return Result.success(Auth.current(session));
    }

    /**
     * 站点对外能力（公开）：现在只暴露「是否显示登录入口」。
     *
     * <p>站点已删除注册功能，这里是前端判断能不能显示「登录」的唯一依据 ——
     * 只对站长本人和登录白名单内的 IP 返回 true，其余访客拿到 false，
     * 页面就不会渲染任何入口。路径沿用 {@code /user/quota} 是为了不改前端与部署脚本。
     */
    @GetMapping("/quota")
    public Result<Map<String, Object>> quota(HttpServletRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("loginEnabled", loginAllowedFor(request));
        return Result.success(data);
    }

    /** 我自己的配额使用情况（需登录）：已发几篇文章、已传几首歌 */
    @GetMapping("/limits")
    public Result<Map<String, Object>> limits(HttpSession session) {
        User me = Auth.current(session);
        if (me == null) {
            return Result.error("请先登录");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("maxArticles", maxArticlesPerUser);
        data.put("myArticles", articleMapper.countByUser(me.getId()));
        data.put("maxSongs", maxSongsPerUser);
        data.put("mySongs", songMapper.countByUser(me.getId()));
        data.put("isAdmin", Auth.isAdmin(me));
        return Result.success(data);
    }

    @PostMapping("/logout")
    public Result<String> logout(HttpSession session) {
        session.invalidate();
        return Result.success("已退出登录");
    }

    /** 上传头像（需登录），同时删掉旧头像文件 */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam("avatar") MultipartFile file, HttpSession session) {
        User me = Auth.current(session);
        if (me == null) {
            return Result.error("请先登录");
        }
        if (file == null || file.isEmpty()) {
            return Result.error("请选择图片");
        }

        String url = FileStorage.save(file, uploadDir, "avatar", IMAGE_EXTENSIONS);
        if (url == null) {
            return Result.error("图片格式不支持，请上传 jpg / png / webp / gif");
        }

        String old = me.getAvatar();
        userMapper.updateAvatar(me.getId(), url);
        if (old != null && !old.equals(url)) {
            FileStorage.delete(uploadDir, old);
        }

        // 同步 Session 里的对象，前端不用重新登录
        me.setAvatar(url);
        session.setAttribute("loginUser", me);
        return Result.success(url);
    }

    /** 修改昵称（需登录） */
    @PostMapping("/nickname")
    public Result<String> updateNickname(@RequestParam String nickname, HttpSession session) {
        User me = Auth.current(session);
        if (me == null) {
            return Result.error("请先登录");
        }
        String name = nickname == null ? "" : nickname.trim();
        if (name.isEmpty() || name.length() > 20) {
            return Result.error("昵称需要 1~20 个字符");
        }
        userMapper.updateNickname(me.getId(), name);
        me.setNickname(name);
        session.setAttribute("loginUser", me);
        return Result.success("昵称已改为 " + name);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
