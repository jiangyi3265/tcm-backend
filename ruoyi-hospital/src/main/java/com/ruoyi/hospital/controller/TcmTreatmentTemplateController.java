package com.ruoyi.hospital.controller;

import java.util.*;
import com.alibaba.fastjson2.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.hospital.domain.TcmTreatmentTemplate;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.hospital.service.ITcmTreatmentTemplateService;

@RestController
@RequestMapping("/api/templates")
public class TcmTreatmentTemplateController
{
    @Autowired
    private ITcmTreatmentTemplateService templateService;

    @Autowired
    private ITcmAuditLogService auditLogService;

    @PreAuthorize("@ss.hasRole('admin')")
    @GetMapping("")
    public List<Map<String, Object>> list() {
        List<TcmTreatmentTemplate> list = templateService.selectTcmTreatmentTemplateList(new TcmTreatmentTemplate());
        List<Map<String, Object>> result = new ArrayList<>();
        for (TcmTreatmentTemplate t : list) { result.add(flatten(t)); }
        return result;
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return flatten(templateService.selectTcmTreatmentTemplateById(id));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PostMapping("")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        TcmTreatmentTemplate t = fromMap(body);
        templateService.insertTcmTreatmentTemplate(t);
        TcmTreatmentTemplate created = templateService.selectTcmTreatmentTemplateById(t.getId());
        auditLogService.log("template", created.getId(), created.getName(),
                "CREATE", String.valueOf(SecurityUtils.getUserId()), "新增诊疗模板");
        return flatten(created);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        TcmTreatmentTemplate t = fromMap(body);
        t.setId(id);
        templateService.updateTcmTreatmentTemplate(t);
        TcmTreatmentTemplate updated = templateService.selectTcmTreatmentTemplateById(id);
        auditLogService.log("template", updated.getId(), updated.getName(),
                "UPDATE", String.valueOf(SecurityUtils.getUserId()), "更新诊疗模板");
        return flatten(updated);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PatchMapping("/{id}/delete")
    public Map<String, Object> softDelete(@PathVariable String id) {
        TcmTreatmentTemplate template = templateService.softDeleteTcmTreatmentTemplate(id);
        auditLogService.log("template", template.getId(), template.getName(),
                "SOFT_DELETE", String.valueOf(SecurityUtils.getUserId()), "逻辑删除诊疗模板");
        return flatten(template);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PatchMapping("/{id}/restore")
    public Map<String, Object> restore(@PathVariable String id) {
        TcmTreatmentTemplate template = templateService.restoreTcmTreatmentTemplate(id);
        auditLogService.log("template", template.getId(), template.getName(),
                "RESTORE", String.valueOf(SecurityUtils.getUserId()), "恢复诊疗模板");
        return flatten(template);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @DeleteMapping("/{id}")
    public Map<String, Object> hardDelete(@PathVariable String id) {
        TcmTreatmentTemplate template = templateService.selectTcmTreatmentTemplateById(id);
        templateService.hardDeleteTcmTreatmentTemplate(id);
        auditLogService.log("template", id, template != null ? template.getName() : id,
                "DELETE", String.valueOf(SecurityUtils.getUserId()), "物理删除诊疗模板");
        Map<String, Object> r = new HashMap<>();
        r.put("ok", true);
        return r;
    }

    private static Map<String, Object> flatten(TcmTreatmentTemplate t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("name", t.getName());
        m.put("disease", t.getDisease());
        m.put("category", t.getCategory());
        m.put("description", t.getDescription());
        m.put("acupoints", parseJsonArray(t.getAcupointsJson()));
        m.put("formulaIds", parseJsonArray(t.getFormulaIds()));
        m.put("advice", t.getAdvice());
        m.put("notes", t.getNotes());
        m.put("isActive", t.getIsActive() != null && t.getIsActive() == 1);
        m.put("deletedAt", t.getDeletedAt());
        return m;
    }

    @SuppressWarnings("unchecked")
    private static TcmTreatmentTemplate fromMap(Map<String, Object> m) {
        TcmTreatmentTemplate t = new TcmTreatmentTemplate();
        t.setId(str(m, "id"));
        t.setName(str(m, "name"));
        t.setDisease(str(m, "disease"));
        t.setCategory(str(m, "category"));
        t.setDescription(str(m, "description"));
        Object acu = m.get("acupoints");
        if (acu instanceof List) t.setAcupointsJson(JSON.toJSONString(acu));
        else if (acu instanceof String) t.setAcupointsJson((String) acu);
        Object fids = m.get("formulaIds");
        if (fids instanceof List) t.setFormulaIds(JSON.toJSONString(fids));
        else if (fids instanceof String) t.setFormulaIds((String) fids);
        t.setAdvice(str(m, "advice"));
        t.setNotes(str(m, "notes"));
        Object active = m.get("isActive");
        t.setIsActive(active instanceof Boolean ? ((Boolean) active ? 1 : 0) : 1);
        return t;
    }

    private static List<?> parseJsonArray(String json) {
        if (json != null && !json.isEmpty()) {
            try { return JSON.parseArray(json); } catch (Exception e) { /* ignore */ }
        }
        return new ArrayList<>();
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v != null ? String.valueOf(v) : null;
    }
}
