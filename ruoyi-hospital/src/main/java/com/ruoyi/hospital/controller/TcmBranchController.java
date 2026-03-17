package com.ruoyi.hospital.controller;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.hospital.domain.TcmBranch;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.hospital.service.ITcmBranchService;
import com.ruoyi.hospital.utils.PayloadUtils;

@RestController
@RequestMapping("/api/branches")
public class TcmBranchController
{
    @Autowired
    private ITcmBranchService branchService;

    @Autowired
    private ITcmAuditLogService auditLogService;

    /** 分店列表 — 所有已登录用户可查（与 bootstrap 保持一致） */
    @GetMapping("")
    public List<Map<String, Object>> list()
    {
        return PayloadUtils.flattenBranches(
                branchService.selectTcmBranchList(new TcmBranch()));
    }

    @PreAuthorize("@ss.hasPermi('tcm:branch:add')")
    @PostMapping("")
    public Map<String, Object> create(@RequestBody Map<String, Object> body)
    {
        TcmBranch branch = PayloadUtils.toBranch(body);
        branchService.insertTcmBranch(branch);
        TcmBranch created = branchService.selectTcmBranchById(branch.getId());
        auditLogService.log("branch", created.getId(), created.getName(),
                "CREATE", String.valueOf(SecurityUtils.getUserId()), "新建分店");
        return PayloadUtils.flatten(created);
    }

    @PreAuthorize("@ss.hasPermi('tcm:branch:edit')")
    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable String id,
            @RequestBody Map<String, Object> body)
    {
        TcmBranch branch = PayloadUtils.toBranch(body);
        branch.setId(id);
        branchService.updateTcmBranch(branch);
        TcmBranch updated = branchService.selectTcmBranchById(id);
        auditLogService.log("branch", updated.getId(), updated.getName(),
                "UPDATE", String.valueOf(SecurityUtils.getUserId()), "更新分店信息");
        return PayloadUtils.flatten(updated);
    }

    @PreAuthorize("@ss.hasPermi('tcm:branch:toggle')")
    @PatchMapping("/{id}/toggle")
    public Map<String, Object> toggle(@PathVariable String id)
    {
        TcmBranch branch = branchService.toggleBranch(id);
        auditLogService.log("branch", branch.getId(), branch.getName(),
                "TOGGLE", String.valueOf(SecurityUtils.getUserId()),
                "切换分店状态为: " + (branch.getIsActive() != null && branch.getIsActive() == 1 ? "启用" : "停用"));
        return PayloadUtils.flatten(branch);
    }

    @PreAuthorize("@ss.hasPermi('tcm:branch:remove')")
    @PatchMapping("/{id}/delete")
    public Map<String, Object> softDelete(@PathVariable String id)
    {
        TcmBranch branch = branchService.softDeleteBranch(id);
        auditLogService.log("branch", branch.getId(), branch.getName(),
                "SOFT_DELETE", String.valueOf(SecurityUtils.getUserId()), "逻辑删除分店");
        return PayloadUtils.flatten(branch);
    }
}
