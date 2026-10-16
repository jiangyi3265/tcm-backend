package com.ruoyi.hospital.service.impl;

import java.util.*;
import javax.mail.AuthenticationFailedException;
import javax.mail.internet.InternetAddress;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmClinicSetting;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
import com.ruoyi.hospital.util.SmtpSecretCipher;

@Service
public class TcmSmtpSettingsService
{
    public static final String STORAGE_KEY = "smtpSettings";
    @Autowired private TcmClinicSettingMapper settingMapper;
    @Autowired private SmtpSecretCipher cipher;
    @Autowired private Environment environment;
    @Autowired(required = false) private JavaMailSender runtimeSender;

    public static boolean isPrivateSetting(String key)
    {
        return key != null && key.toLowerCase(Locale.ROOT).startsWith("smtp");
    }

    private JSONObject stored()
    {
        TcmClinicSetting record = settingMapper.selectSettingByKey(STORAGE_KEY);
        if (record == null) return null;
        try
        {
            JSONObject parsed = JSONObject.parseObject(record.getSettingValue());
            if (parsed == null) throw new IllegalArgumentException();
            return parsed;
        }
        catch (Exception e) { throw new ServiceException("SMTP_SETTINGS_INVALID"); }
    }

    private JSONObject runtime()
    {
        JSONObject result = new JSONObject();
        result.put("host", environment.getProperty("spring.mail.host", "smtp.resend.com"));
        result.put("port", environment.getProperty("spring.mail.port", Integer.class, 465));
        result.put("username", environment.getProperty("spring.mail.username", "resend"));
        result.put("fromAddress", environment.getProperty("spring.mail.from", environment.getProperty("spring.mail.username", "")));
        result.put("security", environment.getProperty("spring.mail.properties.mail.smtp.ssl.enable", Boolean.class, false) ? "ssl" : "starttls");
        return result;
    }

    public Map<String, Object> status()
    {
        JSONObject saved = stored();
        JSONObject config = saved != null ? saved : runtime();
        Map<String, Object> result = new LinkedHashMap<>();
        for (String field : Arrays.asList("host", "port", "username", "fromAddress", "security")) result.put(field, config.get(field));
        result.put("passwordConfigured", saved != null
                ? !text(saved.get("encryptedPassword")).isEmpty()
                : !environment.getProperty("spring.mail.password", "").isBlank());
        result.put("source", saved != null ? "admin" : "environment");
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> update(Map<String, Object> body)
    {
        if (body == null) throw new ServiceException("SMTP_SETTINGS_INVALID");
        JSONObject previous = stored();
        JSONObject original = previous != null ? previous : runtime();
        JSONObject next = new JSONObject();
        for (String field : Arrays.asList("host", "username", "fromAddress", "security")) next.put(field, text(body.get(field)).trim());
        try { next.put("port", Integer.parseInt(text(body.get("port")))); }
        catch (NumberFormatException e) { throw new ServiceException("SMTP_PORT_INVALID"); }
        validate(next);
        String password = text(body.get("password"));
        if (password.length() > 512 || password.contains("\r") || password.contains("\n")
                || password.contains("••••") || password.contains("****")) throw new ServiceException("SMTP_PASSWORD_INVALID");
        if (password.isEmpty())
        {
            // Never forward the previous credential to a different SMTP account or host.
            if (!text(next.get("host")).equalsIgnoreCase(text(original.get("host")))
                    || !text(next.get("username")).equals(text(original.get("username"))))
                throw new ServiceException("SMTP_NEW_PASSWORD_REQUIRED");
            if (previous != null) next.put("encryptedPassword", previous.getString("encryptedPassword"));
            else
            {
                password = environment.getProperty("spring.mail.password", "");
                if (!password.isEmpty()) next.put("encryptedPassword", cipher.encrypt(password));
            }
        }
        else next.put("encryptedPassword", cipher.encrypt(password));
        if (text(next.get("encryptedPassword")).isBlank()) throw new ServiceException("SMTP_NEW_PASSWORD_REQUIRED");
        TcmClinicSetting setting = new TcmClinicSetting();
        setting.setSettingKey(STORAGE_KEY);
        setting.setSettingValue(next.toJSONString());
        if (settingMapper.insertSetting(setting) < 1) throw new ServiceException("SMTP_SAVE_FAILED");
        return status();
    }

    private void validate(JSONObject config)
    {
        String host = config.getString("host");
        if (host.length() > 253 || !host.matches("(?i)[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?")) throw new ServiceException("SMTP_HOST_INVALID");
        int port = config.getIntValue("port");
        if (port < 1 || port > 65535) throw new ServiceException("SMTP_PORT_INVALID");
        if (!Arrays.asList("ssl", "starttls").contains(config.getString("security"))) throw new ServiceException("SMTP_TLS_REQUIRED");
        String username = config.getString("username");
        if (username.isBlank() || username.length() > 254 || username.matches("(?s).*[\\r\\n\\x00].*")) throw new ServiceException("SMTP_USERNAME_INVALID");
        try
        {
            String from = config.getString("fromAddress");
            if (from.isBlank() || from.length() > 320 || from.contains("\r") || from.contains("\n")) throw new IllegalArgumentException();
            InternetAddress[] addresses = InternetAddress.parse(from, true);
            if (addresses.length != 1 || addresses[0].isGroup()) throw new IllegalArgumentException();
            addresses[0].validate();
        }
        catch (Exception e) { throw new ServiceException("SMTP_FROM_INVALID"); }
    }

    public MailTransport transport()
    {
        JSONObject config = stored();
        if (config == null) return new MailTransport(runtimeSender, text(runtime().get("fromAddress")));
        validate(config);
        return new MailTransport(buildSender(config, cipher.decrypt(config.getString("encryptedPassword"))), config.getString("fromAddress"));
    }

    protected JavaMailSenderImpl buildSender(JSONObject config, String password)
    {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.getString("host"));
        sender.setPort(config.getIntValue("port"));
        sender.setUsername(config.getString("username"));
        sender.setPassword(password);
        sender.setDefaultEncoding("UTF-8");
        Properties properties = sender.getJavaMailProperties();
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.ssl.enable", String.valueOf("ssl".equals(config.getString("security"))));
        properties.setProperty("mail.smtp.starttls.enable", String.valueOf("starttls".equals(config.getString("security"))));
        properties.setProperty("mail.smtp.starttls.required", String.valueOf("starttls".equals(config.getString("security"))));
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        for (String name : Arrays.asList("connectiontimeout", "timeout", "writetimeout")) properties.setProperty("mail.smtp." + name, "5000");
        properties.setProperty("mail.debug", "false");
        return sender;
    }

    public Map<String, Object> testConnection()
    {
        String code = "SMTP_CONNECTION_FAILED";
        try
        {
            // Use a bounded, certificate-checked sender even for environment settings.
            JSONObject config = stored();
            String password;
            if (config == null)
            {
                config = runtime();
                password = environment.getProperty("spring.mail.password", "");
            }
            else password = cipher.decrypt(config.getString("encryptedPassword"));
            if (password.isEmpty()) throw new ServiceException("SMTP_NEW_PASSWORD_REQUIRED");
            validate(config);
            buildSender(config, password).testConnection();
            return Map.of("success", true, "code", "SMTP_CONNECTED");
        }
        catch (Exception error)
        {
            if (error instanceof ServiceException) code = error.getMessage();
            else for (Throwable cause = error; cause != null; cause = cause.getCause())
                if (cause instanceof AuthenticationFailedException || cause instanceof MailAuthenticationException) { code = "SMTP_AUTH_FAILED"; break; }
            // Provider replies can contain credentials; never return or log them.
            return Map.of("success", false, "code", code);
        }
    }

    private static String text(Object value) { return value == null ? "" : String.valueOf(value); }

    public static class MailTransport
    {
        public final JavaMailSender sender;
        public final String fromAddress;
        public MailTransport(JavaMailSender sender, String fromAddress) { this.sender = sender; this.fromAddress = fromAddress; }
    }
}
