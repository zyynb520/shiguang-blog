package com.example.hello_spring.common;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * 从图片里提取一套适合当作主题的配色。
 *
 * <p>做法：把图缩到 80×80，逐像素转成 HSB；丢掉接近黑/白/灰的低饱和像素；
 * 按色相分成 18 个桶（每桶 20°），用「饱和度 × 亮度」当权重投票，
 * 得票最高的桶的平均颜色就是主色。辅色和点缀色由主色的色相旋转得到，
 * 最后统一把饱和度和亮度拉到适合深色背景上使用的区间。
 */
public final class ColorExtractor {

    private static final int SAMPLE_SIZE = 80;
    private static final int HUE_BUCKETS = 18;

    /** 找不到可用颜色时的兜底配色（就是默认的粉紫蓝） */
    private static final String[] FALLBACK = {"#ff7ac6", "#8b5cf6", "#38bdf8"};

    private ColorExtractor() {
    }

    /**
     * @param imageFile 图片文件
     * @return 三个十六进制颜色（主色、辅色、点缀色），提取失败时返回 {@link #FALLBACK}
     * @throws IOException 读不出图片时抛出（比如 webp 这种 JDK 默认不支持的格式）
     */
    public static String[] extract(File imageFile) throws IOException {
        BufferedImage src = ImageIO.read(imageFile);
        if (src == null) {
            throw new IOException("JDK 无法解码这个图片格式（webp 不被支持，建议换成 jpg/png）");
        }

        // 缩到 80×80，够用来统计颜色，又很快
        int w = Math.min(SAMPLE_SIZE, src.getWidth());
        int h = Math.min(SAMPLE_SIZE, src.getHeight());
        BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();

        double[] bucketWeight = new double[HUE_BUCKETS];
        double[] bucketR = new double[HUE_BUCKETS];
        double[] bucketG = new double[HUE_BUCKETS];
        double[] bucketB = new double[HUE_BUCKETS];
        float[] hsb = new float[3];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = small.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int gg = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                Color.RGBtoHSB(r, gg, b, hsb);
                float hue = hsb[0];
                float sat = hsb[1];
                float bri = hsb[2];

                // 丢掉灰、近黑、近白的像素，它们对"主题色"没有参考价值
                if (sat < 0.20f || bri < 0.15f || bri > 0.97f) {
                    continue;
                }

                // 越鲜艳、越亮的像素投的票越重
                double weight = sat * bri;
                int idx = (int) (hue * HUE_BUCKETS) % HUE_BUCKETS;
                bucketWeight[idx] += weight;
                bucketR[idx] += r * weight;
                bucketG[idx] += gg * weight;
                bucketB[idx] += b * weight;
            }
        }

        int best = -1;
        for (int i = 0; i < HUE_BUCKETS; i++) {
            if (bucketWeight[i] > 0 && (best < 0 || bucketWeight[i] > bucketWeight[best])) {
                best = i;
            }
        }
        if (best < 0) {
            return FALLBACK.clone();
        }

        int baseR = (int) Math.round(bucketR[best] / bucketWeight[best]);
        int baseG = (int) Math.round(bucketG[best] / bucketWeight[best]);
        int baseB = (int) Math.round(bucketB[best] / bucketWeight[best]);

        // 用平均色的色相，但把饱和度/亮度拉到深色主题上好用的区间。
        // 三个颜色沿同一方向做小幅色相递进（+32°、+64°），这样
        // 标题的 linear-gradient(accent → accent2 → accent3) 才是顺滑的渐变；
        // 如果一正一负地旋转，会出现"蓝配绿"这种突兀的跳色。
        Color.RGBtoHSB(baseR, baseG, baseB, hsb);
        float hue = hsb[0];

        String accent = hsbToHex(hue, 0.70f, 0.98f);
        String accent2 = hsbToHex(hue + 0.089f, 0.62f, 0.94f);  // 色相 +32°
        String accent3 = hsbToHex(hue + 0.178f, 0.74f, 0.96f);  // 色相 +64°

        return new String[]{accent, accent2, accent3};
    }

    private static String hsbToHex(float hue, float sat, float bri) {
        // 色相环绕到 [0,1)
        float h = hue % 1.0f;
        if (h < 0) {
            h += 1.0f;
        }
        int rgb = Color.HSBtoRGB(h, clamp01(sat), clamp01(bri));
        return String.format("#%06x", rgb & 0xFFFFFF);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
