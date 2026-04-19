package com.ruoyi.hospital.service;

import java.util.Map;
import com.ruoyi.hospital.domain.TcmAppointment;

/**
 * 预约通知编排 Service接口
 */
public interface ITcmAppointmentNotificationService
{
    /**
     * 处理新建预约后的通知
     *
     * @param appointment 新建后的预约
     */
    void handleAppointmentCreated(TcmAppointment appointment);

    /**
     * 处理预约更新后的通知
     *
     * @param before 更新前预约
     * @param after 更新后预约
     */
    void handleAppointmentUpdated(TcmAppointment before, TcmAppointment after);

    /**
     * 处理预约状态变化后的通知
     *
     * @param before 变更前预约
     * @param after 变更后预约
     */
    void handleAppointmentStatusChanged(TcmAppointment before, TcmAppointment after);

    /**
     * 根据公开管理令牌获取预约信息
     *
     * @param token 公开管理令牌
     * @return 预约公开信息
     */
    Map<String, Object> getManageInfo(String token);

    /**
     * 通过公开管理令牌取消预约
     *
     * @param token 公开管理令牌
     * @param source 取消来源
     * @return 取消后的预约
     */
    TcmAppointment cancelByManageToken(String token, String source);

    /**
     * 通过公开问诊令牌取消预约
     *
     * @param token 问诊令牌
     * @param source 取消来源
     * @return 取消后的预约
     */
    TcmAppointment cancelByIntakeToken(String token, String source);

    /**
     * 扫描并发送到期通知
     */
    void processDueNotifications();
}
