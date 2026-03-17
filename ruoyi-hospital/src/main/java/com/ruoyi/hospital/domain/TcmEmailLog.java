package com.ruoyi.hospital.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 邮件日志对象 tcm_email_log
 *
 * @author ruoyi
 */
public class TcmEmailLog extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 收件人邮箱 */
    @Excel(name = "收件人邮箱")
    private String toEmail;

    /** 主题 */
    @Excel(name = "主题")
    private String subject;

    /** 邮件类型 */
    @Excel(name = "邮件类型")
    private String emailType;

    /** 邮件内容 */
    private String body;

    /** 发送时间 */
    @Excel(name = "发送时间")
    private String sentAt;

    /** 扩展信息（JSON） */
    private String payload;

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getId()
    {
        return id;
    }

    public void setToEmail(String toEmail)
    {
        this.toEmail = toEmail;
    }

    public String getToEmail()
    {
        return toEmail;
    }

    public void setSubject(String subject)
    {
        this.subject = subject;
    }

    public String getSubject()
    {
        return subject;
    }

    public void setEmailType(String emailType)
    {
        this.emailType = emailType;
    }

    public String getEmailType()
    {
        return emailType;
    }

    public void setBody(String body)
    {
        this.body = body;
    }

    public String getBody()
    {
        return body;
    }

    public void setSentAt(String sentAt)
    {
        this.sentAt = sentAt;
    }

    public String getSentAt()
    {
        return sentAt;
    }

    public void setPayload(String payload)
    {
        this.payload = payload;
    }

    public String getPayload()
    {
        return payload;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("toEmail", getToEmail())
            .append("subject", getSubject())
            .append("emailType", getEmailType())
            .append("body", getBody())
            .append("sentAt", getSentAt())
            .append("payload", getPayload())
            .append("createBy", getCreateBy())
            .append("createTime", getCreateTime())
            .append("updateBy", getUpdateBy())
            .append("updateTime", getUpdateTime())
            .append("remark", getRemark())
            .toString();
    }
}
