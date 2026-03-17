package com.ruoyi.hospital.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.hospital.domain.TcmAcupoint;
import com.ruoyi.hospital.service.ITcmAcupointService;
import com.ruoyi.hospital.utils.PayloadUtils;

@RestController
@RequestMapping("/api/acupoints")
public class TcmAcupointController
{
    @Autowired
    private ITcmAcupointService acupointService;

    @GetMapping("")
    public List<Map<String, Object>> list()
    {
        return PayloadUtils.flattenAcupoints(
                acupointService.selectTcmAcupointList(new TcmAcupoint()));
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id)
    {
        return PayloadUtils.flattenAcupoint(
                acupointService.selectTcmAcupointById(id));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PostMapping("")
    public Map<String, Object> create(@RequestBody Map<String, Object> body)
    {
        TcmAcupoint acupoint = PayloadUtils.toAcupoint(body);
        acupointService.insertTcmAcupoint(acupoint);
        return PayloadUtils.flattenAcupoint(
                acupointService.selectTcmAcupointById(acupoint.getId()));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id,
            @RequestBody Map<String, Object> body)
    {
        TcmAcupoint acupoint = PayloadUtils.toAcupoint(body);
        acupoint.setId(id);
        acupointService.updateTcmAcupoint(acupoint);
        return PayloadUtils.flattenAcupoint(
                acupointService.selectTcmAcupointById(id));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PatchMapping("/{id}/delete")
    public Map<String, Object> softDelete(@PathVariable String id)
    {
        return PayloadUtils.flattenAcupoint(
                acupointService.softDeleteTcmAcupoint(id));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PatchMapping("/{id}/restore")
    public Map<String, Object> restore(@PathVariable String id)
    {
        return PayloadUtils.flattenAcupoint(
                acupointService.restoreTcmAcupoint(id));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @DeleteMapping("/{id}")
    public Map<String, Object> hardDelete(@PathVariable String id)
    {
        acupointService.hardDeleteTcmAcupoint(id);
        Map<String, Object> r = new HashMap<>();
        r.put("ok", true);
        return r;
    }
}
