package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.exception.ServiceException;

class TcmPatientServiceImplTest
{
    private TcmPatientServiceImpl service;

    @BeforeEach
    void setUp()
    {
        service = new TcmPatientServiceImpl();
        ReflectionTestUtils.setField(service, "tcmPatientMapper", null);
        ReflectionTestUtils.setField(service, "tcmConsultationMapper", null);
        ReflectionTestUtils.setField(service, "tcmAppointmentMapper", null);
    }

    @Test
    void mergeTcmPatients_shouldRejectSelfMerge()
    {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.mergeTcmPatients("patient-1", "patient-1"));

        assertEquals("不能合并到自身", ex.getMessage());
    }
}
