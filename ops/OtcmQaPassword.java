import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Uses only the deployed BCrypt library; no Spring bootstrap or credentials in argv. */
public final class OtcmQaPassword {
    public static void main(String[] args) throws Exception {
        String password = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        if (password == null || password.length() < 40 || password.length() > 100) throw new IllegalArgumentException();
        Class<?> bcrypt = Class.forName("org.springframework.security.crypto.bcrypt.BCrypt");
        String salt = (String) bcrypt.getMethod("gensalt", int.class).invoke(null, 10);
        System.out.print(bcrypt.getMethod("hashpw", String.class, String.class).invoke(null, password, salt));
    }
}
