package com.ruoyi.hospital.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.hospital.domain.TcmSupplier;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.hospital.service.ITcmSupplierService;
import com.ruoyi.hospital.utils.PayloadUtils;

@RestController
@RequestMapping("/api/suppliers")
public class TcmSupplierController
{
    @Autowired
    private ITcmSupplierService supplierService;

    @Autowired
    private ITcmAuditLogService auditLogService;

    @PreAuthorize("@ss.hasRole('admin')")
    @GetMapping("")
    public List<Map<String, Object>> list()
    {
        return PayloadUtils.flattenSuppliers(
                supplierService.selectTcmSupplierList(new TcmSupplier()));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id)
    {
        return PayloadUtils.flatten(
                supplierService.selectTcmSupplierById(id));
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PostMapping("")
    public Map<String, Object> create(@RequestBody Map<String, Object> body)
    {
        TcmSupplier supplier = PayloadUtils.toSupplier(body);
        supplierService.insertTcmSupplier(supplier);
        TcmSupplier created = supplierService.selectTcmSupplierById(supplier.getId());
        auditLogService.log("supplier", created.getId(), created.getName(),
                "CREATE", String.valueOf(SecurityUtils.getUserId()), "新增供应商");
        return PayloadUtils.flatten(created);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id,
            @RequestBody Map<String, Object> body)
    {
        TcmSupplier supplier = PayloadUtils.toSupplier(body);
        supplier.setId(id);
        supplierService.updateTcmSupplier(supplier);
        TcmSupplier updated = supplierService.selectTcmSupplierById(id);
        auditLogService.log("supplier", updated.getId(), updated.getName(),
                "UPDATE", String.valueOf(SecurityUtils.getUserId()), "更新供应商");
        return PayloadUtils.flatten(updated);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PatchMapping("/{id}/delete")
    public Map<String, Object> softDelete(@PathVariable String id)
    {
        TcmSupplier supplier = supplierService.softDeleteTcmSupplier(id);
        auditLogService.log("supplier", supplier.getId(), supplier.getName(),
                "SOFT_DELETE", String.valueOf(SecurityUtils.getUserId()), "逻辑删除供应商");
        return PayloadUtils.flatten(supplier);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PatchMapping("/{id}/restore")
    public Map<String, Object> restore(@PathVariable String id)
    {
        TcmSupplier supplier = supplierService.restoreTcmSupplier(id);
        auditLogService.log("supplier", supplier.getId(), supplier.getName(),
                "RESTORE", String.valueOf(SecurityUtils.getUserId()), "恢复供应商");
        return PayloadUtils.flatten(supplier);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @DeleteMapping("/{id}")
    public Map<String, Object> hardDelete(@PathVariable String id)
    {
        TcmSupplier supplier = supplierService.selectTcmSupplierById(id);
        supplierService.hardDeleteTcmSupplier(id);
        auditLogService.log("supplier", id, supplier != null ? supplier.getName() : id,
                "DELETE", String.valueOf(SecurityUtils.getUserId()), "物理删除供应商");
        Map<String, Object> r = new HashMap<>();
        r.put("ok", true);
        return r;
    }
}
