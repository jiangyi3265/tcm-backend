package com.ruoyi.hospital.service.impl;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.hospital.domain.TcmClinicSetting;
import com.ruoyi.hospital.domain.TcmEmailLog;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
import com.ruoyi.hospital.mapper.TcmEmailLogMapper;
import com.ruoyi.hospital.service.ITcmEmailService;
import com.ruoyi.hospital.util.EmailTemplateRegistry;

/**
 * 邮件发送 Service实现
 *
 * @author ruoyi
 */
@Service
public class TcmEmailServiceImpl implements ITcmEmailService
{
    private static final Logger log = LoggerFactory.getLogger(TcmEmailServiceImpl.class);
    private static final Pattern WEB_URL_PATTERN = Pattern.compile("(https?://[^\\s<]+)");

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.from:${spring.mail.username:}}")
    private String fromAddress;

    @Autowired
    private TcmEmailLogMapper emailLogMapper;

    @Autowired
    private TcmClinicSettingMapper clinicSettingMapper;

    @Override
    public boolean sendAndLog(String to, String subject, String body, String type)
    {
        return sendAndLogInternal(to, subject, body, type, null);
    }

    @Override
    public boolean sendTemplateAndLog(
            String to,
            String templateKey,
            Map<String, ?> variables,
            String fallbackSubject,
            String fallbackBody,
            String type)
    {
        EmailTemplateRegistry.RenderedEmail rendered = EmailTemplateRegistry.render(
                getSettingValue("emailTemplates"),
                templateKey,
                variables,
                fallbackSubject,
                fallbackBody);
        Map<String, Object> payloadExtras = new LinkedHashMap<String, Object>();
        payloadExtras.put("templateKey", rendered.getTemplateKey());
        payloadExtras.put("variables", variables != null ? variables : new LinkedHashMap<String, Object>());
        return sendAndLogInternal(to, rendered.getSubject(), rendered.getBody(), type, payloadExtras);
    }

    private boolean sendAndLogInternal(String to, String subject, String body, String type, Map<String, Object> payloadExtras)
    {
        boolean sent = false;
        String sentAt = null;
        String safeSubject = subject != null ? subject : "";
        String safeBody = body != null ? body : "";
        String htmlBody = buildHtmlEmail(safeBody);

        // 尝试真实发送
        if (mailSender != null && to != null && !to.isEmpty())
        {
            try
            {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
                if (fromAddress != null && !fromAddress.isEmpty())
                {
                    helper.setFrom(fromAddress);
                }
                helper.setTo(to);
                helper.setSubject(safeSubject);
                helper.setText(toPlainText(safeBody), htmlBody);
                mailSender.send(message);
                sent = true;
                sentAt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                log.info("邮件发送成功: to={}, subject={}", to, safeSubject);
            }
            catch (Exception e)
            {
                log.warn("邮件发送失败: to={}, error={}", to, e.getMessage());
            }
        }
        else
        {
            log.warn("邮件未发送，SMTP未配置: to={}, subject={}", to, safeSubject);
        }

        // 记录日志
        TcmEmailLog emailLog = new TcmEmailLog();
        emailLog.setToEmail(to);
        emailLog.setSubject(safeSubject);
        emailLog.setEmailType(type);
        emailLog.setBody(safeBody);
        emailLog.setSentAt(sentAt);
        JSONObject payload = new JSONObject();
        payload.put("sent", sent);
        payload.put("html", true);
        if (payloadExtras != null)
        {
            payload.putAll(payloadExtras);
        }
        emailLog.setPayload(JSON.toJSONString(payload));
        emailLogMapper.insertTcmEmailLog(emailLog);

        return sent;
    }

    private String buildHtmlEmail(String body)
    {
        String[] blocks = StringUtils.defaultString(body).split("\\n\\s*\\n");
        StringBuilder content = new StringBuilder();
        for (String block : blocks)
        {
            String trimmed = block.trim();
            if (trimmed.isEmpty())
            {
                continue;
            }
            content.append("<p>")
                    .append(linkify(trimmed).replace("\n", "<br>"))
                    .append("</p>");
        }
        if (content.length() == 0)
        {
            content.append("<p></p>");
        }
        return "<!doctype html><html><head><meta charset=\"UTF-8\"></head>"
                + "<body style=\"margin:0;background:#f5f7f6;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Arial,sans-serif;color:#2f3437;\">"
                + "<div style=\"max-width:680px;margin:0 auto;padding:28px 16px;\">"
                + "<div style=\"background:#fff;border:1px solid #e5e7eb;border-radius:10px;overflow:hidden;box-shadow:0 8px 24px rgba(31,41,55,.06);\">"
                + "<div style=\"padding:18px 24px;background:#2d6a4f;color:#fff;font-size:18px;font-weight:700;letter-spacing:0;\">TCM Clinic</div>"
                + "<div style=\"padding:24px;font-size:15px;line-height:1.7;\">"
                + content
                + "</div></div></div></body></html>";
    }

    private String linkify(String text)
    {
        Matcher matcher = WEB_URL_PATTERN.matcher(text);
        StringBuilder buffer = new StringBuilder();
        int last = 0;
        while (matcher.find())
        {
            buffer.append(escapeHtml(text.substring(last, matcher.start())));
            String url = matcher.group(1);
            String escapedUrl = escapeHtml(url);
            buffer.append("<a href=\"")
                    .append(escapedUrl)
                    .append("\" style=\"color:#2d6a4f;text-decoration:underline;\">")
                    .append(escapedUrl)
                    .append("</a>");
            last = matcher.end();
        }
        buffer.append(escapeHtml(text.substring(last)));
        return buffer.toString();
    }

    private String escapeHtml(String value)
    {
        return StringUtils.defaultString(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String toPlainText(String body)
    {
        return StringUtils.defaultString(body);
    }

    private String getSettingValue(String key)
    {
        try
        {
            TcmClinicSetting setting = clinicSettingMapper.selectSettingByKey(key);
            return setting != null ? setting.getSettingValue() : "";
        }
        catch (Exception e)
        {
            return "";
        }
    }
}
