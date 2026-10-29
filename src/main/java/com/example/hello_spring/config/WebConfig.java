package com.example.hello_spring.config;

import com.example.hello_spring.interceptor.LoginInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;
import java.time.Duration;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload-dir}")
    private String uploadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 只拦截"写"接口；文章列表/详情、曲库、今日推歌、站点配置都对游客公开
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns("/article/publish")
                .addPathPatterns("/article/update")
                .addPathPatterns("/article/delete")
                .addPathPatterns("/song/upload")
                .addPathPatterns("/song/recommend")
                .addPathPatterns("/song/delete")
                .addPathPatterns("/upload/image")
                .addPathPatterns("/user/avatar")
                .addPathPatterns("/user/nickname")
                .addPathPatterns("/setting/bg")
                .addPathPatterns("/setting/dim")
                .addPathPatterns("/setting/clearBg")
                .addPathPatterns("/setting/logo")
                .addPathPatterns("/setting/clearLogo")
                .addPathPatterns("/setting/theme")
                .addPathPatterns("/setting/theme/extract")
                .addPathPatterns("/setting/theme/reset");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 把上传目录映射为静态资源：/uploads/audio/xxx.mp3、/uploads/cover/xxx.jpg
        String location = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        if (!location.endsWith("/")) {
            location += "/"; // 目录不存在时 toUri() 不会补斜杠，缺了 Spring 会告警
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location)
                // 上传文件用的是内容哈希文件名（如 6c53bc86….png），URL 一旦变化就意味着
                // 换了新文件，所以可以放心让浏览器长期缓存，省掉音频的重复请求。
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic());
    }
}
