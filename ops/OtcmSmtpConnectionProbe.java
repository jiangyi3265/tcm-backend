import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Read-only SMTP AUTH probe using the deployed service, without Spring startup.
 *
 * Compile with Java 17; runtime classpath: this class plus deployed BOOT-INF/lib
 * jars, excluding logback-*, log4j-* and SLF4J bindings. Do not include
 * BOOT-INF/classes, whose logback.xml installs file appenders.
 *
 * JSON stdin only: {"runtimeProperties": {...}, "storedSettings": null | {...}
 * | "raw setting_value JSON", "keyFile": "/absolute/existing/key/path"}.
 * No credentials belong in arguments, environment variables, or temporary files.
 * Caller should bound the process to 25 seconds and discard any JVM startup
 * diagnostics. Stdout is a single fixed-enumeration JSON result; stderr is silent.
 */
public final class OtcmSmtpConnectionProbe {
    private static final int MAX_INPUT_BYTES = 1024 * 1024;
    private static final Set<String> MAIL_PROPERTIES = Set.of(
        "spring.mail.host", "spring.mail.port", "spring.mail.username",
        "spring.mail.password", "spring.mail.from",
        "spring.mail.properties.mail.smtp.ssl.enable"
    );
    private static final Set<String> RESULT_CODES = Set.of(
        "SMTP_CONNECTED", "SMTP_CONNECTION_FAILED", "SMTP_AUTH_FAILED",
        "SMTP_SETTINGS_INVALID", "SMTP_NEW_PASSWORD_REQUIRED", "SMTP_HOST_INVALID",
        "SMTP_PORT_INVALID", "SMTP_TLS_REQUIRED", "SMTP_USERNAME_INVALID",
        "SMTP_FROM_INVALID", "SMTP_SECRET_UNAVAILABLE_REENTER_KEY"
    );

    /** Prevent JUL from installing handlers or loading external logging config. */
    public static final class SilentLoggingConfiguration {
        public SilentLoggingConfiguration() { }
    }

    public static void main(String[] args) {
        PrintStream resultOutput = System.out;
        PrintStream discard = new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8);
        System.setOut(discard);
        System.setErr(discard);
        String source = "environment";
        String code = "PROBE_INPUT_INVALID";
        boolean success = false;
        try {
            if (args.length != 0) throw new IllegalArgumentException();
            System.clearProperty("java.util.logging.config.file");
            System.setProperty("java.util.logging.config.class", SilentLoggingConfiguration.class.getName());
            LogManager.getLogManager().reset();
            Logger.getLogger("").setLevel(Level.OFF);

            // Fail before initializing any logger that might create file appenders.
            ClassLoader loader = OtcmSmtpConnectionProbe.class.getClassLoader();
            for (String resource : new String[] {
                    "ch/qos/logback/classic/LoggerContext.class",
                    "org/apache/logging/log4j/core/LoggerContext.class",
                    "org/apache/log4j/Logger.class",
                    "org/slf4j/impl/StaticLoggerBinder.class" }) {
                if (loader.getResource(resource) != null) {
                    code = "PROBE_UNSAFE_LOGGING_CLASSPATH";
                    throw new IllegalStateException();
                }
            }

            byte[] input = System.in.readNBytes(MAX_INPUT_BYTES + 1);
            if (input.length == 0 || input.length > MAX_INPUT_BYTES) throw new IllegalArgumentException();
            Class<?> jsonType = Class.forName("com.alibaba.fastjson2.JSON");
            Object parsed = jsonType.getMethod("parseObject", String.class)
                .invoke(null, new String(input, StandardCharsets.UTF_8));
            java.util.Arrays.fill(input, (byte) 0);
            if (!(parsed instanceof Map<?, ?> request)) throw new IllegalArgumentException();
            Object stored = request.get("storedSettings");
            source = stored == null ? "environment" : "admin";
            if (!(request.get("runtimeProperties") instanceof Map<?, ?> runtime)) throw new IllegalArgumentException();
            if (stored != null && !(stored instanceof String) && !(stored instanceof Map<?, ?>)) throw new IllegalArgumentException();
            Object suppliedKeyFile = request.get("keyFile");
            if (stored != null && (!(suppliedKeyFile instanceof String keyFile)
                    || keyFile.isBlank() || !Path.of(keyFile).isAbsolute())) throw new IllegalArgumentException();
            Map<String, Object> properties = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : runtime.entrySet()) {
                if (!(entry.getKey() instanceof String name) || !MAIL_PROPERTIES.contains(name)) continue;
                Object value = entry.getValue();
                if (value == null) continue;
                if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) throw new IllegalArgumentException();
                properties.put(name, value);
            }

            code = "PROBE_RUNTIME_UNAVAILABLE";
            Class<?> settingType = Class.forName("com.ruoyi.hospital.domain.TcmClinicSetting");
            Object record;
            if (stored == null) record = null;
            else {
                String value = stored instanceof String text ? text
                    : (String) jsonType.getMethod("toJSONString", Object.class).invoke(null, stored);
                record = settingType.getConstructor().newInstance();
                settingType.getMethod("setSettingKey", String.class).invoke(record, "smtpSettings");
                settingType.getMethod("setSettingValue", String.class).invoke(record, value);
            }
            AtomicBoolean rejectedMapperAccess = new AtomicBoolean();
            Class<?> mapperType = Class.forName("com.ruoyi.hospital.mapper.TcmClinicSettingMapper");
            Object mapper = Proxy.newProxyInstance(loader, new Class<?>[] { mapperType }, (proxy, method, values) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "toString" -> "ReadOnlySmtpSettingsMapper";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == values[0];
                        default -> throw new UnsupportedOperationException();
                    };
                }
                if (method.getName().equals("selectSettingByKey") && values != null
                        && values.length == 1 && "smtpSettings".equals(values[0])) return record;
                rejectedMapperAccess.set(true);
                throw new UnsupportedOperationException();
            });

            Class<?> envType = Class.forName("org.springframework.core.env.StandardEnvironment");
            Object environment = envType.getConstructor().newInstance();
            Object propertySources = envType.getMethod("getPropertySources").invoke(environment);
            propertySources.getClass().getMethod("remove", String.class).invoke(propertySources, "systemProperties");
            propertySources.getClass().getMethod("remove", String.class).invoke(propertySources, "systemEnvironment");
            Class<?> propertySourceType = Class.forName("org.springframework.core.env.PropertySource");
            Object sourceObject = Class.forName("org.springframework.core.env.MapPropertySource")
                .getConstructor(String.class, Map.class).newInstance("suppliedRuntimeProperties", properties);
            propertySources.getClass().getMethod("addFirst", propertySourceType).invoke(propertySources, sourceObject);

            Class<?> cipherType = Class.forName("com.ruoyi.hospital.util.SmtpSecretCipher");
            Object cipher = cipherType.getConstructor().newInstance();
            setField(cipher, "keyFile", suppliedKeyFile instanceof String text ? text : "");
            Class<?> serviceType = Class.forName("com.ruoyi.hospital.service.impl.TcmSmtpSettingsService");
            Object service = serviceType.getConstructor().newInstance();
            setField(service, "settingMapper", mapper);
            setField(service, "cipher", cipher);
            setField(service, "environment", environment);
            // Deliberately do not call update(), encrypt(), transport(), or any send method.
            Object outcome = serviceType.getMethod("testConnection").invoke(service);
            if (rejectedMapperAccess.get()) code = "PROBE_MAPPER_ACCESS_REJECTED";
            else if (outcome instanceof Map<?, ?> result && result.get("code") instanceof String resultCode
                    && RESULT_CODES.contains(resultCode)) {
                success = Boolean.TRUE.equals(result.get("success")) && resultCode.equals("SMTP_CONNECTED");
                code = success || !resultCode.equals("SMTP_CONNECTED") ? resultCode : "PROBE_RESULT_INVALID";
            } else code = "PROBE_RESULT_INVALID";
        } catch (Throwable ignored) {
            // Never print exception messages, provider replies, configuration, or stack traces.
            success = false;
        }
        resultOutput.println("{\"success\":" + success + ",\"code\":\"" + code
            + "\",\"source\":\"" + source + "\"}");
        resultOutput.flush();
        // Keep stdout/stderr redirected during library shutdown as well.
    }

    private static void setField(Object instance, String name, Object value) throws ReflectiveOperationException {
        Field field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }
}
