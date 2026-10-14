import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 生成与 PasswordUtil 完全一致的 PBKDF2 密码摘要，用于在数据库里重置密码。
 *
 * 为什么需要它：旧版本把密码明文存进数据库，新版登录要求 PBKDF2 摘要，
 * 于是老账号会提示「用户名或密码错误」。用这个工具生成摘要、直接 UPDATE 数据库即可。
 *
 * 用法（不需要任何第三方依赖，只用 JDK）：
 *
 *   java deploy/PasswordHash.java 你的新密码
 *
 * 输出形如：
 *   pbkdf2$sha256$210000$XAAWWqBYujUBjBZCOyO4lw$DCk...
 *
 * 把它整串填进 UPDATE：
 *   mysql -u blog_user -p blog -e "UPDATE user SET password='粘贴这里' WHERE username='xiaoming';"
 */
public class PasswordHash {

    /** 必须与 PasswordUtil.ITERATIONS 保持一致 */
    private static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args[0].isEmpty()) {
            System.out.println("用法: java PasswordHash.java <新密码>");
            System.out.println("例如: java PasswordHash.java MyNewPass123");
            System.exit(1);
        }

        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);

        PBEKeySpec spec = new PBEKeySpec(args[0].toCharArray(), salt, ITERATIONS, KEY_BITS);
        byte[] key;
        try {
            key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }

        // withoutPadding() 是关键：必须和 PasswordUtil 的输出格式逐字节一致
        Base64.Encoder encoder = Base64.getEncoder().withoutPadding();
        System.out.println("pbkdf2$sha256$" + ITERATIONS + "$"
                + encoder.encodeToString(salt) + "$" + encoder.encodeToString(key));
    }
}
