package com.example.hello_spring;

import com.example.hello_spring.common.Auth;
import com.example.hello_spring.common.FileStorage;
import com.example.hello_spring.common.Result;
import com.example.hello_spring.entity.Song;
import com.example.hello_spring.entity.User;
import com.example.hello_spring.mapper.SongMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/song")
public class SongController {

    /** 允许上传的音频扩展名 */
    private static final Set<String> AUDIO_EXTENSIONS = Set.of("mp3", "m4a", "aac", "wav", "ogg", "flac");
    /** 允许上传的封面扩展名 */
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp", "gif");

    @Autowired
    private SongMapper songMapper;

    @Value("${app.upload-dir}")
    private String uploadDir;

    /** 每个用户最多上传多少首歌 */
    @Value("${app.max-songs-per-user:10}")
    private int maxSongsPerUser;

    // ---------------- 读接口（公开，游客可访问） ----------------

    /**
     * 今日推歌。规则：
     * <ol>
     *   <li>该日期被手动指定过 → 返回指定那首；</li>
     *   <li>否则在全部歌曲中按日期轮换：同一天全站结果一致，且每天自动换一首。</li>
     * </ol>
     *
     * @param date 可选，格式 {@code 2026-10-15}，省略则为今天
     */
    @GetMapping("/today")
    public Result<Song> today(@RequestParam(required = false) String date) {
        LocalDate day;
        try {
            day = parseDate(date);
        } catch (DateTimeParseException e) {
            return Result.error("日期格式应为 2026-10-15");
        }

        Song picked = songMapper.findByRecommendDate(day);
        if (picked == null) {
            int total = songMapper.countAll();
            if (total > 0) {
                // toEpochDay 让相邻日期映射到相邻下标，形成循环轮换
                int index = Math.floorMod(day.toEpochDay(), total);
                picked = songMapper.findByOffset(index);
            }
        }
        // 曲库为空时 data 为 null，由前端显示占位提示
        return Result.success(picked);
    }

    @GetMapping("/list")
    public Result<List<Song>> list() {
        return Result.success(songMapper.findAll());
    }

    // ---------------- 写接口（需登录，见 WebConfig 拦截规则） ----------------

    @PostMapping("/upload")
    public Result<Song> upload(@RequestParam("audio") MultipartFile audio,
                               @RequestParam(value = "cover", required = false) MultipartFile cover,
                               @RequestParam("title") String title,
                               @RequestParam(value = "artist", required = false) String artist,
                               @RequestParam(value = "lyric", required = false) String lyric,
                               HttpSession session) {
        User me = Auth.current(session);
        if (me == null) {
            return Result.error("请先登录");
        }
        if (title == null || title.isBlank()) {
            return Result.error("歌名不能为空");
        }
        if (title.trim().length() > 150) {
            return Result.error("歌名最多 150 个字");
        }
        if (audio == null || audio.isEmpty()) {
            return Result.error("请选择要上传的音频文件");
        }

        int mine = songMapper.countByUser(me.getId());
        if (mine >= maxSongsPerUser) {
            return Result.error("每人最多上传 " + maxSongsPerUser + " 首歌，你已经传满了");
        }

        String audioUrl = FileStorage.save(audio, uploadDir, "audio", AUDIO_EXTENSIONS);
        if (audioUrl == null) {
            return Result.error("音频格式不支持，请上传 mp3 / m4a / aac / wav / ogg / flac");
        }

        String coverUrl = null;
        if (cover != null && !cover.isEmpty()) {
            coverUrl = FileStorage.save(cover, uploadDir, "cover", IMAGE_EXTENSIONS);
            if (coverUrl == null) {
                // 封面不合格就把刚存下的音频也删掉，别留孤儿文件
                FileStorage.delete(uploadDir, audioUrl);
                return Result.error("封面格式不支持，请上传 jpg / png / webp / gif");
            }
        }

        Song song = new Song();
        song.setTitle(title.trim());
        song.setArtist(artist == null || artist.isBlank() ? "未知歌手" : artist.trim());
        song.setLyric(lyric);
        song.setAudioUrl(audioUrl);
        song.setCoverUrl(coverUrl);
        song.setUserId(me.getId());
        songMapper.insertSong(song);

        // uploaderName 是联表字段，插入后手动补上，免得前端拿到 null
        song.setUploaderName(me.displayName());
        return Result.success(song);
    }

    /**
     * 手动指定某天推哪首（只允许管理员）。
     * 「今日推荐」是站级位置，普通用户上传的歌会参与自动轮换，不去抢这个位置。
     */
    @PostMapping("/recommend")
    public Result<String> recommend(@RequestParam Integer songId,
                                    @RequestParam(required = false) String date,
                                    HttpSession session) {
        if (!Auth.isAdmin(Auth.current(session))) {
            return Result.error("只有站长可以指定今日推荐");
        }
        LocalDate day;
        try {
            day = parseDate(date);
        } catch (DateTimeParseException e) {
            return Result.error("日期格式应为 2026-10-15");
        }
        Song song = songMapper.findById(songId);
        if (song == null) {
            return Result.error("歌曲不存在");
        }
        // 唯一索引保证一天只有一首，先把该日已有的指定清掉
        songMapper.clearRecommendDate(day);
        songMapper.setRecommendDate(songId, day);
        return Result.success("已把《" + song.getTitle() + "》设为 " + day + " 的推荐");
    }

    /** 删除歌曲（只能删自己传的；管理员可以删所有人的），音频和封面文件一并删除 */
    @PostMapping("/delete")
    public Result<String> delete(@RequestParam Integer id, HttpSession session) {
        User me = Auth.current(session);
        Song song = songMapper.findById(id);
        if (song == null) {
            return Result.error("歌曲不存在");
        }
        if (!Auth.canManage(me, song.getUserId())) {
            return Result.error("只能删除自己上传的歌曲");
        }

        songMapper.deleteById(id);

        // 数据库记录删掉后，把磁盘上的音频和封面也一起删掉，否则文件会越攒越多
        boolean audioDeleted = FileStorage.delete(uploadDir, song.getAudioUrl());
        boolean coverDeleted = song.getCoverUrl() == null
                || FileStorage.delete(uploadDir, song.getCoverUrl());

        StringBuilder msg = new StringBuilder("已删除《").append(song.getTitle()).append("》");
        if (!audioDeleted) {
            msg.append("，但音频文件删除失败（可能已被手动清理）");
        }
        if (!coverDeleted) {
            msg.append("，封面文件删除失败");
        }
        return Result.success(msg.toString());
    }

    /** 解析 {@code yyyy-MM-dd}；为空则取今天 */
    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDate.now();
        }
        return LocalDate.parse(raw.trim());
    }
}
