package com.ruoyi.hospital.service.impl;

import java.text.SimpleDateFormat;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.hospital.domain.TcmConsultationMod;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.hospital.service.ITcmConsultationModService;

@Service
public class TcmAuditLogServiceImpl implements ITcmAuditLogService
{
    @Autowired
    private ITcmConsultationModService consultationModService;

    @Override
    public void log(String targetType, String targetId, String targetName, String action, String actorId, String details)
    {
        TcmConsultationMod mod = new TcmConsultationMod();
        mod.setConsultationId(targetId);
        mod.setModDate(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        mod.setModType(targetType);
        mod.setAction(action);
        mod.setUserId(actorId);
        mod.setVersion(1);

        JSONObject payload = new JSONObject();
        payload.put("targetType", targetType);
        payload.put("targetId", targetId);
        payload.put("targetName", targetName);
        payload.put("details", details);
        mod.setChanges(JSON.toJSONString(payload));

        consultationModService.insertMod(mod);
    }
}
