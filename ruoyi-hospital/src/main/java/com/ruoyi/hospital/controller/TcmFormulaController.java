package com.ruoyi.hospital.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.hospital.domain.TcmFormula;
import com.ruoyi.hospital.service.ITcmFormulaService;
import com.ruoyi.hospital.utils.PayloadUtils;

@RestController
@RequestMapping("/api/formulas")
public class TcmFormulaController
{
    @Autowired
    private ITcmFormulaService formulaService;

    @PreAuthorize("@ss.hasAnyRole('admin,practitioner')")
    @GetMapping("")
    public List<Map<String, Object>> list()
    {
        return PayloadUtils.flattenFormulas(
                formulaService.selectTcmFormulaList(new TcmFormula()));
    }

    @PreAuthorize("@ss.hasAnyRole('admin,practitioner')")
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id)
    {
        return PayloadUtils.flattenFormula(
                formulaService.selectTcmFormulaById(id));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PostMapping("")
    public Map<String, Object> create(@RequestBody Map<String, Object> body)
    {
        TcmFormula formula = PayloadUtils.toFormula(body);
        formulaService.insertTcmFormula(formula);
        return PayloadUtils.flattenFormula(
                formulaService.selectTcmFormulaById(formula.getId()));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id,
            @RequestBody Map<String, Object> body)
    {
        TcmFormula formula = PayloadUtils.toFormula(body);
        formula.setId(id);
        formulaService.updateTcmFormula(formula);
        return PayloadUtils.flattenFormula(
                formulaService.selectTcmFormulaById(id));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PatchMapping("/{id}/delete")
    public Map<String, Object> softDelete(@PathVariable String id)
    {
        return PayloadUtils.flattenFormula(
                formulaService.softDeleteTcmFormula(id));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PatchMapping("/{id}/restore")
    public Map<String, Object> restore(@PathVariable String id)
    {
        return PayloadUtils.flattenFormula(
                formulaService.restoreTcmFormula(id));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @DeleteMapping("/{id}")
    public Map<String, Object> hardDelete(@PathVariable String id)
    {
        formulaService.hardDeleteTcmFormula(id);
        Map<String, Object> r = new HashMap<>();
        r.put("ok", true);
        return r;
    }
}
