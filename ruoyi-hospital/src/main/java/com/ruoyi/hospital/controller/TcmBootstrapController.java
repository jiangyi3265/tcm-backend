package com.ruoyi.hospital.controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
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
import com.ruoyi.hospital.domain.TcmPatientFile;
import com.ruoyi.hospital.domain.TcmPriceList;
import com.ruoyi.hospital.domain.TcmRoom;
import com.ruoyi.hospital.domain.TcmServiceType;
import com.ruoyi.hospital.domain.TcmSupplier;
import com.ruoyi.hospital.domain.TcmTreatmentTemplate;
import com.ruoyi.hospital.domain.TcmUnitConversion;
import com.ruoyi.hospital.mapper.TcmAcupointMapper;
import com.ruoyi.hospital.mapper.TcmAppointmentMapper;
import com.ruoyi.hospital.mapper.TcmBranchMapper;
import com.ruoyi.hospital.mapper.TcmConsultationMapper;
import com.ruoyi.hospital.mapper.TcmEmailLogMapper;
import com.ruoyi.hospital.mapper.TcmHerbDictMapper;
import com.ruoyi.hospital.mapper.TcmInventoryItemMapper;
import com.ruoyi.hospital.mapper.TcmMeridianMapper;
import com.ruoyi.hospital.mapper.TcmPatientFileMapper;
import com.ruoyi.hospital.mapper.TcmPatientMapper;
import com.ruoyi.hospital.mapper.TcmPriceListMapper;
import com.ruoyi.hospital.mapper.TcmRoomMapper;
import com.ruoyi.hospital.mapper.TcmServiceTypeMapper;
import com.ruoyi.hospital.mapper.TcmSupplierMapper;
import com.ruoyi.hospital.mapper.TcmTreatmentTemplateMapper;
import com.ruoyi.hospital.mapper.TcmUnitConversionMapper;
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
    @Autowired
    private TcmPatientMapper patientMapper;
    @Autowired
    private TcmPatientFileMapper patientFileMapper;
    @Autowired
    private TcmConsultationMapper consultationMapper;
    @Autowired
    private TcmAppointmentMapper appointmentMapper;
    @Autowired
    private TcmInventoryItemMapper inventoryItemMapper;
    @Autowired
    private TcmBranchMapper branchMapper;
    @Autowired
    private TcmSupplierMapper supplierMapper;
    @Autowired
    private TcmAcupointMapper acupointMapper;
    @Autowired
    private TcmHerbDictMapper herbDictMapper;
    @Autowired
    private TcmMeridianMapper meridianMapper;
    @Autowired
    private TcmTreatmentTemplateMapper treatmentTemplateMapper;
    @Autowired
    private TcmEmailLogMapper emailLogMapper;
    @Autowired
    private TcmRoomMapper roomMapper;
    @Autowired
    private TcmServiceTypeMapper serviceTypeMapper;
    @Autowired
    private TcmPriceListMapper priceListMapper;
    @Autowired
    private TcmUnitConversionMapper unitConversionMapper;

    @GetMapping("/export")
    @PreAuthorize("@ss.hasRole('admin')")
    public void exportData(javax.servlet.http.HttpServletResponse response) throws Exception
    {
        Map<String, Object> data = buildFullBackupData();
        String json = JSON.toJSONString(data);
        response.setContentType("application/json");
        response.setHeader(
                "Content-Disposition",
                "attachment; filename=clinic-backup-"
                        + new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date()) + ".json");
        response.getOutputStream().write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        response.getOutputStream().flush();
    }

    @PostMapping("/import")
    @PreAuthorize("@ss.hasRole('admin')")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importData(@RequestParam("file") MultipartFile file)
    {
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("备份包不能为空");
        }
        try
        {
            JSONObject data = JSON.parseObject(new String(file.getBytes(), StandardCharsets.UTF_8));
            int restored = 0;
            restored += restoreSettings(data.get("settings"));
            restored += restoreUsers(data.get("users"));
            restored += restorePatients(data.get("patients"));
            restored += restoreAppointments(data.get("appointments"));
            restored += restoreConsultations(data.get("consultations"));
            restored += restorePatientFiles(data.get("patientFiles"));
            restored += restoreInventory(data.get("inventory"));
            restored += restoreBranches(data.get("branches"));
            restored += restoreSuppliers(data.get("suppliers"));
            restored += restoreAcupoints(data.get("acupoints"));
            restored += restoreHerbs(data.get("herbDict"));
            restored += restoreFormulas(data.get("formulas"));
            restored += restoreUnitConversions(data.get("unitConversions"));
            restored += restoreMeridians(data.get("meridians"));
            restored += restoreTreatmentTemplates(data.get("templates"));
            restored += restoreEmailLog(data.get("emailLog"));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("restored", restored);
            result.put("ok", true);
            return result;
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new ServiceException("备份恢复失败: " + e.getMessage());
        }
    }

    @GetMapping("")
    public Map<String, Object> bootstrap()
    {
        return buildBootstrap(false);
    }

    public Map<String, Object> buildFullBackupData()
    {
        return buildBootstrap(true);
    }

    private Map<String, Object> buildBootstrap(boolean fullAccess)
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

        List<TcmPatient> accessiblePatients = fullAccess
                ? allPatients
                : PrivacyUtils.filterPatients(allPatients, allConsultations, allAppointments);
        Set<String> accessiblePatientIds = fullAccess
                ? collectAllPatientIds(allPatients)
                : PrivacyUtils.collectAccessiblePatientIds(allPatients, allConsultations, allAppointments);
        List<TcmConsultation> visibleConsultations = fullAccess
                ? allConsultations
                : PrivacyUtils.filterConsultations(allConsultations, allPatients, allAppointments);
        List<TcmAppointment> visibleAppointments = fullAccess
                ? allAppointments
                : PrivacyUtils.filterAppointments(allAppointments, accessiblePatientIds);

        result.put("users", filterUsers(users, accessiblePatients, visibleAppointments, visibleConsultations));
        result.put(
                "patients",
                !fullAccess && PrivacyUtils.hasRole("apprentice")
                        ? PayloadUtils.flattenPatientSummaries(accessiblePatients)
                        : !fullAccess && PrivacyUtils.shouldHidePatientContactForCurrentUser()
                        ? PayloadUtils.flattenPatientsWithoutContact(accessiblePatients)
                        : PayloadUtils.flattenPatients(accessiblePatients));
        result.put("appointments", PayloadUtils.flattenAppointments(visibleAppointments));
        result.put("consultations", PayloadUtils.flattenConsultations(visibleConsultations));
        result.put("patientFiles", fullAccess ? flattenPatientFiles(patientFileMapper.selectAllTcmPatientFiles()) : new ArrayList<>());
        result.put(
                "inventory",
                fullAccess || canViewInventory()
                        ? flattenInventoryWithUsage(inventoryService.selectTcmInventoryItemListIncludingDeleted(new TcmInventoryItem()))
                        : new ArrayList<>());
        result.put("branches", PayloadUtils.flattenBranches(branchService.selectTcmBranchList(new TcmBranch())));
        result.put("settings", fullAccess ? settingsService.getBundle() : filterSettings(settingsService.getBundle()));
        result.put(
                "emailLog",
                fullAccess || canViewEmailLog() ? emailLogService.selectTcmEmailLogList(new TcmEmailLog()) : new ArrayList<>());
        result.put("formulas", PayloadUtils.flattenFormulas(formulaService.selectTcmFormulaList(new TcmFormula())));
        result.put(
                "suppliers",
                fullAccess || canViewSuppliers()
                        ? PayloadUtils.flattenSuppliers(supplierService.selectTcmSupplierList(new TcmSupplier()))
                        : new ArrayList<>());
        result.put("acupoints", PayloadUtils.flattenAcupoints(acupointService.selectTcmAcupointList(new TcmAcupoint())));
        result.put("unitConversions", flattenUnitConversions(unitConversionService.selectAll()));
        result.put("herbDict", flattenHerbs(herbDictService.selectTcmHerbDictList(new TcmHerbDict())));
        result.put("meridians", flattenMeridians(meridianService.selectTcmMeridianList(new TcmMeridian())));
        result.put(
                "templates",
                fullAccess || canViewTemplates()
                        ? flattenTemplates(treatmentTemplateService.selectTcmTreatmentTemplateList(new TcmTreatmentTemplate()))
                        : new ArrayList<>());

        return result;
    }

    private Set<String> collectAllPatientIds(List<TcmPatient> patients)
    {
        Set<String> ids = new HashSet<>();
        for (TcmPatient patient : patients)
        {
            ids.add(patient.getId());
        }
        return ids;
    }

    private int restoreSettings(Object value)
    {
        Map<String, Object> settings = asObjectMap(value);
        if (settings.isEmpty())
        {
            return 0;
        }
        int restored = 0;
        Object rooms = settings.remove("rooms");
        Object serviceTypes = settings.remove("serviceTypes");
        Object priceLists = settings.remove("priceLists");
        if (!settings.isEmpty())
        {
            settingsService.updateBaseSettings(settings);
            restored += settings.size();
        }
        restored += restoreRooms(rooms);
        restored += restoreServiceTypes(serviceTypes);
        restored += restorePriceLists(priceLists);
        return restored;
    }

    private int restoreUsers(Object value)
    {
        int count = 0;
        for (Map<String, Object> row : asObjectList(value))
        {
            Long userId = longValue(row.get("id"));
            if (userId == null)
            {
                continue;
            }
            SysUser user = sysUserService.selectUserById(userId);
            if (user == null)
            {
                continue;
            }
            String name = text(row, "name", "nickName");
            if (!isBlank(name)) { user.setNickName(name); }
            if (row.containsKey("email")) { user.setEmail(text(row, "email")); }
            if (row.containsKey("phone")) { user.setPhonenumber(text(row, "phone")); }
            if (row.containsKey("isActive")) { user.setStatus(boolToInt(row.get("isActive"), 1) == 1 ? "0" : "1"); }

            JSONObject profile = parseProfileJson(user.getRemark());
            String[] profileKeys = {
                    "prescriptionPreference", "regulatoryBody", "title", "registrationNumber",
                    "organization", "organizationNumber", "homeAddress", "workingHours",
                    "practitionerSortOrder", "serviceKeys", "internshipDates", "internshipSessions",
                    "color", "overlap1", "overlap2", "dripEnabled", "signature"
            };
            for (String key : profileKeys)
            {
                if (row.containsKey(key))
                {
                    Object fieldValue = row.get(key);
                    if (fieldValue == null || (fieldValue instanceof String && String.valueOf(fieldValue).trim().isEmpty()))
                    {
                        profile.remove(key);
                    }
                    else
                    {
                        profile.put(key, fieldValue);
                    }
                }
            }
            user.setRemark(profile.toJSONString());
            sysUserService.updateUserProfile(user);
            count++;
        }
        return count;
    }

    private int restoreRooms(Object value)
    {
        int count = 0;
        for (Map<String, Object> row : asObjectList(value))
        {
            TcmRoom item = toRoom(row);
            if (isBlank(item.getId())) { continue; }
            if (roomMapper.selectTcmRoomById(item.getId()) == null)
            {
                roomMapper.insertTcmRoom(item);
            }
            else
            {
                roomMapper.updateTcmRoom(item);
            }
            count++;
        }
        return count;
    }

    private int restoreServiceTypes(Object value)
    {
        int count = 0;
        for (Map<String, Object> row : asServiceTypeRows(value))
        {
            TcmServiceType item = toServiceType(row);
            if (isBlank(item.getServiceKey())) { continue; }
            if (serviceTypeMapper.selectTcmServiceTypeByKey(item.getServiceKey()) == null)
            {
                serviceTypeMapper.insertTcmServiceType(item);
            }
            else
            {
                serviceTypeMapper.updateTcmServiceType(item);
            }
            count++;
        }
        return count;
    }

    private int restorePriceLists(Object value)
    {
        int count = 0;
        for (Map<String, Object> row : asObjectList(value))
        {
            TcmPriceList item = toPriceList(row);
            if (isBlank(item.getId())) { continue; }
            if (priceListMapper.selectTcmPriceListById(item.getId()) == null)
            {
                priceListMapper.insertTcmPriceList(item);
            }
            else
            {
                priceListMapper.updateTcmPriceList(item);
            }
            count++;
        }
        return count;
    }

    private int restorePatients(Object value)
    {
        int count = 0;
        for (Map<String, Object> row : asObjectList(value))
        {
            TcmPatient item = PayloadUtils.toPatient(row);
            if (isBlank(item.getId())) { continue; }
            if (patientMapper.selectTcmPatientById(item.getId()) == null)
            {
                patientMapper.insertTcmPatient(item);
            }
            else
            {
                patientMapper.updateTcmPatient(item);
            }
            count++;
        }
        return count;
    }

    private int restoreConsultations(Object value)
    {
        int count = 0;
        for (Map<String, Object> row : asObjectList(value))
        {
            TcmConsultation item = PayloadUtils.toConsultation(row);
            if (isBlank(item.getId())) { continue; }
            if (consultationMapper.selectTcmConsultationById(item.getId()) == null)
            {
                consultationMapper.insertTcmConsultation(item);
            }
            else
            {
                consultationMapper.updateTcmConsultation(item);
            }
            count++;
        }
        return count;
    }

    private int restoreAppointments(Object value)
    {
        int count = 0;
        for (Map<String, Object> row : asObjectList(value))
        {
            TcmAppointment item = PayloadUtils.toAppointment(row);
            if (isBlank(item.getId())) { continue; }
            if (appointmentMapper.selectTcmAppointmentById(item.getId()) == null)
            {
                appointmentMapper.insertTcmAppointment(item);
            }
            else
            {
                appointmentMapper.updateTcmAppointment(item);
            }
            count++;
        }
        return count;
    }

    private int restorePatientFiles(Object value)
    {
        int count = 0;
        for (Map<String, Object> row : asObjectList(value))
        {
            TcmPatientFile item = toPatientFile(row);
            if (item.getId() == null && isBlank(item.getFilePath()))
            {
                continue;
            }
            TcmPatientFile existing = item.getId() != null
                    ? patientFileMapper.selectTcmPatientFileById(item.getId())
                    : null;
            if (existing == null && !isBlank(item.getFilePath()))
            {
                existing = patientFileMapper.selectTcmPatientFileByPath(item.getFilePath());
            }
            if (existing == null)
            {
                patientFileMapper.insertTcmPatientFile(item);
            }
            else
            {
                item.setId(existing.getId());
                patientFileMapper.updateTcmPatientFile(item);
            }
            count++;
        }
        return count;
    }

    private int restoreInventory(Object value)
    {
        int count = 0;
        for (Map<String, Object> row : asObjectList(value))
        {
            TcmInventoryItem item = PayloadUtils.toInventoryItem(row);
            if (isBlank(item.getId())) { continue; }
            if (inventoryItemMapper.selectTcmInventoryItemById(item.getId()) == null)
            {
                inventoryItemMapper.insertTcmInventoryItem(item);
            }
            else
            {
                inventoryItemMapper.updateTcmInventoryItem(item);
            }
            count++;
        }
        return count;
    }

    private int restoreBranches(Object value)
    {
        int count = 0;
        for (Map<String, Object> row : asObjectList(value))
        {
            TcmBranch item = PayloadUtils.toBranch(row);
            if (isBlank(item.getId())) { continue; }
            if (branchMapper.selectTcmBranchById(item.getId()) == null)
            {
                branchMapper.insertTcmBranch(item);
            }
            else
            {
                branchMapper.updateTcmBranch(item);
            }
            count++;
        }
        return count;
    }

    private int restoreSuppliers(Object value)
    {
        int count = 0;
        for (Map<String, Object> row : asObjectList(value))
        {
            TcmSupplier item = PayloadUtils.toSupplier(row);
            if (isBlank(item.getId())) { continue; }
            if (supplierMapper.selectTcmSupplierById(item.getId()) == null)
            {
                supplierMapper.insertTcmSupplier(item);
            }
            else
            {
                supplierMapper.updateTcmSupplier(item);
            }
            count++;
        }
        return count;
    }

    private int restoreAcupoints(Object value)
    {
        int count = 0;
        for (Map<String, Object> row : asObjectList(value))
        {
            TcmAcupoint item = PayloadUtils.toAcupoint(row);
            if (isBlank(item.getId())) { continue; }
            if (acupointMapper.selectTcmAcupointById(item.getId()) == null)
            {
                acupointMapper.insertTcmAcupoint(item);
            }
            else
            {
                acupointMapper.updateTcmAcupoint(item);
            }
            count++;
        }
        return count;
    }

    private int restoreHerbs(Object value)
    {
        int count = 0;
        for (Map<String, Object> row : asObjectList(value))
        {
            TcmHerbDict item = toHerb(row);
            if (isBlank(item.getId())) { continue; }
            if (herbDictMapper.selectTcmHerbDictById(item.getId()) == null)
            {
                herbDictMapper.insertTcmHerbDict(item);
            }
            else
            {
                herbDictMapper.updateTcmHerbDict(item);
            }
            count++;
        }
        return count;
    }

    private int restoreMeridians(Object value)
    {
        int count = 0;
        for (Map<String, Object> row : asObjectList(value))
        {
            TcmMeridian item = toMeridian(row);
            if (isBlank(item.getId())) { continue; }
            if (meridianMapper.selectTcmMeridianById(item.getId()) == null)
            {
                meridianMapper.insertTcmMeridian(item);
            }
            else
            {
                meridianMapper.updateTcmMeridian(item);
            }
            count++;
        }
        return count;
    }

    private int restoreTreatmentTemplates(Object value)
    {
        int count = 0;
        for (Map<String, Object> row : asObjectList(value))
        {
            TcmTreatmentTemplate item = toTreatmentTemplate(row);
            if (isBlank(item.getId())) { continue; }
            if (treatmentTemplateMapper.selectTcmTreatmentTemplateById(item.getId()) == null)
            {
                treatmentTemplateMapper.insertTcmTreatmentTemplate(item);
            }
            else
            {
                treatmentTemplateMapper.updateTcmTreatmentTemplate(item);
            }
            count++;
        }
        return count;
    }

    private int restoreFormulas(Object value)
    {
        int count = 0;
        for (Map<String, Object> row : asObjectList(value))
        {
            TcmFormula item = PayloadUtils.toFormula(row);
            if (isBlank(item.getId())) { continue; }
            if (formulaService.selectTcmFormulaById(item.getId()) == null)
            {
                formulaService.insertTcmFormula(item);
            }
            else
            {
                formulaService.updateTcmFormula(item);
            }
            count++;
        }
        return count;
    }

    private int restoreUnitConversions(Object value)
    {
        int count = 0;
        for (Map<String, Object> row : asObjectList(value))
        {
            TcmUnitConversion item = toUnitConversion(row);
            if (isBlank(item.getFromUnit()) || isBlank(item.getToUnit()) || item.getFactor() == null)
            {
                continue;
            }
            TcmUnitConversion existing = item.getId() != null
                    ? unitConversionMapper.selectTcmUnitConversionById(item.getId())
                    : null;
            if (existing == null)
            {
                existing = unitConversionMapper.selectByPair(item.getFromUnit(), item.getToUnit());
            }
            if (existing == null)
            {
                unitConversionMapper.insertTcmUnitConversion(item);
            }
            else
            {
                item.setId(existing.getId());
                unitConversionMapper.updateTcmUnitConversion(item);
            }
            count++;
        }
        return count;
    }

    private int restoreEmailLog(Object value)
    {
        int count = 0;
        for (Map<String, Object> row : asObjectList(value))
        {
            TcmEmailLog item = toEmailLog(row);
            if (item.getId() != null && emailLogMapper.selectTcmEmailLogById(item.getId()) != null)
            {
                emailLogMapper.updateTcmEmailLog(item);
            }
            else
            {
                emailLogMapper.insertTcmEmailLog(item);
            }
            count++;
        }
        return count;
    }

    private TcmRoom toRoom(Map<String, Object> row)
    {
        TcmRoom item = new TcmRoom();
        item.setId(text(row, "id"));
        item.setName(text(row, "name"));
        item.setBranchId(text(row, "branchId"));
        Object supportTags = row.get("supportTags");
        item.setSupportTags(supportTags instanceof Collection<?> ? JSON.toJSONString(supportTags) : text(row, "supportTags"));
        item.setIsActive(boolToInt(row.get("isActive"), 1));
        return item;
    }

    private TcmServiceType toServiceType(Map<String, Object> row)
    {
        TcmServiceType item = new TcmServiceType();
        item.setServiceKey(text(row, "serviceKey", "key"));
        item.setLabel(text(row, "label", "name"));
        item.setDuration(intValue(row.get("duration")));
        item.setPractitionerTime(text(row, "practitionerTime"));
        item.setRoomRequired(boolToInt(row.get("roomRequired"), 0));
        item.setDefaultPrice(bigDecimal(row.get("defaultPrice")));
        item.setRequiredTag(text(row, "requiredTag"));
        item.setPublicVisible(boolToInt(row.get("publicVisible"), 1));
        item.setTaxable(boolToInt(row.get("taxable"), 1));
        item.setPricingVisible(boolToInt(row.get("pricingVisible"), 1));
        return item;
    }

    private TcmPriceList toPriceList(Map<String, Object> row)
    {
        TcmPriceList item = new TcmPriceList();
        item.setId(text(row, "id"));
        item.setName(text(row, "name"));
        item.setEffectiveDate(text(row, "effectiveDate"));
        item.setIsActive(boolToInt(row.get("isActive"), 1));
        item.setItemsJson(row.get("items") != null ? JSON.toJSONString(row.get("items")) : text(row, "itemsJson"));
        return item;
    }

    private TcmUnitConversion toUnitConversion(Map<String, Object> row)
    {
        TcmUnitConversion item = new TcmUnitConversion();
        item.setId(longValue(row.get("id")));
        item.setFromUnit(text(row, "fromUnit"));
        item.setToUnit(text(row, "toUnit"));
        item.setFactor(bigDecimal(row.get("factor")));
        item.setNotes(text(row, "notes"));
        return item;
    }

    private TcmEmailLog toEmailLog(Map<String, Object> row)
    {
        TcmEmailLog item = new TcmEmailLog();
        item.setId(longValue(row.get("id")));
        item.setToEmail(text(row, "toEmail"));
        item.setSubject(text(row, "subject"));
        item.setEmailType(text(row, "emailType"));
        item.setBody(text(row, "body"));
        item.setSentAt(text(row, "sentAt"));
        item.setPayload(row.get("payload") instanceof Map<?, ?> || row.get("payload") instanceof Collection<?>
                ? JSON.toJSONString(row.get("payload"))
                : text(row, "payload"));
        return item;
    }

    private TcmPatientFile toPatientFile(Map<String, Object> row)
    {
        TcmPatientFile item = new TcmPatientFile();
        item.setId(longValue(row.get("id")));
        item.setPatientId(text(row, "patientId"));
        item.setConsultationId(text(row, "consultationId"));
        item.setFileType(text(row, "fileType", "type"));
        item.setFileName(text(row, "fileName", "name"));
        item.setFilePath(text(row, "filePath", "path", "url", "resource"));
        item.setUploadTime(text(row, "uploadTime", "createdAt"));
        return item;
    }

    private TcmHerbDict toHerb(Map<String, Object> row)
    {
        TcmHerbDict item = new TcmHerbDict();
        item.setId(text(row, "id"));
        item.setName(text(row, "name"));
        item.setAlias(text(row, "alias", "aliases"));
        item.setPinyin(text(row, "pinyin"));
        item.setLatinName(text(row, "latinName", "latin", "latin_name"));
        item.setCategory(text(row, "category"));
        item.setNature(text(row, "nature"));
        item.setTaste(text(row, "taste"));
        item.setToxicity(text(row, "toxicity"));
        item.setMeridianTropism(text(row, "meridianTropism"));
        item.setEfficacy(text(row, "efficacy"));
        item.setIndication(text(row, "indication"));
        item.setDosageRange(text(row, "dosageRange"));
        item.setContraindication(text(row, "contraindication"));
        item.setNotes(text(row, "notes"));
        item.setIsActive(boolToInt(row.get("isActive"), 1));
        item.setDeletedAt(text(row, "deletedAt"));
        return item;
    }

    private TcmMeridian toMeridian(Map<String, Object> row)
    {
        TcmMeridian item = new TcmMeridian();
        item.setId(text(row, "id"));
        item.setName(text(row, "name"));
        item.setEnglishName(text(row, "englishName"));
        item.setAbbr(text(row, "abbr"));
        item.setCategory(text(row, "category"));
        item.setOrgan(text(row, "organ"));
        item.setPathway(text(row, "pathway"));
        item.setAcupointCount(intValue(row.get("acupointCount")));
        item.setIndication(text(row, "indication"));
        item.setNotes(text(row, "notes"));
        item.setIsActive(boolToInt(row.get("isActive"), 1));
        item.setDeletedAt(text(row, "deletedAt"));
        return item;
    }

    private TcmTreatmentTemplate toTreatmentTemplate(Map<String, Object> row)
    {
        TcmTreatmentTemplate item = new TcmTreatmentTemplate();
        item.setId(text(row, "id"));
        item.setName(text(row, "name"));
        item.setDisease(text(row, "disease"));
        item.setCategory(text(row, "category"));
        item.setDescription(text(row, "description"));
        item.setAcupointsJson(row.get("acupoints") != null ? JSON.toJSONString(row.get("acupoints")) : text(row, "acupointsJson"));
        item.setFormulaIds(row.get("formulaIds") != null ? JSON.toJSONString(row.get("formulaIds")) : text(row, "formulaIds"));
        item.setAdvice(text(row, "advice"));
        item.setNotes(text(row, "notes"));
        item.setIsActive(boolToInt(row.get("isActive"), 1));
        item.setDeletedAt(text(row, "deletedAt"));
        return item;
    }

    private List<Map<String, Object>> flattenPatientFiles(List<TcmPatientFile> files)
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (files == null)
        {
            return rows;
        }
        for (TcmPatientFile file : files)
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", file.getId());
            row.put("patientId", file.getPatientId());
            row.put("consultationId", file.getConsultationId());
            row.put("fileType", file.getFileType());
            row.put("fileName", file.getFileName());
            row.put("filePath", file.getFilePath());
            row.put("uploadTime", file.getUploadTime());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> asServiceTypeRows(Object value)
    {
        if (value instanceof Collection<?>)
        {
            return asObjectList(value);
        }
        Map<String, Object> serviceTypeMap = asObjectMap(value);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Object> entry : serviceTypeMap.entrySet())
        {
            Map<String, Object> row = asObjectMap(entry.getValue());
            if (row.isEmpty())
            {
                continue;
            }
            if (!row.containsKey("serviceKey") && !row.containsKey("key"))
            {
                row.put("key", entry.getKey());
            }
            rows.add(row);
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asObjectList(Object value)
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (!(value instanceof Collection<?>))
        {
            return rows;
        }
        for (Object item : (Collection<?>) value)
        {
            Map<String, Object> map = asObjectMap(item);
            if (!map.isEmpty())
            {
                rows.add(map);
            }
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asObjectMap(Object value)
    {
        Map<String, Object> map = new LinkedHashMap<>();
        if (value instanceof JSONObject)
        {
            map.putAll((JSONObject) value);
        }
        else if (value instanceof Map<?, ?>)
        {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet())
            {
                if (entry.getKey() != null)
                {
                    map.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }
        return map;
    }

    private String text(Map<String, Object> row, String... keys)
    {
        for (String key : keys)
        {
            Object value = row.get(key);
            if (value != null)
            {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private Integer boolToInt(Object value, int defaultValue)
    {
        if (value instanceof Boolean)
        {
            return ((Boolean) value) ? 1 : 0;
        }
        if (value instanceof Number)
        {
            return ((Number) value).intValue() != 0 ? 1 : 0;
        }
        if (value != null)
        {
            String text = String.valueOf(value).trim();
            if ("true".equalsIgnoreCase(text) || "1".equals(text)) { return 1; }
            if ("false".equalsIgnoreCase(text) || "0".equals(text)) { return 0; }
        }
        return defaultValue;
    }

    private Integer intValue(Object value)
    {
        if (value instanceof Number)
        {
            return ((Number) value).intValue();
        }
        if (value != null)
        {
            try { return Integer.parseInt(String.valueOf(value)); }
            catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private Long longValue(Object value)
    {
        if (value instanceof Number)
        {
            return ((Number) value).longValue();
        }
        if (value != null)
        {
            try { return Long.parseLong(String.valueOf(value)); }
            catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private BigDecimal bigDecimal(Object value)
    {
        if (value instanceof BigDecimal)
        {
            return (BigDecimal) value;
        }
        if (value instanceof Number || value instanceof String)
        {
            try { return new BigDecimal(String.valueOf(value)); }
            catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
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
            map.put("organization", profile.getString("organization"));
            map.put("organizationNumber", profile.getString("organizationNumber"));
            map.put("homeAddress", profile.get("homeAddress"));
            map.put("workingHours", normalizeWorkingHours(profile.get("workingHours")));
            map.put("practitionerSortOrder", sanitizeInteger(profile.get("practitionerSortOrder")));
            map.put("serviceKeys", sanitizeStringList(profile.get("serviceKeys")));
            map.put("internshipDates", sanitizeDateList(profile.get("internshipDates")));
            map.put("internshipSessions", profile.get("internshipSessions"));
            map.put("color", profile.getString("color"));
            map.put("overlap1", sanitizeInteger(profile.get("overlap1")));
            map.put("overlap2", sanitizeInteger(profile.get("overlap2")));
            map.put("dripEnabled", profile.getBooleanValue("dripEnabled", true));
            map.put("signature", profile.get("signature"));
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
            item.put("latinName", herb.getLatinName());
            item.put("category", herb.getCategory());
            item.put("nature", herb.getNature());
            item.put("taste", herb.getTaste());
            item.put("toxicity", herb.getToxicity());
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

    private List<Map<String, Object>> flattenInventoryWithUsage(List<TcmInventoryItem> items)
    {
        List<Map<String, Object>> rows = PayloadUtils.flattenInventory(items);
        Map<String, BigDecimal> usageMap = inventoryService.calculateLast30DaysUsage(items);
        for (Map<String, Object> row : rows)
        {
            String inventoryId = row.get("id") != null ? String.valueOf(row.get("id")) : null;
            row.put("last30DaysUsage", usageMap.getOrDefault(inventoryId, BigDecimal.ZERO));
        }
        return rows;
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
        return normalizeRoleKeys(roleKeys);
    }

    private List<String> normalizeRoleKeys(List<String> rawRoleKeys)
    {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (rawRoleKeys != null)
        {
            for (String roleKey : rawRoleKeys)
            {
                String normalized = normalizeRoleKey(roleKey);
                if (!normalized.isEmpty())
                {
                    unique.add(normalized);
                }
            }
        }
        List<String> roleKeys = new ArrayList<>(unique);
        Collections.sort(roleKeys, (left, right) -> {
            int leftRank = roleRank(left);
            int rightRank = roleRank(right);
            if (leftRank != rightRank) return leftRank - rightRank;
            return left.compareTo(right);
        });
        return roleKeys;
    }

    private String normalizeRoleKey(String roleKey)
    {
        String normalized = roleKey == null ? "" : roleKey.trim().toLowerCase();
        return "doctor".equals(normalized) ? "practitioner" : normalized;
    }

    private int roleRank(String roleKey)
    {
        String normalized = normalizeRoleKey(roleKey);
        if ("admin".equals(normalized)) return 0;
        if ("practitioner".equals(normalized)) return 1;
        if ("cashier".equals(normalized)) return 2;
        if ("pharmacist".equals(normalized)) return 3;
        if ("apprentice".equals(normalized)) return 4;
        return 99;
    }
}
