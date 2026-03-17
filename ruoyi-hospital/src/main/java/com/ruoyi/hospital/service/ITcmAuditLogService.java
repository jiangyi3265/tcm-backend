package com.ruoyi.hospital.service;

/**
 * 通用审计日志 Service接口
 */
public interface ITcmAuditLogService
{
    /**
     * 写入一条审计日志
     *
     * @param targetType 目标类型
     * @param targetId   目标ID
     * @param targetName 目标名称
     * @param action     审计动作
     * @param actorId    操作人ID
     * @param details    详情
     */
    void log(String targetType, String targetId, String targetName, String action, String actorId, String details);
}
