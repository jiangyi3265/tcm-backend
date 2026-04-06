package com.ruoyi.hospital.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.hospital.domain.*;
import com.ruoyi.hospital.service.*;
import com.ruoyi.hospital.utils.PrivacyUtils;

@RestController
@RequestMapping("/api/statistics")
public class TcmStatisticsController
{
    @Autowired
    private ITcmPatientService patientService;

    @Autowired
    private ITcmConsultationService consultationService;

    @Autowired
    private ITcmAppointmentService appointmentService;

    @Autowired
    private ITcmInventoryService inventoryService;

    @PreAuthorize("@ss.hasRole('admin') or @ss.hasRole('practitioner')")
    @GetMapping("/overview")
    public Map<String, Object> overview()
    {
        Map<String, Object> result = new HashMap<>();

        TcmPatient patientQuery = new TcmPatient();
        patientQuery.setDeletedAt("ANY");
        List<TcmPatient> patients = patientService.selectTcmPatientList(patientQuery);
        TcmConsultation consultationQuery = new TcmConsultation();
        consultationQuery.setDeletedAt("ANY");
        List<TcmConsultation> consultations = consultationService.selectTcmConsultationList(consultationQuery);
        List<TcmAppointment> appointments = appointmentService.selectTcmAppointmentList(new TcmAppointment());
        Set<String> accessiblePatientIds = PrivacyUtils.collectAccessiblePatientIds(patients, consultations, appointments);
        List<TcmPatient> visiblePatients = PrivacyUtils.filterPatients(patients, consultations, appointments);
        List<TcmConsultation> visibleConsultations = PrivacyUtils.filterConsultations(consultations, accessiblePatientIds);
        List<TcmAppointment> visibleAppointments = PrivacyUtils.filterAppointments(
                appointments,
                accessiblePatientIds);

        // 患者总数
        long activePatients = visiblePatients.stream().filter(p -> p.getDeletedAt() == null || p.getDeletedAt().isEmpty()).count();
        result.put("totalPatients", activePatients);

        // 问诊统计
        long totalConsultations = visibleConsultations.stream().filter(c -> c.getDeletedAt() == null || c.getDeletedAt().isEmpty()).count();
        long paidConsultations = visibleConsultations.stream()
                .filter(c -> "paid".equals(c.getStatus()) && (c.getDeletedAt() == null || c.getDeletedAt().isEmpty()))
                .count();
        long completedConsultations = visibleConsultations.stream()
                .filter(c -> "completed".equals(c.getStatus()) && (c.getDeletedAt() == null || c.getDeletedAt().isEmpty()))
                .count();
        result.put("totalConsultations", totalConsultations);
        result.put("paidConsultations", paidConsultations);
        result.put("completedConsultations", completedConsultations);

        // 预约统计
        result.put("totalAppointments", visibleAppointments.size());

        // 库存统计
        List<TcmInventoryItem> inventoryItems = inventoryService.selectTcmInventoryItemList(new TcmInventoryItem());
        long activeItems = inventoryItems.stream()
                .filter(i -> i.getIsActive() != null && i.getIsActive() == 1 && (i.getDeletedAt() == null || i.getDeletedAt().isEmpty()))
                .count();
        long lowStockItems = inventoryItems.stream()
                .filter(i -> i.getIsActive() != null && i.getIsActive() == 1
                        && (i.getDeletedAt() == null || i.getDeletedAt().isEmpty())
                        && i.getQuantity() != null && i.getMinStockLevel() != null
                        && i.getQuantity().compareTo(i.getMinStockLevel()) <= 0)
                .count();
        result.put("totalInventoryItems", activeItems);
        result.put("lowStockItems", lowStockItems);

        return result;
    }
}
