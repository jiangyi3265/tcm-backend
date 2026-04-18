package com.ruoyi.hospital.controller;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.hospital.domain.TcmPriceList;
import com.ruoyi.hospital.domain.TcmRoom;
import com.ruoyi.hospital.domain.TcmServiceType;
import com.ruoyi.hospital.service.*;
import com.ruoyi.hospital.utils.PayloadUtils;

@RestController
@RequestMapping("/api/settings")
public class TcmSettingsController
{
    @Autowired
    private ITcmSettingsService settingsService;
    @Autowired
    private ITcmRoomService roomService;
    @Autowired
    private ITcmServiceTypeService serviceTypeService;
    @Autowired
    private ITcmPriceListService priceListService;

    @Autowired
    private ITcmAuditLogService auditLogService;

    @PreAuthorize("@ss.hasRole('admin')")
    @GetMapping("")
    public Map<String, Object> getBundle()
    {
        return settingsService.getBundle();
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PutMapping("/base")
    public Map<String, Object> updateBase(@RequestBody Map<String, Object> data)
    {
        Map<String, Object> updated = settingsService.updateBaseSettings(data);
        auditLogService.log("settings", "base", "基础设置",
                "UPDATE", String.valueOf(SecurityUtils.getUserId()), "更新基础设置");
        return updated;
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PostMapping("/rooms")
    public Map<String, Object> addRoom(@RequestBody Map<String, Object> body)
    {
        TcmRoom room = new TcmRoom();
        room.setId((String) body.get("id"));
        room.setName((String) body.get("name"));
        room.setBranchId((String) body.get("branchId"));
        if (body.containsKey("supportTags"))
        {
            room.setSupportTags(serializeTags(body.get("supportTags")));
        }
        Object isActive = body.get("isActive");
        if (isActive instanceof Boolean)
        {
            room.setIsActive(((Boolean) isActive) ? 1 : 0);
        }
        else
        {
            room.setIsActive(1);
        }
        if (body.containsKey("color"))
        {
            room.setRemark(packRoomExtras(null, body.get("color")));
        }
        roomService.insertTcmRoom(room);
        TcmRoom created = roomService.selectTcmRoomById(room.getId());
        auditLogService.log("settings", created.getId(), created.getName(),
                "CREATE", String.valueOf(SecurityUtils.getUserId()), "新增诊室");
        return flattenRoom(created);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PutMapping("/rooms/{id}")
    public Map<String, Object> updateRoom(@PathVariable String id,
            @RequestBody Map<String, Object> body)
    {
        TcmRoom room = new TcmRoom();
        room.setId(id);
        if (body.containsKey("name")) { room.setName((String) body.get("name")); }
        if (body.containsKey("branchId")) { room.setBranchId((String) body.get("branchId")); }
        if (body.containsKey("supportTags"))
        {
            room.setSupportTags(serializeTags(body.get("supportTags")));
        }
        if (body.containsKey("isActive"))
        {
            Object isActive = body.get("isActive");
            if (isActive instanceof Boolean)
            {
                room.setIsActive(((Boolean) isActive) ? 1 : 0);
            }
            else if (isActive instanceof Number)
            {
                room.setIsActive(((Number) isActive).intValue());
            }
        }
        if (body.containsKey("color"))
        {
            TcmRoom existing = roomService.selectTcmRoomById(id);
            String currentRemark = existing != null ? existing.getRemark() : null;
            room.setRemark(packRoomExtras(currentRemark, body.get("color")));
        }
        roomService.updateTcmRoom(room);
        TcmRoom updated = roomService.selectTcmRoomById(id);
        auditLogService.log("settings", updated.getId(), updated.getName(),
                "UPDATE", String.valueOf(SecurityUtils.getUserId()), "更新诊室");
        return flattenRoom(updated);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PutMapping("/service-types/{key}")
    public Map<String, Object> updateServiceType(@PathVariable String key,
            @RequestBody Map<String, Object> body)
    {
        TcmServiceType type = new TcmServiceType();
        type.setServiceKey(key);
        if (body.containsKey("label")) { type.setLabel((String) body.get("label")); }
        if (body.containsKey("duration") && body.get("duration") instanceof Number)
        {
            type.setDuration(((Number) body.get("duration")).intValue());
        }
        if (body.containsKey("practitionerTime") && body.get("practitionerTime") != null)
        {
            Object pt = body.get("practitionerTime");
            if (pt instanceof Number)
            {
                type.setPractitionerTime(String.valueOf(((Number) pt).intValue()));
            }
            else
            {
                type.setPractitionerTime(String.valueOf(pt));
            }
        }
        if (body.containsKey("roomRequired"))
        {
            Object rr = body.get("roomRequired");
            if (rr instanceof Boolean) { type.setRoomRequired(((Boolean) rr) ? 1 : 0); }
            else if (rr instanceof Number) { type.setRoomRequired(((Number) rr).intValue()); }
        }
        if (body.containsKey("defaultPrice") && body.get("defaultPrice") != null)
        {
            type.setDefaultPrice(new java.math.BigDecimal(String.valueOf(body.get("defaultPrice"))));
        }
        if (body.containsKey("requiredTag"))
        {
            type.setRequiredTag(normalizeOptionalString(body.get("requiredTag")));
        }
        if (body.containsKey("publicVisible"))
        {
            Object pv = body.get("publicVisible");
            if (pv instanceof Boolean) { type.setPublicVisible(((Boolean) pv) ? 1 : 0); }
            else if (pv instanceof Number) { type.setPublicVisible(((Number) pv).intValue()); }
        }
        serviceTypeService.updateServiceType(type);
        TcmServiceType updated = serviceTypeService.selectByKey(key);
        if (updated == null)
        {
            throw new ServiceException("服务类型不存在");
        }
        auditLogService.log("settings", key, updated != null ? updated.getLabel() : key,
                "UPDATE", String.valueOf(SecurityUtils.getUserId()), "更新服务类型");
        return PayloadUtils.flattenServiceType(updated);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PostMapping("/price-lists")
    public Map<String, Object> addPriceList(@RequestBody Map<String, Object> body)
    {
        TcmPriceList pl = new TcmPriceList();
        pl.setName((String) body.get("name"));
        pl.setEffectiveDate((String) body.get("effectiveDate"));
        pl.setIsActive(1);
        Object items = body.get("items");
        if (items != null) { pl.setItemsJson(JSON.toJSONString(items)); }
        priceListService.insertTcmPriceList(pl);
        TcmPriceList created = priceListService.selectTcmPriceListById(pl.getId());
        auditLogService.log("settings", created.getId(), created.getName(),
                "CREATE", String.valueOf(SecurityUtils.getUserId()), "新增价格表");
        return PayloadUtils.flatten(created);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @PutMapping("/price-lists/{id}")
    public Map<String, Object> updatePriceList(@PathVariable String id,
            @RequestBody Map<String, Object> body)
    {
        TcmPriceList pl = new TcmPriceList();
        pl.setId(id);
        if (body.containsKey("name")) { pl.setName((String) body.get("name")); }
        if (body.containsKey("effectiveDate")) { pl.setEffectiveDate((String) body.get("effectiveDate")); }
        Object items = body.get("items");
        if (items != null) { pl.setItemsJson(JSON.toJSONString(items)); }
        if (body.containsKey("isActive"))
        {
            Object isActive = body.get("isActive");
            if (isActive instanceof Boolean) { pl.setIsActive(((Boolean) isActive) ? 1 : 0); }
            else if (isActive instanceof Number) { pl.setIsActive(((Number) isActive).intValue()); }
        }
        priceListService.updateTcmPriceList(pl);
        TcmPriceList updated = priceListService.selectTcmPriceListById(id);
        auditLogService.log("settings", updated.getId(), updated.getName(),
                "UPDATE", String.valueOf(SecurityUtils.getUserId()), "更新价格表");
        return PayloadUtils.flatten(updated);
    }

    @PreAuthorize("@ss.hasRole('admin')")
    @DeleteMapping("/price-lists/{id}")
    public Map<String, Object> deletePriceList(@PathVariable String id)
    {
        TcmPriceList priceList = priceListService.selectTcmPriceListById(id);
        if (priceList == null)
        {
            Map<String, Object> r = new HashMap<>();
            r.put("success", false);
            return r;
        }
        priceList.setIsActive(0);
        priceListService.updateTcmPriceList(priceList);
        TcmPriceList updated = priceListService.selectTcmPriceListById(id);
        auditLogService.log("settings", id, priceList != null ? priceList.getName() : id,
                "DISABLE", String.valueOf(SecurityUtils.getUserId()), "停用价格表");
        return PayloadUtils.flatten(updated);
    }

    private Map<String, Object> flattenRoom(TcmRoom r)
    {
        return new LinkedHashMap<>(PayloadUtils.flattenRoom(r));
    }

    private String serializeTags(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof java.util.Collection || value.getClass().isArray())
        {
            return JSON.toJSONString(value);
        }
        return String.valueOf(value);
    }

    private String packRoomExtras(String existingRemark, Object colorValue)
    {
        JSONObject extras = null;
        if (existingRemark != null && !existingRemark.trim().isEmpty() && existingRemark.trim().startsWith("{"))
        {
            try { extras = JSON.parseObject(existingRemark); } catch (Exception ignored) { extras = null; }
        }
        if (extras == null) extras = new JSONObject();
        String color = colorValue == null ? "" : String.valueOf(colorValue).trim();
        if (color.isEmpty())
        {
            extras.remove("color");
        }
        else
        {
            if (!color.matches("^#[0-9a-fA-F]{3,8}$"))
            {
                throw new ServiceException("无效颜色值: " + color);
            }
            extras.put("color", color);
        }
        return extras.isEmpty() ? null : extras.toJSONString();
    }

    private String normalizeOptionalString(Object value)
    {
        if (value == null)
        {
            return "";
        }
        return String.valueOf(value).trim();
    }
}
