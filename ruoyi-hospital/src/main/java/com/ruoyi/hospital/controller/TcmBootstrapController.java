package com.ruoyi.hospital.controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.hospital.domain.*;
import com.ruoyi.hospital.service.*;
import com.ruoyi.hospital.utils.PayloadUtils;
import com.ruoyi.hospital.utils.PrivacyUtils;
import com.ruoyi.system.service.ISysUserService;
import com.alibaba.fastjson2.JSON;

@RestController
@RequestMapping("/api/bootstrap")
public class TcmBootstrapController {
    @Autowired
    private ISysUserService sysUserService;
    @Autowired
    private ITcmPatientService patientService;
    @Autowired
    private ITcmAppointmentService appointmentService;
    @Autowired
    private ITcmConsultationService consultationService;
    @Autowired
    private ITcmInventoryService inventoryService;
    @Autowired
    private ITcmBranchService branchService;
    @Autowired
    private ITcmSettingsService settingsService;
    @Autowired
    private ITcmEmailLogService emailLogService;
    @Autowired
    private ITcmFormulaService formulaService;
    @Autowired
    private ITcmSupplierService supplierService;
    @Autowired
    private ITcmAcupointService acupointService;
    @Autowired
    private ITcmUnitConversionService unitConversionService;
    @Autowired
    private ITcmHerbDictService herbDictService;
    @Autowired
    private ITcmMeridianService meridianService;
    @Autowired
    private ITcmTreatmentTemplateService treatmentTemplateService;

    @GetMapping("/export")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasRole('admin')")
    public void exportData(javax.servlet.http.HttpServletResponse response) throws Exception {
        Map<String, Object> data = bootstrap();
        String json = JSON.toJSONString(data);
        response.setContentType("application/json");
        response.setHeader("Content-Disposition",
                "attachment; filename=clinic-backup-" +
                new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date()) + ".json");
        response.getOutputStream().write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }

    @GetMapping("")
    public Map<String, Object> bootstrap() {
        Map<String, Object> result = new HashMap<>();

        // Convert sys_user to frontend user format
        SysUser query = new SysUser();
        List<SysUser> sysUsers = sysUserService.selectUserList(query);
        List<Map<String, Object>> users = new ArrayList<>();
        for (SysUser basicUser : sysUsers) {
            if (basicUser.getUserId() >= 1L) {
                SysUser u = sysUserService.selectUserById(basicUser.getUserId());
                if (u == null)
                    continue;
                Map<String, Object> um = new HashMap<>();
                um.put("id", String.valueOf(u.getUserId()));
                um.put("name", u.getNickName());
                um.put("email", u.getEmail());
                um.put("phone", u.getPhonenumber());
                String role = "admin"; // default fallback
                if (u.getRoles() != null && !u.getRoles().isEmpty()) {
                    role = u.getRoles().get(0).getRoleKey();
                } else if (u.getRoleIds() != null && u.getRoleIds().length > 0) {
                    Long roleId = u.getRoleIds()[0];
                    if (roleId == 1L)
                        role = "admin";
                    else if (roleId == 2L)
                        role = "practitioner";
                    else
                        role = "practitioner";
                }
                um.put("role", role);
                um.put("isActive", "0".equals(u.getStatus()));
                um.put("createdAt", u.getCreateTime());
                users.add(um);
            }
        }
        // 使用 deletedAt="ANY" 获取包含已删除记录的完整列表（前端需要回收站功能）
        TcmPatient patientQuery = new TcmPatient();
        patientQuery.setDeletedAt("ANY");
        List<TcmPatient> allPatients = patientService.selectTcmPatientList(patientQuery);
        List<TcmAppointment> allAppointments = appointmentService.selectTcmAppointmentList(new TcmAppointment());
        TcmConsultation consultQuery = new TcmConsultation();
        consultQuery.setDeletedAt("ANY");
        List<TcmConsultation> allConsultations = consultationService.selectTcmConsultationList(consultQuery);

        // 隐私保护：收紧医师/学徒的病人、预约、诊疗范围
        List<TcmPatient> accessiblePatients = PrivacyUtils.filterPatients(allPatients, allConsultations);
        Set<String> accessiblePatientIds = PrivacyUtils.collectAccessiblePatientIds(allPatients, allConsultations);
        List<TcmConsultation> visibleConsultations = PrivacyUtils.filterConsultations(allConsultations, accessiblePatientIds);
        List<TcmAppointment> visibleAppointments = PrivacyUtils.filterAppointments(allAppointments, accessiblePatientIds);

        result.put("users", filterUsers(users, accessiblePatients, visibleAppointments, visibleConsultations));
        result.put("patients", PayloadUtils.flattenPatients(accessiblePatients));
        result.put("appointments", PayloadUtils.flattenAppointments(visibleAppointments));
        result.put("consultations", PayloadUtils.flattenConsultations(visibleConsultations));
        result.put("inventory", canViewInventory()
                ? PayloadUtils.flattenInventory(
                        inventoryService.selectTcmInventoryItemList(new TcmInventoryItem()))
                : new ArrayList<>());
        result.put("branches", PayloadUtils.flattenBranches(
                branchService.selectTcmBranchList(new TcmBranch())));
        result.put("settings", filterSettings(settingsService.getBundle()));
        result.put("emailLog", canViewEmailLog()
                ? emailLogService.selectTcmEmailLogList(new TcmEmailLog())
                : new ArrayList<>());
        result.put("formulas", PayloadUtils.flattenFormulas(
                formulaService.selectTcmFormulaList(new TcmFormula())));
        result.put("suppliers", canViewSuppliers()
                ? PayloadUtils.flattenSuppliers(
                        supplierService.selectTcmSupplierList(new TcmSupplier()))
                : new ArrayList<>());
        result.put("acupoints", PayloadUtils.flattenAcupoints(
                acupointService.selectTcmAcupointList(new TcmAcupoint())));
        // Unit conversions as simple list
        List<TcmUnitConversion> conversions = unitConversionService.selectAll();
        List<Map<String, Object>> convList = new ArrayList<>();
        for (TcmUnitConversion c : conversions) {
            Map<String, Object> cm = new LinkedHashMap<>();
            cm.put("id", c.getId());
            cm.put("fromUnit", c.getFromUnit());
            cm.put("toUnit", c.getToUnit());
            cm.put("factor", c.getFactor());
            cm.put("notes", c.getNotes());
            convList.add(cm);
        }
        result.put("unitConversions", convList);

        // Herb dictionary
        List<TcmHerbDict> herbs = herbDictService.selectTcmHerbDictList(new TcmHerbDict());
        List<Map<String, Object>> herbList = new ArrayList<>();
        for (TcmHerbDict h : herbs) {
            Map<String, Object> hm = new LinkedHashMap<>();
            hm.put("id", h.getId()); hm.put("name", h.getName()); hm.put("alias", h.getAlias());
            hm.put("pinyin", h.getPinyin()); hm.put("category", h.getCategory());
            hm.put("nature", h.getNature()); hm.put("taste", h.getTaste());
            hm.put("meridianTropism", h.getMeridianTropism()); hm.put("efficacy", h.getEfficacy());
            hm.put("indication", h.getIndication()); hm.put("dosageRange", h.getDosageRange());
            hm.put("contraindication", h.getContraindication()); hm.put("notes", h.getNotes());
            hm.put("isActive", h.getIsActive() != null && h.getIsActive() == 1);
            hm.put("deletedAt", h.getDeletedAt());
            herbList.add(hm);
        }
        result.put("herbDict", herbList);

        // Meridians
        List<TcmMeridian> meridians = meridianService.selectTcmMeridianList(new TcmMeridian());
        List<Map<String, Object>> merList = new ArrayList<>();
        for (TcmMeridian mer : meridians) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("id", mer.getId()); mm.put("name", mer.getName());
            mm.put("englishName", mer.getEnglishName()); mm.put("abbr", mer.getAbbr());
            mm.put("category", mer.getCategory()); mm.put("organ", mer.getOrgan());
            mm.put("pathway", mer.getPathway()); mm.put("acupointCount", mer.getAcupointCount());
            mm.put("indication", mer.getIndication()); mm.put("notes", mer.getNotes());
            mm.put("isActive", mer.getIsActive() != null && mer.getIsActive() == 1);
            mm.put("deletedAt", mer.getDeletedAt());
            merList.add(mm);
        }
        result.put("meridians", merList);

        // Treatment templates
        List<TcmTreatmentTemplate> templates = canViewTemplates()
                ? treatmentTemplateService.selectTcmTreatmentTemplateList(new TcmTreatmentTemplate())
                : new ArrayList<>();
        List<Map<String, Object>> tmplList = new ArrayList<>();
        for (TcmTreatmentTemplate t : templates) {
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("id", t.getId()); tm.put("name", t.getName()); tm.put("disease", t.getDisease());
            tm.put("category", t.getCategory()); tm.put("description", t.getDescription());
            try { tm.put("acupoints", JSON.parseArray(t.getAcupointsJson())); } catch (Exception e) { tm.put("acupoints", new ArrayList<>()); }
            try { tm.put("formulaIds", JSON.parseArray(t.getFormulaIds())); } catch (Exception e) { tm.put("formulaIds", new ArrayList<>()); }
            tm.put("advice", t.getAdvice()); tm.put("notes", t.getNotes());
            tm.put("isActive", t.getIsActive() != null && t.getIsActive() == 1);
            tm.put("deletedAt", t.getDeletedAt());
            tmplList.add(tm);
        }
        result.put("templates", tmplList);

        return result;
    }

    private boolean canViewInventory()
    {
        return PrivacyUtils.isAdmin()
                || PrivacyUtils.hasRole("practitioner")
                || PrivacyUtils.hasRole("pharmacist");
    }

    private boolean canViewEmailLog()
    {
        return PrivacyUtils.isAdmin();
    }

    private boolean canViewSuppliers()
    {
        return PrivacyUtils.isAdmin();
    }

    private boolean canViewTemplates()
    {
        return PrivacyUtils.isAdmin();
    }

    private Map<String, Object> filterSettings(Map<String, Object> settings)
    {
        if (settings == null)
        {
            return new HashMap<>();
        }
        if (PrivacyUtils.isAdmin())
        {
            return settings;
        }

        Map<String, Object> filtered = new LinkedHashMap<>();
        copySetting(settings, filtered, "taxRate");
        copySetting(settings, filtered, "rooms");
        copySetting(settings, filtered, "serviceTypes");
        copySetting(settings, filtered, "clinicName");
        copySetting(settings, filtered, "clinicAddress");
        copySetting(settings, filtered, "clinicPhone");
        copySetting(settings, filtered, "priceLists");

        if (PrivacyUtils.hasRole("practitioner") || PrivacyUtils.hasRole("cashier"))
        {
            copySetting(settings, filtered, "practitionerInterval");
        }
        return filtered;
    }

    private List<Map<String, Object>> filterUsers(
            List<Map<String, Object>> users,
            List<TcmPatient> patients,
            List<TcmAppointment> appointments,
            List<TcmConsultation> consultations)
    {
        if (PrivacyUtils.isAdmin())
        {
            return users;
        }

        Set<String> visibleUserIds = new HashSet<>();
        String currentUserId = PrivacyUtils.getCurrentUserId();
        if (currentUserId != null)
        {
            visibleUserIds.add(currentUserId);
        }

        for (TcmPatient patient : patients)
        {
            if (patient.getPractitionerId() != null && !patient.getPractitionerId().isEmpty())
            {
                visibleUserIds.add(patient.getPractitionerId());
            }
        }
        collectPractitionerIds(consultations, visibleUserIds);
        collectPractitionerIds(appointments, visibleUserIds);

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> user : users)
        {
            Object id = user.get("id");
            Object role = user.get("role");
            if (id != null && visibleUserIds.contains(String.valueOf(id)))
            {
                filtered.add(user);
                continue;
            }
            if ("admin".equals(String.valueOf(role)) && currentUserId != null && currentUserId.equals(String.valueOf(id)))
            {
                filtered.add(user);
            }
        }
        return filtered;
    }

    private void collectPractitionerIds(Collection<?> records, Set<String> visibleUserIds)
    {
        for (Object record : records)
        {
            String practitionerId = null;
            if (record instanceof TcmConsultation)
            {
                practitionerId = ((TcmConsultation) record).getPractitionerId();
            }
            else if (record instanceof TcmAppointment)
            {
                practitionerId = ((TcmAppointment) record).getPractitionerId();
            }
            if (practitionerId != null && !practitionerId.isEmpty())
            {
                visibleUserIds.add(practitionerId);
            }
        }
    }

    private void copySetting(Map<String, Object> source, Map<String, Object> target, String key)
    {
        if (source.containsKey(key))
        {
            target.put(key, source.get(key));
        }
    }
}
