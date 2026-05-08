package com.ruoyi.hospital.service.impl;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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

        // 尝试真实发送
        if (mailSender != null && to != null && !to.isEmpty())
        {
            try
            {
                SimpleMailMessage message = new SimpleMailMessage();
                if (fromAddress != null && !fromAddress.isEmpty())
                {
                    message.setFrom(fromAddress);
                }
                message.setTo(to);
                message.setSubject(safeSubject);
                message.setText(safeBody);
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
        if (payloadExtras != null)
        {
            payload.putAll(payloadExtras);
        }
        emailLog.setPayload(JSON.toJSONString(payload));
        emailLogMapper.insertTcmEmailLog(emailLog);

        return sent;
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
