package com.ruoyi.hospital.controller;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmConsultationMod;
import com.ruoyi.hospital.service.ITcmConsultationModService;
import com.ruoyi.hospital.service.ITcmConsultationService;
import com.ruoyi.system.service.ISysUserService;

@RestController
@RequestMapping("/api/audit-logs")
public class TcmAuditLogController
{
    @Autowired
    private ITcmConsultationModService consultationModService;

    @Autowired
    private ITcmConsultationService consultationService;

    @Autowired
    private ISysUserService sysUserService;

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @PreAuthorize("@ss.hasRole('admin')")
    @GetMapping("")
    public List<Map<String, Object>> list(@RequestParam(required = false) String targetType,
            @RequestParam(defaultValue = "200") Integer limit)
    {
        return buildAuditLogs(targetType, null, limit);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @GetMapping("/recent")
    public List<Map<String, Object>> recent(@RequestParam(defaultValue = "7") Integer days,
            @RequestParam(required = false) String targetType)
    {
        return buildAuditLogs(targetType, days, 500);
    }

    private List<Map<String, Object>> buildAuditLogs(String targetType, Integer days, Integer limit)
    {
        Map<String, TcmConsultation> consultationMap = new HashMap<>();
        for (TcmConsultation consultation : consultationService.selectTcmConsultationList(new TcmConsultation()))
        {
            consultationMap.put(consultation.getConsultationId(), consultation);
        }

        List<TcmConsultationMod> mods = consultationModService.selectTcmConsultationModList(new TcmConsultationMod());
        List<Map<String, Object>> result = new ArrayList<>();
        LocalDateTime cutoff = days != null ? LocalDateTime.now().minusDays(days.longValue()) : null;

        for (TcmConsultationMod mod : mods)
        {
            LocalDateTime createdAt = parseDateTime(mod.getModDate(), mod.getCreateTime());
            if (cutoff != null && createdAt != null && createdAt.isBefore(cutoff))
            {
                continue;
            }

            JSONObject payload = parsePayload(mod.getChanges());
            TcmConsultation consultation = consultationMap.get(mod.getConsultationId());
            String resolvedTargetType = resolveTargetType(mod, payload, consultation);
            if (targetType != null && !targetType.isEmpty() && !targetType.equals(resolvedTargetType))
            {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", mod.getId());
            item.put("action", mapAction(mod, resolvedTargetType));
            item.put("targetType", resolvedTargetType);
            item.put("targetName", resolveTargetName(mod, payload, consultation));
            item.put("targetId", resolveTargetId(mod, payload, consultation));
            item.put("details", resolveDetails(mod, payload));
            item.put("createdAt", createdAt != null
                    ? createdAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    : null);

            SysUser user = resolveUser(mod.getUserId());
            if (user != null)
            {
                item.put("userName", user.getNickName());
                item.put("userId", String.valueOf(user.getUserId()));
                item.put("userRole", user.getRoles() != null && !user.getRoles().isEmpty()
                        ? user.getRoles().get(0).getRoleKey()
                        : null);
            }
            else
            {
                applyFallbackUser(item, mod.getUserId());
            }
            result.add(item);
        }

        result.sort(Comparator.comparing(
                item -> item.get("createdAt") != null ? String.valueOf(item.get("createdAt")) : "",
                Comparator.reverseOrder()));

        int safeLimit = limit != null && limit > 0 ? limit : result.size();
        return result.size() > safeLimit ? new ArrayList<>(result.subList(0, safeLimit)) : result;
    }

    private void applyFallbackUser(Map<String, Object> item, String userId)
    {
        if (userId == null || userId.isEmpty())
        {
            item.put("userName", "系统");
            item.put("userId", null);
            item.put("userRole", "system");
            return;
        }
        if (userId.startsWith("public:"))
        {
            item.put("userName", "公开签署");
            item.put("userId", userId);
            item.put("userRole", "public");
            return;
        }
        item.put("userName", userId);
        item.put("userId", userId);
        item.put("userRole", null);
    }

    private SysUser resolveUser(String userId)
    {
        if (userId == null || userId.isEmpty())
        {
            return null;
        }
        try
        {
            return sysUserService.selectUserById(Long.valueOf(userId));
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private String mapAction(TcmConsultationMod mod, String targetType)
    {
        if (!"consultation".equals(targetType))
        {
            return mod.getAction();
        }
        String modType = mod.getModType();
        String action = mod.getAction() != null ? mod.getAction().toLowerCase() : "";
        if ("payment".equals(modType) || action.contains("paid"))
        {
            return "PAID";
        }
        if ("dispense".equals(modType) || action.contains("dispens"))
        {
            return "DISPENSE";
        }
        if ("edit".equals(modType))
        {
            return "UPDATE";
        }
        if ("complete".equals(modType) || action.contains("complete"))
        {
            return "COMPLETE";
        }
        return "UPDATE";
    }

    private String resolveTargetType(TcmConsultationMod mod, JSONObject payload, TcmConsultation consultation)
    {
        String payloadType = payload.getString("targetType");
        if (payloadType != null && !payloadType.isEmpty())
        {
            return payloadType;
        }
        if (consultation != null)
        {
            return "consultation";
        }
        String modType = mod.getModType();
        if ("patient".equals(modType) || "inventory".equals(modType) || "file".equals(modType))
        {
            return modType;
        }
        return "consultation";
    }

    private Object resolveTargetId(TcmConsultationMod mod, JSONObject payload, TcmConsultation consultation)
    {
        String payloadTargetId = payload.getString("targetId");
        if (payloadTargetId != null && !payloadTargetId.isEmpty())
        {
            return payloadTargetId;
        }
        if (consultation != null)
        {
            return consultation.getId();
        }
        return mod.getConsultationId();
    }

    private String resolveTargetName(TcmConsultationMod mod, JSONObject payload, TcmConsultation consultation)
    {
        String payloadTargetName = payload.getString("targetName");
        if (payloadTargetName != null && !payloadTargetName.isEmpty())
        {
            return payloadTargetName;
        }
        if (consultation != null)
        {
            return consultation.getConsultationId();
        }
        return mod.getConsultationId();
    }

    private String resolveDetails(TcmConsultationMod mod, JSONObject payload)
    {
        String payloadDetails = payload.getString("details");
        if (payloadDetails != null && !payloadDetails.isEmpty())
        {
            return payloadDetails;
        }
        return mod.getChanges() != null && !mod.getChanges().isEmpty() ? mod.getChanges() : mod.getAction();
    }

    private JSONObject parsePayload(String text)
    {
        if (text == null || text.isEmpty())
        {
            return new JSONObject();
        }
        try
        {
            return JSON.parseObject(text);
        }
        catch (Exception e)
        {
            return new JSONObject();
        }
    }

    private LocalDateTime parseDateTime(String modDate, java.util.Date createTime)
    {
        if (modDate != null && !modDate.isEmpty())
        {
            try
            {
                return LocalDateTime.parse(modDate, DATETIME_FORMATTER);
            }
            catch (Exception e)
            {
                // ignore
            }
        }
        if (createTime != null)
        {
            return LocalDateTime.ofInstant(createTime.toInstant(), ZoneId.systemDefault());
        }
        return null;
    }
}
