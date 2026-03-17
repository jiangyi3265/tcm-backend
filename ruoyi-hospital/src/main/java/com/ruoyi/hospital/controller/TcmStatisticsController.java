package com.ruoyi.hospital.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.hospital.domain.*;
import com.ruoyi.hospital.service.*;

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

        // 患者总数
        List<TcmPatient> patients = patientService.selectTcmPatientList(new TcmPatient());
        long activePatients = patients.stream().filter(p -> p.getDeletedAt() == null || p.getDeletedAt().isEmpty()).count();
        result.put("totalPatients", activePatients);

        // 问诊统计
        List<TcmConsultation> consultations = consultationService.selectTcmConsultationList(new TcmConsultation());
        long totalConsultations = consultations.stream().filter(c -> c.getDeletedAt() == null || c.getDeletedAt().isEmpty()).count();
        long paidConsultations = consultations.stream()
                .filter(c -> "paid".equals(c.getStatus()) && (c.getDeletedAt() == null || c.getDeletedAt().isEmpty()))
                .count();
        long completedConsultations = consultations.stream()
                .filter(c -> "completed".equals(c.getStatus()) && (c.getDeletedAt() == null || c.getDeletedAt().isEmpty()))
                .count();
        result.put("totalConsultations", totalConsultations);
        result.put("paidConsultations", paidConsultations);
        result.put("completedConsultations", completedConsultations);

        // 预约统计
        List<TcmAppointment> appointments = appointmentService.selectTcmAppointmentList(new TcmAppointment());
        result.put("totalAppointments", appointments.size());

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
