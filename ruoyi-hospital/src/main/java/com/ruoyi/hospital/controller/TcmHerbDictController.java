package com.ruoyi.hospital.controller;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmHerbDict;
import com.ruoyi.hospital.service.ITcmHerbDictService;

@RestController
@RequestMapping("/api/herb-dict")
public class TcmHerbDictController
{
    @Autowired
    private ITcmHerbDictService herbDictService;

    @GetMapping("")
    public List<Map<String, Object>> list() {
        List<TcmHerbDict> list = herbDictService.selectTcmHerbDictList(new TcmHerbDict());
        List<Map<String, Object>> result = new ArrayList<>();
        for (TcmHerbDict h : list) { result.add(flatten(h)); }
        return result;
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return flatten(herbDictService.selectTcmHerbDictById(id));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PostMapping("")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        TcmHerbDict h = fromMap(body);
        herbDictService.insertTcmHerbDict(h);
        return flatten(herbDictService.selectTcmHerbDictById(h.getId()));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        TcmHerbDict h = fromMap(body);
        h.setId(id);
        herbDictService.updateTcmHerbDict(h);
        return flatten(herbDictService.selectTcmHerbDictById(id));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PatchMapping("/{id}/delete")
    public Map<String, Object> softDelete(@PathVariable String id) {
        return flatten(herbDictService.softDeleteTcmHerbDict(id));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PatchMapping("/{id}/restore")
    public Map<String, Object> restore(@PathVariable String id) {
        return flatten(herbDictService.restoreTcmHerbDict(id));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @DeleteMapping("/{id}")
    public Map<String, Object> hardDelete(@PathVariable String id) {
        herbDictService.hardDeleteTcmHerbDict(id);
        Map<String, Object> r = new HashMap<>();
        r.put("ok", true);
        return r;
    }

    private static Map<String, Object> flatten(TcmHerbDict h) {
        if (h == null) {
            throw new ServiceException("药材不存在");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", h.getId());
        m.put("name", h.getName());
        m.put("alias", h.getAlias());
        m.put("pinyin", h.getPinyin());
        m.put("category", h.getCategory());
        m.put("nature", h.getNature());
        m.put("taste", h.getTaste());
        m.put("toxicity", h.getToxicity());
        m.put("meridianTropism", h.getMeridianTropism());
        m.put("efficacy", h.getEfficacy());
        m.put("indication", h.getIndication());
        m.put("dosageRange", h.getDosageRange());
        m.put("contraindication", h.getContraindication());
        m.put("notes", h.getNotes());
        m.put("isActive", h.getIsActive() != null && h.getIsActive() == 1);
        m.put("deletedAt", h.getDeletedAt());
        return m;
    }

    private static TcmHerbDict fromMap(Map<String, Object> m) {
        TcmHerbDict h = new TcmHerbDict();
        h.setId(str(m, "id"));
        h.setName(str(m, "name"));
        h.setAlias(str(m, "alias"));
        h.setPinyin(str(m, "pinyin"));
        h.setCategory(str(m, "category"));
        h.setNature(str(m, "nature"));
        h.setTaste(str(m, "taste"));
        h.setToxicity(str(m, "toxicity"));
        h.setMeridianTropism(str(m, "meridianTropism"));
        h.setEfficacy(str(m, "efficacy"));
        h.setIndication(str(m, "indication"));
        h.setDosageRange(str(m, "dosageRange"));
        h.setContraindication(str(m, "contraindication"));
        h.setNotes(str(m, "notes"));
        Object active = m.get("isActive");
        h.setIsActive(active instanceof Boolean ? ((Boolean) active ? 1 : 0) : 1);
        return h;
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v != null ? String.valueOf(v) : null;
    }
}
