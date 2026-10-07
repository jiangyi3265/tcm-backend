package com.ruoyi.hospital.controller;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.hospital.service.impl.TcmInvoiceImportService;

@RestController
@RequestMapping("/api/inventory/invoices")
@PreAuthorize("@ss.hasRole('admin')")
public class TcmInvoiceImportController
{
    @Autowired private TcmInvoiceImportService invoiceService;
    @Autowired private ITcmAuditLogService auditLogService;

    @PostMapping("/recognize")
    public Map<String, Object> recognize(@RequestParam("file") MultipartFile file) { return invoiceService.recognize(file); }

    @PostMapping("/confirm")
    public Map<String, Object> confirm(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = invoiceService.confirm(body);
        if (!Boolean.TRUE.equals(result.get("alreadyImported"))) auditLogService.log("inventory", String.valueOf(result.get("invoiceId")), "供应商发票", "IMPORT", String.valueOf(SecurityUtils.getUserId()), "确认发票入库");
        return result;
    }
}
