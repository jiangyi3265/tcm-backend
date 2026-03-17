package com.ruoyi.hospital.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.hospital.domain.TcmAppointment;

/**
 * 中医预约 Service接口
 *
 * @author ruoyi
 */
public interface ITcmAppointmentService
{
    /**
     * 查询预约列表
     *
     * @param appointment 预约查询条件
     * @return 预约集合
     */
    List<TcmAppointment> selectTcmAppointmentList(TcmAppointment appointment);

    /**
     * 查询预约详情
     *
     * @param id 预约ID
     * @return 预约信息
     */
    TcmAppointment selectTcmAppointmentById(String id);

    /**
     * 新增预约
     *
     * @param appointment 预约信息
     * @return 影响行数
     */
    int insertTcmAppointment(TcmAppointment appointment);

    /**
     * 修改预约
     *
     * @param appointment 预约信息
     * @return 影响行数
     */
    int updateTcmAppointment(TcmAppointment appointment);

    /**
     * 更新预约状态
     *
     * @param id     预约ID
     * @param status 新状态
     * @return 更新后的预约对象
     */
    TcmAppointment updateStatus(String id, String status);

    /**
     * 检查时间段是否可用
     *
     * @param practitionerId 医师ID
     * @param roomId         诊室ID
     * @param startTime      开始时间
     * @param endTime        结束时间
     * @param excludeId      排除的预约ID（用于编辑时排除自身）
     * @return 检查结果
     */
    Map<String, Object> checkSlot(String practitionerId, String roomId, String startTime, String endTime, String excludeId);

    /**
     * 根据问诊表单令牌查询预约
     *
     * @param intakeToken 令牌
     * @return 预约信息
     */
    TcmAppointment selectTcmAppointmentByIntakeToken(String intakeToken);
}
