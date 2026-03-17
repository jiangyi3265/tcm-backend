package com.ruoyi.hospital.controller;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.hospital.domain.TcmMeridian;
import com.ruoyi.hospital.service.ITcmMeridianService;

@RestController
@RequestMapping("/api/meridians")
public class TcmMeridianController
{
    @Autowired
    private ITcmMeridianService meridianService;

    @GetMapping("")
    public List<Map<String, Object>> list() {
        List<TcmMeridian> list = meridianService.selectTcmMeridianList(new TcmMeridian());
        List<Map<String, Object>> result = new ArrayList<>();
        for (TcmMeridian m : list) { result.add(flatten(m)); }
        return result;
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return flatten(meridianService.selectTcmMeridianById(id));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PostMapping("")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        TcmMeridian m = fromMap(body);
        meridianService.insertTcmMeridian(m);
        return flatten(meridianService.selectTcmMeridianById(m.getId()));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        TcmMeridian m = fromMap(body);
        m.setId(id);
        meridianService.updateTcmMeridian(m);
        return flatten(meridianService.selectTcmMeridianById(id));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PatchMapping("/{id}/delete")
    public Map<String, Object> softDelete(@PathVariable String id) {
        return flatten(meridianService.softDeleteTcmMeridian(id));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PatchMapping("/{id}/restore")
    public Map<String, Object> restore(@PathVariable String id) {
        return flatten(meridianService.restoreTcmMeridian(id));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @DeleteMapping("/{id}")
    public Map<String, Object> hardDelete(@PathVariable String id) {
        meridianService.hardDeleteTcmMeridian(id);
        Map<String, Object> r = new HashMap<>();
        r.put("ok", true);
        return r;
    }

    private static Map<String, Object> flatten(TcmMeridian m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", m.getId());
        map.put("name", m.getName());
        map.put("englishName", m.getEnglishName());
        map.put("abbr", m.getAbbr());
        map.put("category", m.getCategory());
        map.put("organ", m.getOrgan());
        map.put("pathway", m.getPathway());
        map.put("acupointCount", m.getAcupointCount());
        map.put("indication", m.getIndication());
        map.put("notes", m.getNotes());
        map.put("isActive", m.getIsActive() != null && m.getIsActive() == 1);
        map.put("deletedAt", m.getDeletedAt());
        return map;
    }

    private static TcmMeridian fromMap(Map<String, Object> m) {
        TcmMeridian mer = new TcmMeridian();
        mer.setId(str(m, "id"));
        mer.setName(str(m, "name"));
        mer.setEnglishName(str(m, "englishName"));
        mer.setAbbr(str(m, "abbr"));
        mer.setCategory(str(m, "category"));
        mer.setOrgan(str(m, "organ"));
        mer.setPathway(str(m, "pathway"));
        if (m.get("acupointCount") instanceof Number)
            mer.setAcupointCount(((Number) m.get("acupointCount")).intValue());
        mer.setIndication(str(m, "indication"));
        mer.setNotes(str(m, "notes"));
        Object active = m.get("isActive");
        mer.setIsActive(active instanceof Boolean ? ((Boolean) active ? 1 : 0) : 1);
        return mer;
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v != null ? String.valueOf(v) : null;
    }
}
