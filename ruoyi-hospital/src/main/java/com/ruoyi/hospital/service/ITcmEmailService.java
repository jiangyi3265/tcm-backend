package com.ruoyi.hospital.service;

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
}
