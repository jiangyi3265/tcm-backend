package com.ruoyi.hospital.controller;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmUnitConversion;
import com.ruoyi.hospital.service.ITcmUnitConversionService;

@RestController
@RequestMapping("/api/unit-conversions")
public class TcmUnitConversionController
{
    @Autowired
    private ITcmUnitConversionService conversionService;

    @PreAuthorize("@ss.hasRole('admin')")
    @GetMapping("")
    public List<Map<String, Object>> list()
    {
        List<TcmUnitConversion> all = conversionService.selectAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (TcmUnitConversion c : all)
        {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("fromUnit", c.getFromUnit());
            m.put("toUnit", c.getToUnit());
            m.put("factor", c.getFactor());
            m.put("notes", c.getNotes());
            result.add(m);
        }
        return result;
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PostMapping("")
    public Map<String, Object> create(@RequestBody Map<String, Object> body)
    {
        TcmUnitConversion c = new TcmUnitConversion();
        c.setFromUnit(validateRequiredLength(body.get("fromUnit"), "fromUnit", 20));
        c.setToUnit(validateRequiredLength(body.get("toUnit"), "toUnit", 20));
        if (body.get("factor") == null) {
            throw new ServiceException("factor is required");
        }
        c.setFactor(new BigDecimal(body.get("factor").toString()));
        c.setNotes(validateOptionalLength(body.get("notes"), "notes", 200));
        conversionService.insertTcmUnitConversion(c);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", c.getId());
        r.put("fromUnit", c.getFromUnit());
        r.put("toUnit", c.getToUnit());
        r.put("factor", c.getFactor());
        r.put("notes", c.getNotes());
        return r;
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id,
            @RequestBody Map<String, Object> body)
    {
        TcmUnitConversion c = new TcmUnitConversion();
        c.setId(id);
        if (body.containsKey("fromUnit")) c.setFromUnit(validateRequiredLength(body.get("fromUnit"), "fromUnit", 20));
        if (body.containsKey("toUnit")) c.setToUnit(validateRequiredLength(body.get("toUnit"), "toUnit", 20));
        if (body.containsKey("factor")) c.setFactor(new BigDecimal(body.get("factor").toString()));
        if (body.containsKey("notes")) c.setNotes(validateOptionalLength(body.get("notes"), "notes", 200));
        conversionService.updateTcmUnitConversion(c);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", id);
        r.put("fromUnit", c.getFromUnit());
        r.put("toUnit", c.getToUnit());
        r.put("factor", c.getFactor());
        r.put("notes", c.getNotes());
        return r;
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id)
    {
        conversionService.deleteTcmUnitConversionById(id);
        Map<String, Object> r = new HashMap<>();
        r.put("ok", true);
        return r;
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PostMapping("/convert")
    public Map<String, Object> convert(@RequestBody Map<String, Object> body)
    {
        String fromUnit = (String) body.get("fromUnit");
        String toUnit = (String) body.get("toUnit");
        if (body.get("value") == null) {
            throw new com.ruoyi.common.exception.ServiceException("value is required");
        }
        BigDecimal value = new BigDecimal(body.get("value").toString());
        BigDecimal result = conversionService.convert(fromUnit, toUnit, value);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("fromUnit", fromUnit);
        r.put("toUnit", toUnit);
        r.put("value", value);
        r.put("result", result);
        return r;
    }

    private String validateRequiredLength(Object value, String fieldName, int maxLength)
    {
        String trimmed = validateOptionalLength(value, fieldName, maxLength);
        if (trimmed == null || trimmed.isEmpty())
        {
            throw new ServiceException(fieldName + " is required");
        }
        return trimmed;
    }

    private String validateOptionalLength(Object value, String fieldName, int maxLength)
    {
        if (value == null)
        {
            return null;
        }
        String trimmed = String.valueOf(value).trim();
        if (trimmed.length() > maxLength)
        {
            throw new ServiceException(fieldName + " length must be <= " + maxLength);
        }
        return trimmed;
    }
}
