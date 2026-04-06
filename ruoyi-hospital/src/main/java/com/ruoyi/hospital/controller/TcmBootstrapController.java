package com.ruoyi.hospital.controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.hospital.domain.TcmAcupoint;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmBranch;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmEmailLog;
import com.ruoyi.hospital.domain.TcmFormula;
import com.ruoyi.hospital.domain.TcmHerbDict;
import com.ruoyi.hospital.domain.TcmInventoryItem;
import com.ruoyi.hospital.domain.TcmMeridian;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.domain.TcmSupplier;
import com.ruoyi.hospital.domain.TcmTreatmentTemplate;
import com.ruoyi.hospital.domain.TcmUnitConversion;
import com.ruoyi.hospital.service.ITcmAcupointService;
import com.ruoyi.hospital.service.ITcmAppointmentService;
import com.ruoyi.hospital.service.ITcmBranchService;
import com.ruoyi.hospital.service.ITcmConsultationService;
import com.ruoyi.hospital.service.ITcmEmailLogService;
import com.ruoyi.hospital.service.ITcmFormulaService;
import com.ruoyi.hospital.service.ITcmHerbDictService;
import com.ruoyi.hospital.service.ITcmInventoryService;
import com.ruoyi.hospital.service.ITcmMeridianService;
import com.ruoyi.hospital.service.ITcmPatientService;
import com.ruoyi.hospital.service.ITcmSettingsService;
import com.ruoyi.hospital.service.ITcmSupplierService;
import com.ruoyi.hospital.service.ITcmTreatmentTemplateService;
import com.ruoyi.hospital.service.ITcmUnitConversionService;
import com.ruoyi.hospital.utils.PayloadUtils;
import com.ruoyi.hospital.utils.PrivacyUtils;
import com.ruoyi.system.service.ISysRoleService;
import com.ruoyi.system.service.ISysUserService;

@RestController
@RequestMapping("/api/bootstrap")
public class TcmBootstrapController
{
    @Autowired
    private ISysUserService sysUserService;
    @Autowired
    private ISysRoleService roleService;
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
    @PreAuthorize("@ss.hasRole('admin')")
    public void exportData(javax.servlet.http.HttpServletResponse response) throws Exception
    {
        Map<String, Object> data = bootstrap();
        String json = JSON.toJSONString(data);
        response.setContentType("application/json");
        response.setHeader(
                "Content-Disposition",
                "attachment; filename=clinic-backup-"
                        + new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date()) + ".json");
        response.getOutputStream().write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }

    @GetMapping("")
    public Map<String, Object> bootstrap()
    {
        Map<String, Object> result = new HashMap<>();

        List<Map<String, Object>> users = buildUsers();

        TcmPatient patientQuery = new TcmPatient();
        patientQuery.setDeletedAt("ANY");
        List<TcmPatient> allPatients = patientService.selectTcmPatientList(patientQuery);
        List<TcmAppointment> allAppointments = appointmentService.selectTcmAppointmentList(new TcmAppointment());
        TcmConsultation consultationQuery = new TcmConsultation();
        consultationQuery.setDeletedAt("ANY");
        List<TcmConsultation> allConsultations = consultationService.selectTcmConsultationList(consultationQuery);

        List<TcmPatient> accessiblePatients = PrivacyUtils.filterPatients(allPatients, allConsultations, allAppointments);
        Set<String> accessiblePatientIds = PrivacyUtils.collectAccessiblePatientIds(allPatients, allConsultations, allAppointments);
        List<TcmConsultation> visibleConsultations = PrivacyUtils.filterConsultations(allConsultations, accessiblePatientIds);
        List<TcmAppointment> visibleAppointments = PrivacyUtils.filterAppointments(allAppointments, accessiblePatientIds);

        result.put("users", filterUsers(users, accessiblePatients, visibleAppointments, visibleConsultations));
        result.put(
                "patients",
                PrivacyUtils.hasRole("apprentice")
                        ? PayloadUtils.flattenPatientSummaries(accessiblePatients)
                        : PayloadUtils.flattenPatients(accessiblePatients));
        result.put("appointments", PayloadUtils.flattenAppointments(visibleAppointments));
        result.put("consultations", PayloadUtils.flattenConsultations(visibleConsultations));
        result.put(
                "inventory",
                canViewInventory()
                        ? PayloadUtils.flattenInventory(
                                inventoryService.selectTcmInventoryItemList(new TcmInventoryItem()))
                        : new ArrayList<>());
        result.put("branches", PayloadUtils.flattenBranches(branchService.selectTcmBranchList(new TcmBranch())));
        result.put("settings", filterSettings(settingsService.getBundle()));
        result.put(
                "emailLog",
                canViewEmailLog() ? emailLogService.selectTcmEmailLogList(new TcmEmailLog()) : new ArrayList<>());
        result.put("formulas", PayloadUtils.flattenFormulas(formulaService.selectTcmFormulaList(new TcmFormula())));
        result.put(
                "suppliers",
                canViewSuppliers()
                        ? PayloadUtils.flattenSuppliers(supplierService.selectTcmSupplierList(new TcmSupplier()))
                        : new ArrayList<>());
        result.put("acupoints", PayloadUtils.flattenAcupoints(acupointService.selectTcmAcupointList(new TcmAcupoint())));
        result.put("unitConversions", flattenUnitConversions(unitConversionService.selectAll()));
        result.put("herbDict", flattenHerbs(herbDictService.selectTcmHerbDictList(new TcmHerbDict())));
        result.put("meridians", flattenMeridians(meridianService.selectTcmMeridianList(new TcmMeridian())));
        result.put(
                "templates",
                canViewTemplates()
                        ? flattenTemplates(treatmentTemplateService.selectTcmTreatmentTemplateList(new TcmTreatmentTemplate()))
                        : new ArrayList<>());

        return result;
    }

    private List<Map<String, Object>> buildUsers()
    {
        SysUser query = new SysUser();
        List<SysUser> sysUsers = sysUserService.selectUserList(query);
        List<Map<String, Object>> users = new ArrayList<>();
        for (SysUser basicUser : sysUsers)
        {
            if (basicUser.getUserId() < 1L)
            {
                continue;
            }
            SysUser user = sysUserService.selectUserById(basicUser.getUserId());
            if (user == null)
            {
                continue;
            }
            Map<String, Object> map = new HashMap<>();
            List<String> roleKeys = resolveRoleKeys(user);
            map.put("id", String.valueOf(user.getUserId()));
            map.put("name", user.getNickName());
            map.put("email", user.getEmail());
            map.put("phone", user.getPhonenumber());
            map.put("role", roleKeys.isEmpty() ? null : roleKeys.get(0));
            map.put("roles", roleKeys);
            map.put("isActive", "0".equals(user.getStatus()));
            map.put("createdAt", user.getCreateTime());
            JSONObject profile = parseProfileJson(user.getRemark());
            map.put("prescriptionPreference", sanitizePrescriptionPreference(profile.get("prescriptionPreference")));
            map.put("regulatoryBody", profile.getString("regulatoryBody"));
            map.put("title", profile.getString("title"));
            map.put("registrationNumber", profile.getString("registrationNumber"));
            map.put("homeAddress", profile.get("homeAddress"));
            map.put("workingHours", normalizeWorkingHours(profile.get("workingHours")));
            map.put("practitionerSortOrder", sanitizeInteger(profile.get("practitionerSortOrder")));
            map.put("serviceKeys", sanitizeStringList(profile.get("serviceKeys")));
            map.put("internshipDates", sanitizeDateList(profile.get("internshipDates")));
            users.add(map);
        }
        return users;
    }

    private JSONObject parseProfileJson(String remark)
    {
        if (remark == null || remark.trim().isEmpty())
        {
            return new JSONObject();
        }
        String trimmed = remark.trim();
        if (!trimmed.startsWith("{"))
        {
            return new JSONObject();
        }
        try
        {
            JSONObject profile = JSON.parseObject(trimmed);
            return profile != null ? profile : new JSONObject();
        }
        catch (Exception e)
        {
            return new JSONObject();
        }
    }

    private String sanitizePrescriptionPreference(Object value)
    {
        if (value == null)
        {
            return null;
        }
        String preference = String.valueOf(value).trim();
        if ("powder".equals(preference) || "raw_herbs".equals(preference) || "pills".equals(preference))
        {
            return preference;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeWorkingHours(Object value)
    {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (!(value instanceof Map))
        {
            return normalized;
        }
        Map<String, Object> rawWorkingHours = (Map<String, Object>) value;
        for (Map.Entry<String, Object> entry : rawWorkingHours.entrySet())
        {
            List<Map<String, String>> ranges = normalizeWorkingHourRanges(entry.getValue());
            if (!ranges.isEmpty())
            {
                normalized.put(entry.getKey(), ranges);
            }
        }
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> normalizeWorkingHourRanges(Object value)
    {
        List<Map<String, String>> normalized = new ArrayList<>();
        if (value instanceof Map)
        {
            Map<String, String> single = normalizeWorkingHourRange((Map<String, Object>) value);
            if (!single.isEmpty())
            {
                normalized.add(single);
            }
            return normalized;
        }
        if (!(value instanceof List))
        {
            return normalized;
        }
        for (Object item : (List<?>) value)
        {
            if (!(item instanceof Map))
            {
                continue;
            }
            Map<String, String> range = normalizeWorkingHourRange((Map<String, Object>) item);
            if (!range.isEmpty())
            {
                normalized.add(range);
            }
        }
        return normalized;
    }

    private Map<String, String> normalizeWorkingHourRange(Map<String, Object> rawRange)
    {
        Map<String, String> range = new LinkedHashMap<>();
        if (rawRange == null)
        {
            return range;
        }
        String start = normalizeTimeValue(rawRange.get("start"));
        String end = normalizeTimeValue(rawRange.get("end"));
        if (start == null || end == null)
        {
            return range;
        }
        range.put("start", start);
        range.put("end", end);
        return range;
    }

    private String normalizeTimeValue(Object value)
    {
        if (value == null)
        {
            return null;
        }
        String time = String.valueOf(value).trim();
        if (!time.matches("^\\d{2}:\\d{2}$"))
        {
            return null;
        }
        return time;
    }

    private Integer sanitizeInteger(Object value)
    {
        if (value == null)
        {
            return null;
        }
        String raw = String.valueOf(value).trim();
        if (raw.isEmpty())
        {
            return null;
        }
        try
        {
            return Integer.parseInt(raw);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    private List<String> sanitizeStringList(Object value)
    {
        List<String> items = new ArrayList<>();
        if (!(value instanceof List))
        {
            return items;
        }
        for (Object item : (List<?>) value)
        {
            if (item == null)
            {
                continue;
            }
            String normalized = String.valueOf(item).trim();
            if (!normalized.isEmpty() && !items.contains(normalized))
            {
                items.add(normalized);
            }
        }
        return items;
    }

    private List<String> sanitizeDateList(Object value)
    {
        TreeSet<String> dates = new TreeSet<>();
        if (!(value instanceof List))
        {
            return new ArrayList<>();
        }
        for (Object item : (List<?>) value)
        {
            if (item == null)
            {
                continue;
            }
            String raw = String.valueOf(item).trim();
            if (raw.isEmpty())
            {
                continue;
            }
            try
            {
                dates.add(LocalDate.parse(raw).toString());
            }
            catch (Exception ignored)
            {
            }
        }
        return new ArrayList<>(dates);
    }

    private List<Map<String, Object>> flattenUnitConversions(List<TcmUnitConversion> conversions)
    {
        List<Map<String, Object>> list = new ArrayList<>();
        for (TcmUnitConversion conversion : conversions)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", conversion.getId());
            item.put("fromUnit", conversion.getFromUnit());
            item.put("toUnit", conversion.getToUnit());
            item.put("factor", conversion.getFactor());
            item.put("notes", conversion.getNotes());
            list.add(item);
        }
        return list;
    }

    private List<Map<String, Object>> flattenHerbs(List<TcmHerbDict> herbs)
    {
        List<Map<String, Object>> list = new ArrayList<>();
        for (TcmHerbDict herb : herbs)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", herb.getId());
            item.put("name", herb.getName());
            item.put("alias", herb.getAlias());
            item.put("pinyin", herb.getPinyin());
            item.put("category", herb.getCategory());
            item.put("nature", herb.getNature());
            item.put("taste", herb.getTaste());
            item.put("meridianTropism", herb.getMeridianTropism());
            item.put("efficacy", herb.getEfficacy());
            item.put("indication", herb.getIndication());
            item.put("dosageRange", herb.getDosageRange());
            item.put("contraindication", herb.getContraindication());
            item.put("notes", herb.getNotes());
            item.put("isActive", herb.getIsActive() != null && herb.getIsActive() == 1);
            item.put("deletedAt", herb.getDeletedAt());
            list.add(item);
        }
        return list;
    }

    private List<Map<String, Object>> flattenMeridians(List<TcmMeridian> meridians)
    {
        List<Map<String, Object>> list = new ArrayList<>();
        for (TcmMeridian meridian : meridians)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", meridian.getId());
            item.put("name", meridian.getName());
            item.put("englishName", meridian.getEnglishName());
            item.put("abbr", meridian.getAbbr());
            item.put("category", meridian.getCategory());
            item.put("organ", meridian.getOrgan());
            item.put("pathway", meridian.getPathway());
            item.put("acupointCount", meridian.getAcupointCount());
            item.put("indication", meridian.getIndication());
            item.put("notes", meridian.getNotes());
            item.put("isActive", meridian.getIsActive() != null && meridian.getIsActive() == 1);
            item.put("deletedAt", meridian.getDeletedAt());
            list.add(item);
        }
        return list;
    }

    private List<Map<String, Object>> flattenTemplates(List<TcmTreatmentTemplate> templates)
    {
        List<Map<String, Object>> list = new ArrayList<>();
        for (TcmTreatmentTemplate template : templates)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", template.getId());
            item.put("name", template.getName());
            item.put("disease", template.getDisease());
            item.put("category", template.getCategory());
            item.put("description", template.getDescription());
            try
            {
                item.put("acupoints", JSON.parseArray(template.getAcupointsJson()));
            }
            catch (Exception e)
            {
                item.put("acupoints", new ArrayList<>());
            }
            try
            {
                item.put("formulaIds", JSON.parseArray(template.getFormulaIds()));
            }
            catch (Exception e)
            {
                item.put("formulaIds", new ArrayList<>());
            }
            item.put("advice", template.getAdvice());
            item.put("notes", template.getNotes());
            item.put("isActive", template.getIsActive() != null && template.getIsActive() == 1);
            item.put("deletedAt", template.getDeletedAt());
            list.add(item);
        }
        return list;
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
        if (PrivacyUtils.hasRole("practitioner") || PrivacyUtils.hasRole("apprentice"))
        {
            copySetting(settings, filtered, "practitionerIntervals");
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
            if ("admin".equals(String.valueOf(role))
                    && currentUserId != null
                    && currentUserId.equals(String.valueOf(id)))
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

    private List<String> resolveRoleKeys(SysUser user)
    {
        List<String> roleKeys = new ArrayList<>();
        List<SysRole> roles = user.getRoles();
        boolean usingEmbeddedRoles = roles != null && !roles.isEmpty();
        if (!usingEmbeddedRoles)
        {
            roles = roleService.selectRolesByUserId(user.getUserId());
        }
        if (roles == null)
        {
            return roleKeys;
        }
        for (SysRole role : roles)
        {
            if (role == null || role.getRoleKey() == null || role.getRoleKey().trim().isEmpty())
            {
                continue;
            }
            if (usingEmbeddedRoles || role.isFlag())
            {
                roleKeys.add(role.getRoleKey());
            }
        }
        return roleKeys;
    }
}
