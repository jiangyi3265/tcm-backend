package com.ruoyi.hospital.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmClinicSetting;
import com.ruoyi.hospital.mapper.TcmAppointmentMapper;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
import com.ruoyi.hospital.service.ITcmAppointmentService;

/**
 * 中医预约 Service业务层处理
 *
 * @author ruoyi
 */
@Service
public class TcmAppointmentServiceImpl implements ITcmAppointmentService
{
    @Autowired
    private TcmAppointmentMapper appointmentMapper;

    @Autowired
    private TcmClinicSettingMapper settingMapper;

    /** 有效的预约状态列表 */
    private static final List<String> VALID_STATUSES = Arrays.asList("booked", "confirmed", "completed", "cancelled");

    /**
     * 查询预约列表
     *
     * @param appointment 预约查询条件
     * @return 预约集合
     */
    @Override
    public List<TcmAppointment> selectTcmAppointmentList(TcmAppointment appointment)
    {
        return appointmentMapper.selectTcmAppointmentList(appointment);
    }

    /**
     * 查询预约详情
     *
     * @param id 预约ID
     * @return 预约信息
     */
    @Override
    public TcmAppointment selectTcmAppointmentById(String id)
    {
        return appointmentMapper.selectTcmAppointmentById(id);
    }

    /**
     * 新增预约
     *
     * @param appointment 预约信息
     * @return 影响行数
     */
    @Override
    public int insertTcmAppointment(TcmAppointment appointment)
    {
        if (appointment.getId() == null || appointment.getId().isEmpty())
        {
            appointment.setId(java.util.UUID.randomUUID().toString());
        }
        appointment.setCreateTime(DateUtils.getNowDate());
        return appointmentMapper.insertTcmAppointment(appointment);
    }

    /**
     * 修改预约
     *
     * @param appointment 预约信息
     * @return 影响行数
     */
    @Override
    public int updateTcmAppointment(TcmAppointment appointment)
    {
        return appointmentMapper.updateTcmAppointment(appointment);
    }

    /**
     * 更新预约状态
     *
     * @param id     预约ID
     * @param status 新状态
     * @return 更新后的预约对象
     */
    @Override
    public TcmAppointment updateStatus(String id, String status)
    {
        if (!VALID_STATUSES.contains(status))
        {
            throw new ServiceException("无效的预约状态: " + status + "，有效状态为: " + VALID_STATUSES);
        }
        TcmAppointment existing = appointmentMapper.selectTcmAppointmentById(id);
        if (existing == null)
        {
            throw new ServiceException("预约记录不存在");
        }
        existing.setStatus(status);
        appointmentMapper.updateTcmAppointment(existing);
        return existing;
    }

    /**
     * 检查时间段是否可用
     *
     * @param practitionerId 医师ID
     * @param roomId         诊室ID
     * @param startTime      开始时间
     * @param endTime        结束时间
     * @param excludeId      排除的预约ID
     * @return 检查结果
     */
    @Override
    public Map<String, Object> checkSlot(String practitionerId, String roomId, String startTime, String endTime, String excludeId)
    {
        Map<String, Object> result = new HashMap<>();
        List<String> conflicts = new ArrayList<>();
        boolean practitionerConflict = false;
        boolean roomConflict = false;

        // 查询重叠的预约
        List<TcmAppointment> overlapping = appointmentMapper.selectOverlappingAppointments(
                practitionerId, roomId, startTime, endTime, excludeId);

        for (TcmAppointment apt : overlapping)
        {
            if (apt.getPractitionerId() != null && apt.getPractitionerId().equals(practitionerId))
            {
                practitionerConflict = true;
                conflicts.add("医师时间冲突: " + apt.getStartTime() + " - " + apt.getEndTime());
            }
            if (apt.getRoomId() != null && apt.getRoomId().equals(roomId))
            {
                roomConflict = true;
                conflicts.add("诊室时间冲突: " + apt.getStartTime() + " - " + apt.getEndTime());
            }
        }

        // 检查医师间隔设置
        TcmClinicSetting intervalSetting = settingMapper.selectSettingByKey("practitionerInterval");
        if (intervalSetting != null && intervalSetting.getSettingValue() != null)
        {
            try
            {
                int intervalMinutes = Integer.parseInt(intervalSetting.getSettingValue().replaceAll("\"", ""));
                if (intervalMinutes > 0 && !overlapping.isEmpty())
                {
                    for (TcmAppointment apt : overlapping)
                    {
                        if (apt.getPractitionerId() != null && apt.getPractitionerId().equals(practitionerId))
                        {
                            practitionerConflict = true;
                            conflicts.add("医师间隔不足 " + intervalMinutes + " 分钟");
                            break;
                        }
                    }
                }
            }
            catch (NumberFormatException ignored)
            {
            }
        }

        boolean available = conflicts.isEmpty();
        result.put("available", available);
        result.put("conflicts", conflicts);
        result.put("practitionerConflict", practitionerConflict);
        result.put("roomConflict", roomConflict);
        return result;
    }

    @Override
    public TcmAppointment selectTcmAppointmentByIntakeToken(String intakeToken)
    {
        return appointmentMapper.selectTcmAppointmentByIntakeToken(intakeToken);
    }
}
