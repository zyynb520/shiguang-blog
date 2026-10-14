package com.example.hello_spring.common;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码哈希工具：使用 JDK 自带的 PBKDF2-HMAC-SHA256 做加盐慢哈希。
 *
 * <p>存储格式：{@code pbkdf2$sha256$<迭代次数>$<盐(Base64)>$<哈希(Base64)>}
 * <p>每次调用 {@link #hash(String)} 都会生成随机盐，因此同一密码的两次结果不同。
 */
public final class PasswordUtil {

    /** 格式前缀，用于识别本工具生成的摘要。 */
    private static final String PREFIX = "pbkdf2$sha256$";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

    /** 迭代次数，可按机器性能调高（OWASP 对 SHA256 的建议值为 600000）。 */
    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private PasswordUtil() {
    }

    /**
     * 生成密码摘要，用于写入数据库。
     *
     * @param rawPassword 用户提交的明文密码
     * @return 可直接入库的摘要字符串
     */
    public static String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] key = pbkdf2(rawPassword, salt, ITERATIONS);
        return PREFIX + ITERATIONS + "$" + ENCODER.encodeToString(salt) + "$" + ENCODER.encodeToString(key);
    }

    /**
     * 校验明文密码与数据库中的摘要是否匹配。
     *
     * @param rawPassword 用户提交的明文密码
     * @param stored      数据库中保存的摘要
     * @return 匹配返回 true；参数为空、格式非法或摘要不是本工具生成的一律返回 false
     */
    public static boolean matches(String rawPassword, String stored) {
        if (rawPassword == null || stored == null || !stored.startsWith(PREFIX)) {
            return false;
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 5) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[2]);
            byte[] salt = DECODER.decode(parts[3]);
            byte[] expected = DECODER.decode(parts[4]);
            byte[] actual = pbkdf2(rawPassword, salt, iterations);
            // 使用常量时间比较，避免通过响应时间推断哈希内容
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException e) {
            // Base64 解码失败或迭代次数不是数字：视为摘要损坏，登录失败
            return false;
        }
    }

    private static byte[] pbkdf2(String rawPassword, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("密码哈希计算失败", e);
        } finally {
            spec.clearPassword();
        }
    }
}
