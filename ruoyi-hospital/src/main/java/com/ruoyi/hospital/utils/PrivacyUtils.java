package com.ruoyi.hospital.utils;

import java.text.SimpleDateFormat;
import java.util.*;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmPatient;

/**
 * 隐私保护工具类
 *
 * 规则：非主治医师在诊疗结束1周后无法查看该病人档案
 * - Admin 始终有完全访问权限
 * - 主治医师(patient.practitionerId)始终有访问权限
 * - 其他医师仅在诊疗完成后1周内有访问权限
 */
public class PrivacyUtils
{
    private static final long ONE_WEEK_MS = 7L * 24 * 60 * 60 * 1000;

    /**
     * 判断当前用户是否为管理员
     */
    public static boolean isAdmin()
    {
        try
        {
            LoginUser loginUser = SecurityUtils.getLoginUser();
            if (loginUser == null || loginUser.getUser() == null) return false;
            List<SysRole> roles = loginUser.getUser().getRoles();
            if (roles == null) return false;
            for (SysRole role : roles)
            {
                if ("admin".equals(role.getRoleKey())) return true;
            }
            return false;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /**
     * 获取当前登录用户ID（字符串）
     */
    public static String getCurrentUserId()
    {
        try
        {
            return String.valueOf(SecurityUtils.getUserId());
        }
        catch (Exception e)
        {
            return null;
        }
    }

    public static boolean hasRole(String roleKey)
    {
        try
        {
            LoginUser loginUser = SecurityUtils.getLoginUser();
            if (loginUser == null || loginUser.getUser() == null || loginUser.getUser().getRoles() == null)
            {
                return false;
            }
            for (SysRole role : loginUser.getUser().getRoles())
            {
                if (roleKey.equals(role.getRoleKey()))
                {
                    return true;
                }
            }
            return false;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    public static boolean isRestrictedClinicalRole()
    {
        return hasRole("practitioner") || hasRole("apprentice")
                || hasRole("pharmacist") || hasRole("cashier");
    }

    public static Set<String> collectAccessiblePatientIds(
            List<TcmPatient> patients,
            List<TcmConsultation> allConsultations)
    {
        if (isAdmin())
        {
            Set<String> allIds = new HashSet<>();
            for (TcmPatient patient : patients)
            {
                allIds.add(patient.getId());
            }
            return allIds;
        }

        String userId = getCurrentUserId();
        if (userId == null)
        {
            return Collections.emptySet();
        }

        Set<String> accessiblePatientIds = new HashSet<>();
        long now = System.currentTimeMillis();

        // pharmacist: 只看到有 paid 状态（待发药）问诊的患者
        // cashier: 只看到有 completed/paid 状态（待收款/已收款）问诊的患者
        boolean isPharmacist = hasRole("pharmacist");
        boolean isCashier = hasRole("cashier");

        for (TcmConsultation c : allConsultations)
        {
            if (isPharmacist && "paid".equals(c.getStatus()))
            {
                accessiblePatientIds.add(c.getPatientId());
                continue;
            }
            if (isCashier && ("completed".equals(c.getStatus()) || "paid".equals(c.getStatus())))
            {
                accessiblePatientIds.add(c.getPatientId());
                continue;
            }
            if (!"completed".equals(c.getStatus()) && !"paid".equals(c.getStatus()))
            {
                if (userId.equals(c.getPractitionerId()))
                {
                    accessiblePatientIds.add(c.getPatientId());
                }
                continue;
            }
            if (isWithinOneWeek(c.getConsultDate(), now))
            {
                accessiblePatientIds.add(c.getPatientId());
            }
        }

        for (TcmPatient patient : patients)
        {
            if (userId.equals(patient.getPractitionerId()))
            {
                accessiblePatientIds.add(patient.getId());
            }
        }

        return accessiblePatientIds;
    }

    /**
     * 过滤病人列表：非管理员只能看到自己负责的病人或1周内有诊疗记录的病人
     */
    public static List<TcmPatient> filterPatients(
            List<TcmPatient> patients,
            List<TcmConsultation> allConsultations)
    {
        if (!isRestrictedClinicalRole())
        {
            return patients;
        }

        Set<String> accessiblePatientIds = collectAccessiblePatientIds(patients, allConsultations);

        List<TcmPatient> filtered = new ArrayList<>();
        for (TcmPatient p : patients)
        {
            if (accessiblePatientIds.contains(p.getId()))
            {
                filtered.add(p);
            }
        }
        return filtered;
    }

    /**
     * 检查当前用户是否可以访问某个病人
     */
    public static boolean canAccessPatient(TcmPatient patient, List<TcmConsultation> consultations)
    {
        if (!isRestrictedClinicalRole()) return true;

        String userId = getCurrentUserId();
        if (userId == null) return false;

        // 主治医师始终有访问权限
        if (userId.equals(patient.getPractitionerId())) return true;

        // 检查1周内是否有诊疗记录
        long now = System.currentTimeMillis();
        for (TcmConsultation c : consultations)
        {
            if (!patient.getId().equals(c.getPatientId())) continue;
            if (isWithinOneWeek(c.getConsultDate(), now)) return true;
        }

        return false;
    }

    public static List<TcmConsultation> filterConsultations(
            List<TcmConsultation> consultations,
            Set<String> accessiblePatientIds)
    {
        if (!isRestrictedClinicalRole())
        {
            return consultations;
        }

        String userId = getCurrentUserId();
        List<TcmConsultation> filtered = new ArrayList<>();
        for (TcmConsultation consultation : consultations)
        {
            if (accessiblePatientIds.contains(consultation.getPatientId())
                    || (userId != null && userId.equals(consultation.getPractitionerId())))
            {
                filtered.add(consultation);
            }
        }
        return filtered;
    }

    public static List<TcmAppointment> filterAppointments(
            List<TcmAppointment> appointments,
            Set<String> accessiblePatientIds)
    {
        if (!isRestrictedClinicalRole())
        {
            return appointments;
        }

        String userId = getCurrentUserId();
        List<TcmAppointment> filtered = new ArrayList<>();
        for (TcmAppointment appointment : appointments)
        {
            if (accessiblePatientIds.contains(appointment.getPatientId())
                    || (userId != null && userId.equals(appointment.getPractitionerId())))
            {
                filtered.add(appointment);
            }
        }
        return filtered;
    }

    private static boolean isWithinOneWeek(String dateStr, long now)
    {
        if (dateStr == null || dateStr.isEmpty()) return true;
        try
        {
            Date date;
            if (dateStr.contains("T"))
            {
                date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(dateStr.substring(0, 19));
            }
            else
            {
                date = new SimpleDateFormat("yyyy-MM-dd").parse(dateStr.substring(0, 10));
            }
            return (now - date.getTime()) <= ONE_WEEK_MS;
        }
        catch (Exception e)
        {
            return true; // 解析失败默认允许访问
        }
    }
}
