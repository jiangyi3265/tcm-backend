package com.ruoyi.hospital.service;

import java.util.List;
import java.util.Map;

/**
 * 邮件发送 Service接口
 *
 * @author ruoyi
 */
public interface ITcmEmailService
{
    /**
     * 发送邮件并记录日志
     *
     * @param to      收件人邮箱
     * @param subject 邮件主题
     * @param body    邮件正文
     * @param type    邮件类型
     * @return 是否发送成功
     */
    boolean sendAndLog(String to, String subject, String body, String type);

    boolean sendAndLog(String to, String subject, String body, String type, List<Map<String, Object>> attachments);

    /**
     * 按模板渲染变量后发送邮件并记录日志
     *
     * @param to              收件人邮箱
     * @param templateKey     模板白名单 key
     * @param variables       模板变量
     * @param fallbackSubject 模板不可用时的主题兜底
     * @param fallbackBody    模板不可用时的正文兜底
     * @param type            邮件类型
     * @return 是否发送成功
     */
    boolean sendTemplateAndLog(
            String to,
            String templateKey,
            Map<String, ?> variables,
            String fallbackSubject,
            String fallbackBody,
            String type);

    boolean sendTemplateAndLog(
            String to,
            String templateKey,
            Map<String, ?> variables,
            String fallbackSubject,
            String fallbackBody,
            String type,
            List<Map<String, Object>> attachments);
}
