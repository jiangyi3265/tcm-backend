package com.ruoyi.hospital.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.hospital.domain.TcmConsultationMod;
import com.ruoyi.hospital.service.ITcmConsultationModService;
import com.ruoyi.hospital.service.ITcmConsultationService;
import com.ruoyi.system.service.ISysUserService;

class TcmAuditLogControllerTest
{
    @Test
    void repeatedActorsAreResolvedOncePerRequestIncludingMissingUsers()
    {
        TcmAuditLogController controller = new TcmAuditLogController();
        ITcmConsultationModService mods = mock(ITcmConsultationModService.class);
        ITcmConsultationService consultations = mock(ITcmConsultationService.class);
        ISysUserService users = mock(ISysUserService.class);
        ReflectionTestUtils.setField(controller, "consultationModService", mods);
        ReflectionTestUtils.setField(controller, "consultationService", consultations);
        ReflectionTestUtils.setField(controller, "sysUserService", users);
        SysUser actor = new SysUser(); actor.setUserId(1L); actor.setNickName("QA");
        when(users.selectUserById(1L)).thenReturn(actor);
        List<TcmConsultationMod> rows = new ArrayList<>();
        for (int i = 0; i < 100; i++)
        {
            TcmConsultationMod row = new TcmConsultationMod();
            row.setUserId(i % 2 == 0 ? "1" : "2"); row.setModType("inventory");
            row.setModDate("2026-10-08 10:00:00"); row.setChanges("{}"); rows.add(row);
        }
        when(mods.selectTcmConsultationModList(any())).thenReturn(rows);
        List<Map<String, Object>> result = controller.list("inventory", 200);
        assertEquals(100, result.size());
        assertEquals("QA", result.get(0).get("userName"));
        assertEquals("2", result.get(1).get("userName"));
        verify(users, times(1)).selectUserById(1L);
        verify(users, times(1)).selectUserById(2L);
    }
}
