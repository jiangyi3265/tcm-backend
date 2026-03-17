package com.ruoyi.hospital.service.impl;

import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import com.ruoyi.hospital.domain.TcmEmailLog;
import com.ruoyi.hospital.mapper.TcmEmailLogMapper;
import com.ruoyi.hospital.service.ITcmEmailService;

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

    @Autowired
    private TcmEmailLogMapper emailLogMapper;

    @Override
    public boolean sendAndLog(String to, String subject, String body, String type)
    {
        boolean sent = false;
        String sentAt = null;

        // 尝试真实发送
        if (mailSender != null && to != null && !to.isEmpty())
        {
            try
            {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(to);
                message.setSubject(subject);
                message.setText(body);
                mailSender.send(message);
                sent = true;
                sentAt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                log.info("邮件发送成功: to={}, subject={}", to, subject);
            }
            catch (Exception e)
            {
                log.warn("邮件发送失败: to={}, error={}", to, e.getMessage());
            }
        }
        else
        {
            log.warn("邮件未发送，SMTP未配置: to={}, subject={}", to, subject);
        }

        // 记录日志
        TcmEmailLog emailLog = new TcmEmailLog();
        emailLog.setToEmail(to);
        emailLog.setSubject(subject);
        emailLog.setEmailType(type);
        emailLog.setBody(body);
        emailLog.setSentAt(sentAt);
        emailLog.setPayload("{\"sent\":" + sent + "}");
        emailLogMapper.insertTcmEmailLog(emailLog);

        return sent;
    }
}
