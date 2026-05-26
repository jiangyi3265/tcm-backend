package com.ruoyi.hospital.utils;

import java.util.*;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmPatient;

/**
 * 隐私保护工具类
 *
 * 规则：非主治医师在诊疗结束3天后无法查看该病人档案
 * - Admin 始终有完全访问权限
 * - 主治医师(patient.practitionerId)始终有访问权限
 * - 其他医师仅在诊疗完成后3天内有访问权限
 */
public class PrivacyUtils
{
    private static final int RECENT_CONSULTATION_MONTHS = 3;
    private static final int APPOINTMENT_RECORD_SHARE_DAYS = 7;
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Shanghai");

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
        if (isAdmin())
        {
            return false;
        }
        return hasRole("practitioner") || hasRole("apprentice")
                || hasRole("pharmacist") || hasRole("cashier");
    }

    public static boolean shouldHidePatientContactForCurrentUser()
    {
        if (isAdmin())
        {
            return false;
        }
        return hasRole("practitioner") || hasRole("apprentice");
    }

    public static Set<String> collectAccessiblePatientIds(
            List<TcmPatient> patients,
            List<TcmConsultation> allConsultations)
    {
        return collectAccessiblePatientIds(patients, allConsultations, Collections.emptyList());
    }

    public static Set<String> collectAccessiblePatientIds(
            List<TcmPatient> patients,
            List<TcmConsultation> allConsultations,
            List<TcmAppointment> appointments)
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
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        InternshipWindow internshipWindow = resolveActiveInternshipWindow();

        if (hasRole("apprentice"))
        {
            if (internshipWindow == null)
            {
                return Collections.emptySet();
            }
            accessiblePatientIds.addAll(collectInternshipPatientIds(allConsultations, appointments, internshipWindow));
            return accessiblePatientIds;
        }

        // pharmacist: 与前端一致，可见 paid 问诊或存在 editing/pending/dispensed 处方的患者
        // cashier: 与前端一致，可见 completed/paid 问诊或存在 pending/dispensed 处方的患者
        boolean isPractitioner = hasRole("practitioner");
        boolean isPharmacist = hasRole("pharmacist");
        boolean isCashier = hasRole("cashier");

        for (TcmConsultation c : allConsultations)
        {
            if (isDeletedConsultation(c))
            {
                continue;
            }
            if (isPharmacist && isPharmacyVisibleConsultation(c))
            {
                accessiblePatientIds.add(c.getPatientId());
                continue;
            }
            if (isCashier && isCashierVisibleConsultation(c))
            {
                accessiblePatientIds.add(c.getPatientId());
                continue;
            }
        }

        if (isPractitioner)
        {
            for (TcmPatient patient : patients)
            {
                if (userId.equals(patient.getPractitionerId()))
                {
                    accessiblePatientIds.add(patient.getId());
                }
            }

            for (TcmAppointment appointment : appointments)
            {
                if (isPractitionerAppointmentActiveForSharing(appointment, userId, today))
                {
                    accessiblePatientIds.add(appointment.getPatientId());
                }
            }
        }

        return accessiblePatientIds;
    }

    /**
     * 过滤病人列表：非管理员只能看到自己负责的病人或3天内有诊疗记录的病人
     */
    public static List<TcmPatient> filterPatients(
            List<TcmPatient> patients,
            List<TcmConsultation> allConsultations)
    {
        return filterPatients(patients, allConsultations, Collections.emptyList());
    }

    public static List<TcmPatient> filterPatients(
            List<TcmPatient> patients,
            List<TcmConsultation> allConsultations,
            List<TcmAppointment> appointments)
    {
        if (!isRestrictedClinicalRole())
        {
            return patients;
        }

        Set<String> accessiblePatientIds = collectAccessiblePatientIds(patients, allConsultations, appointments);

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
        return canAccessPatient(patient, consultations, Collections.emptyList());
    }

    public static boolean canAccessPatient(
            TcmPatient patient,
            List<TcmConsultation> consultations,
            List<TcmAppointment> appointments)
    {
        if (isAdmin()) return true;
        if (!isRestrictedClinicalRole()) return true;
        if (patient == null) return false;

        String userId = getCurrentUserId();
        if (userId == null) return false;

        if (hasRole("apprentice"))
        {
            InternshipWindow internshipWindow = resolveActiveInternshipWindow();
            if (internshipWindow == null)
            {
                return false;
            }
            return collectInternshipPatientIds(consultations, appointments, internshipWindow).contains(patient.getId());
        }

        if (hasRole("pharmacist") && hasPharmacyVisibleConsultation(patient.getId(), consultations))
        {
            return true;
        }

        if (hasRole("cashier") && hasCashierVisibleConsultation(patient.getId(), consultations))
        {
            return true;
        }

        // 主治医师始终有访问权限
        if (hasRole("practitioner") && userId.equals(patient.getPractitionerId())) return true;

        if (hasRole("practitioner")
                && hasActivePractitionerAppointment(patient.getId(), appointments, userId, LocalDate.now(CLINIC_ZONE)))
        {
            return true;
        }

        // 检查3天内是否有诊疗记录
        for (TcmConsultation c : consultations)
        {
            if (isDeletedConsultation(c))
            {
                continue;
            }
            if (!patient.getId().equals(c.getPatientId())) continue;
            if (hasRole("practitioner") && userId.equals(c.getPractitionerId())) return true;
        }

        return false;
    }

    private static boolean hasActivePractitionerAppointment(
            String patientId,
            List<TcmAppointment> appointments,
            String userId,
            LocalDate today)
    {
        if (patientId == null || appointments == null)
        {
            return false;
        }
        for (TcmAppointment appointment : appointments)
        {
            if (patientId.equals(appointment.getPatientId())
                    && isPractitionerAppointmentActiveForSharing(appointment, userId, today))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean isPractitionerAppointmentActiveForSharing(
            TcmAppointment appointment,
            String userId,
            LocalDate today)
    {
        if (appointment == null
                || userId == null
                || appointment.getPatientId() == null
                || appointment.getPatientId().trim().isEmpty()
                || !userId.equals(appointment.getPractitionerId()))
        {
            return false;
        }
        String status = appointment.getStatus();
        if (status != null && "cancelled".equalsIgnoreCase(status.trim()))
        {
            return false;
        }
        LocalDate endDate = parseLocalDate(firstNonBlank(appointment.getEndTime(), appointment.getStartTime()));
        if (endDate == null || today == null)
        {
            return false;
        }
        return !today.isAfter(endDate.plusDays(APPOINTMENT_RECORD_SHARE_DAYS));
    }

    private static boolean hasOwnConsultation(
            String patientId,
            List<TcmConsultation> consultations,
            String userId)
    {
        if (patientId == null || consultations == null || userId == null)
        {
            return false;
        }
        for (TcmConsultation consultation : consultations)
        {
            if (!isDeletedConsultation(consultation)
                    && patientId.equals(consultation.getPatientId())
                    && userId.equals(consultation.getPractitionerId()))
            {
                return true;
            }
        }
        return false;
    }

    public static List<TcmConsultation> filterConsultations(
            List<TcmConsultation> consultations,
            Set<String> accessiblePatientIds)
    {
        return filterConsultations(consultations, accessiblePatientIds, Collections.emptyList());
    }

    public static List<TcmConsultation> filterConsultations(
            List<TcmConsultation> consultations,
            Set<String> accessiblePatientIds,
            List<TcmAppointment> appointments)
    {
        if (isAdmin() || !isRestrictedClinicalRole())
        {
            return consultations;
        }

        String userId = getCurrentUserId();
        InternshipWindow internshipWindow = hasRole("apprentice") ? resolveActiveInternshipWindow() : null;
        List<TcmConsultation> filtered = new ArrayList<>();
        for (TcmConsultation consultation : consultations)
        {
            if (consultation == null)
            {
                continue;
            }
            if (hasRole("apprentice"))
            {
                if (internshipWindow != null
                        && accessiblePatientIds.contains(consultation.getPatientId())
                        && isWithinWindow(parseLocalDate(consultation.getConsultDate()), internshipWindow))
                {
                    filtered.add(consultation);
                }
                continue;
            }
            if (userId != null && userId.equals(consultation.getPractitionerId()))
            {
                filtered.add(consultation);
                continue;
            }
            if (accessiblePatientIds.contains(consultation.getPatientId())
                    && isRecentConsultation(consultation, LocalDate.now(CLINIC_ZONE))
                    && hasActivePractitionerAppointment(
                            consultation.getPatientId(),
                            appointments,
                            userId,
                            LocalDate.now(CLINIC_ZONE)))
            {
                filtered.add(consultation);
            }
        }
        return filtered;
    }

    public static List<TcmConsultation> filterConsultations(
            List<TcmConsultation> consultations,
            List<TcmPatient> patients,
            List<TcmAppointment> appointments)
    {
        if (isAdmin() || !isRestrictedClinicalRole())
        {
            return consultations;
        }
        Map<String, TcmPatient> patientById = new HashMap<>();
        for (TcmPatient patient : patients)
        {
            if (patient != null && patient.getId() != null)
            {
                patientById.put(patient.getId(), patient);
            }
        }
        List<TcmConsultation> filtered = new ArrayList<>();
        for (TcmConsultation consultation : consultations)
        {
            TcmPatient patient = consultation != null ? patientById.get(consultation.getPatientId()) : null;
            if (canAccessConsultation(consultation, patient, appointments))
            {
                filtered.add(consultation);
            }
        }
        return filtered;
    }

    public static boolean canAccessConsultation(
            TcmConsultation consultation,
            TcmPatient patient,
            List<TcmAppointment> appointments)
    {
        if (consultation == null)
        {
            return false;
        }
        if (isAdmin()) return true;
        if (!isRestrictedClinicalRole()) return true;

        String userId = getCurrentUserId();
        if (userId == null) return false;

        if (hasRole("apprentice"))
        {
            InternshipWindow internshipWindow = resolveActiveInternshipWindow();
            return internshipWindow != null
                    && isWithinWindow(parseLocalDate(consultation.getConsultDate()), internshipWindow);
        }

        if (hasRole("pharmacist") && isPharmacyVisibleConsultation(consultation))
        {
            return true;
        }
        if (hasRole("cashier") && isCashierVisibleConsultation(consultation))
        {
            return true;
        }

        if (!hasRole("practitioner"))
        {
            return false;
        }
        if (patient != null && userId.equals(patient.getPractitionerId()))
        {
            return true;
        }
        if (userId.equals(consultation.getPractitionerId()))
        {
            return true;
        }
        LocalDate today = LocalDate.now(CLINIC_ZONE);
        return isRecentConsultation(consultation, today)
                && hasActivePractitionerAppointment(
                        consultation.getPatientId(),
                        appointments,
                        userId,
                        today);
    }

    public static List<TcmAppointment> filterAppointments(
            List<TcmAppointment> appointments,
            Set<String> accessiblePatientIds)
    {
        if (isAdmin() || !isRestrictedClinicalRole())
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

    private static boolean isWithinAccessWindow(String dateStr, long now)
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
            return (now - date.getTime()) <= APPOINTMENT_RECORD_SHARE_DAYS * 24L * 60 * 60 * 1000;
        }
        catch (Exception e)
        {
            return true; // 解析失败默认允许访问
        }
    }

    private static String firstNonBlank(String primary, String fallback)
    {
        if (primary != null && !primary.trim().isEmpty())
        {
            return primary;
        }
        return fallback;
    }

    private static boolean isRecentConsultation(TcmConsultation consultation, LocalDate today)
    {
        if (consultation == null || today == null)
        {
            return false;
        }
        LocalDate consultDate = parseLocalDate(consultation.getConsultDate());
        if (consultDate == null)
        {
            return false;
        }
        LocalDate earliest = today.minusMonths(RECENT_CONSULTATION_MONTHS);
        return !consultDate.isBefore(earliest) && !consultDate.isAfter(today.plusDays(1));
    }

    private static boolean hasPharmacyVisibleConsultation(String patientId, List<TcmConsultation> consultations)
    {
        for (TcmConsultation consultation : consultations)
        {
            if (isDeletedConsultation(consultation))
            {
                continue;
            }
            if (patientId.equals(consultation.getPatientId()) && isPharmacyVisibleConsultation(consultation))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCashierVisibleConsultation(String patientId, List<TcmConsultation> consultations)
    {
        for (TcmConsultation consultation : consultations)
        {
            if (isDeletedConsultation(consultation))
            {
                continue;
            }
            if (patientId.equals(consultation.getPatientId()) && isCashierVisibleConsultation(consultation))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean isPharmacyVisibleConsultation(TcmConsultation consultation)
    {
        if (consultation == null)
        {
            return false;
        }
        if ("paid".equals(consultation.getStatus()))
        {
            return true;
        }
        JSONObject payload = parsePayload(consultation.getPayload());
        if ("paid".equals(payload.getString("paymentStatus")))
        {
            return true;
        }
        return hasActivePrescriptionWithStatuses(consultation, Arrays.asList("editing", "pending", "dispensed"));
    }

    private static boolean isCashierVisibleConsultation(TcmConsultation consultation)
    {
        if (consultation == null)
        {
            return false;
        }
        if ("completed".equals(consultation.getStatus()) || "paid".equals(consultation.getStatus()))
        {
            return true;
        }
        JSONObject payload = parsePayload(consultation.getPayload());
        String paymentStatus = payload.getString("paymentStatus");
        if (paymentStatus != null && !"unpaid".equals(paymentStatus))
        {
            return true;
        }
        return hasActivePrescriptionWithStatuses(consultation, Arrays.asList("pending", "dispensed"));
    }

    private static boolean isDeletedConsultation(TcmConsultation consultation)
    {
        return consultation != null
                && consultation.getDeletedAt() != null
                && !consultation.getDeletedAt().trim().isEmpty();
    }

    private static boolean hasActivePrescriptionWithStatuses(TcmConsultation consultation, List<String> statuses)
    {
        JSONObject payload = parsePayload(consultation.getPayload());
        List<Map<String, Object>> prescriptions = toMapList(payload.get("prescriptions"));
        for (Map<String, Object> prescription : prescriptions)
        {
            if (isDeletedPrescription(prescription))
            {
                continue;
            }
            String status = resolvePrescriptionStatus(prescription);
            if (statuses.contains(status))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean isDeletedPrescription(Map<String, Object> prescription)
    {
        if (prescription == null)
        {
            return false;
        }
        Object deletedAt = prescription.get("deletedAt");
        return deletedAt != null && !String.valueOf(deletedAt).trim().isEmpty();
    }

    private static String resolvePrescriptionStatus(Map<String, Object> prescription)
    {
        if (prescription == null)
        {
            return "editing";
        }
        Object rxStatus = prescription.get("rxStatus");
        if (rxStatus != null && !String.valueOf(rxStatus).trim().isEmpty())
        {
            return String.valueOf(rxStatus).trim();
        }
        Object dispensingCompleted = prescription.get("dispensingCompleted");
        if (Boolean.TRUE.equals(dispensingCompleted) || "true".equalsIgnoreCase(String.valueOf(dispensingCompleted)))
        {
            return "dispensed";
        }
        return "editing";
    }

    private static JSONObject parsePayload(String payload)
    {
        if (payload == null || payload.trim().isEmpty())
        {
            return new JSONObject();
        }
        try
        {
            JSONObject parsed = JSON.parseObject(payload);
            return parsed != null ? parsed : new JSONObject();
        }
        catch (Exception e)
        {
            return new JSONObject();
        }
    }

    private static InternshipWindow resolveActiveInternshipWindow()
    {
        if (!hasRole("apprentice"))
        {
            return null;
        }
        try
        {
            LoginUser loginUser = SecurityUtils.getLoginUser();
            if (loginUser == null || loginUser.getUser() == null)
            {
                return null;
            }
            JSONObject profile = parsePayload(loginUser.getUser().getRemark());
            List<String> internshipDates = toStringList(profile.get("internshipDates"));
            if (internshipDates.isEmpty())
            {
                return null;
            }
            LocalDate today = LocalDate.now(CLINIC_ZONE);
            InternshipWindow matched = null;
            for (String rawDate : internshipDates)
            {
                try
                {
                    LocalDate start = LocalDate.parse(rawDate);
                    LocalDate end = start.plusDays(2);
                    if (today.isBefore(start) || today.isAfter(end))
                    {
                        continue;
                    }
                    if (matched == null || start.isAfter(matched.startDate))
                    {
                        matched = new InternshipWindow(start, end);
                    }
                }
                catch (Exception ignored)
                {
                }
            }
            return matched;
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private static Set<String> collectInternshipPatientIds(
            List<TcmConsultation> consultations,
            List<TcmAppointment> appointments,
            InternshipWindow window)
    {
        Set<String> patientIds = new HashSet<>();
        if (window == null)
        {
            return patientIds;
        }
        for (TcmConsultation consultation : consultations)
        {
            if (isDeletedConsultation(consultation))
            {
                continue;
            }
            LocalDate consultDate = parseLocalDate(consultation != null ? consultation.getConsultDate() : null);
            if (consultation != null && consultation.getPatientId() != null && isWithinWindow(consultDate, window))
            {
                patientIds.add(consultation.getPatientId());
            }
        }
        for (TcmAppointment appointment : appointments)
        {
            if (appointment == null
                    || "cancelled".equalsIgnoreCase(String.valueOf(appointment.getStatus()))
                    || appointment.getPatientId() == null)
            {
                continue;
            }
            LocalDate appointmentDate = parseLocalDate(appointment.getStartTime());
            if (isWithinWindow(appointmentDate, window))
            {
                patientIds.add(appointment.getPatientId());
            }
        }
        return patientIds;
    }

    private static boolean isWithinWindow(LocalDate date, InternshipWindow window)
    {
        if (date == null || window == null)
        {
            return false;
        }
        return !date.isBefore(window.startDate) && !date.isAfter(window.endDate);
    }

    private static LocalDate parseLocalDate(String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return null;
        }
        try
        {
            if (value.contains("T"))
            {
                return LocalDateTime.parse(value.substring(0, 19).replace('T', ' '),
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toLocalDate();
            }
            if (value.length() >= 19 && value.charAt(10) == ' ')
            {
                return LocalDateTime.parse(value.substring(0, 19),
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")).toLocalDate();
            }
            return LocalDate.parse(value.substring(0, 10));
        }
        catch (Exception e)
        {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toMapList(Object value)
    {
        if (value == null)
        {
            return new ArrayList<>();
        }
        if (value instanceof List<?>)
        {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object entry : (List<?>) value)
            {
                if (entry instanceof Map<?, ?>)
                {
                    result.add(new LinkedHashMap<>((Map<String, Object>) entry));
                }
                else if (entry != null)
                {
                    try
                    {
                        result.add(JSON.parseObject(JSON.toJSONString(entry), Map.class));
                    }
                    catch (Exception ignored)
                    {
                    }
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    private static List<String> toStringList(Object value)
    {
        List<String> items = new ArrayList<>();
        if (!(value instanceof List<?>))
        {
            return items;
        }
        for (Object entry : (List<?>) value)
        {
            if (entry == null)
            {
                continue;
            }
            String raw = String.valueOf(entry).trim();
            if (!raw.isEmpty())
            {
                items.add(raw);
            }
        }
        return items;
    }

    private static final class InternshipWindow
    {
        private final LocalDate startDate;
        private final LocalDate endDate;

        private InternshipWindow(LocalDate startDate, LocalDate endDate)
        {
            this.startDate = startDate;
            this.endDate = endDate;
        }
    }
}
