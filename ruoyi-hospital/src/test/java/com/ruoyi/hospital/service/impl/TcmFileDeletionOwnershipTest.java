package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.*;
import com.ruoyi.hospital.mapper.*;

/** Ownership checks must use locked current records rather than the first file lookup. */
class TcmFileDeletionOwnershipTest {
    private final TcmPatientMapper patients = mock(TcmPatientMapper.class);
    private final TcmConsultationMapper consultations = mock(TcmConsultationMapper.class);
    private final TcmPatientFileMapper files = mock(TcmPatientFileMapper.class);
    private final TcmPatientFileServiceImpl service = new TcmPatientFileServiceImpl();
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "patientMapper", patients);
        ReflectionTestUtils.setField(service, "consultationMapper", consultations);
        ReflectionTestUtils.setField(service, "tcmPatientFileMapper", files);
        when(files.markTcmPatientFileDeleted(1L)).thenReturn(1);
    }
    private TcmPatientFile file(String patient, String consultation) {
        TcmPatientFile file = new TcmPatientFile(); file.setId(1L); file.setPatientId(patient);
        file.setConsultationId(consultation); file.setFilePath("hospital-private/synthetic/document.png");
        file.setFileType("document"); return file;
    }
    private TcmConsultation consultation(String patient) {
        TcmConsultation consultation = new TcmConsultation(); consultation.setId("c"); consultation.setPatientId(patient);
        consultation.setPayload("{}"); return consultation;
    }
    @Test void ownershipChangedByMergeIsRejectedBeforeAnyMutationOrNewParentLock() {
        when(files.selectTcmPatientFileById(1L)).thenReturn(file("source", "c"));
        when(files.selectTcmPatientFileByIdForUpdate(1L)).thenReturn(file("target", "c"));
        assertThrows(ServiceException.class, () -> service.deleteTcmPatientFileById(1L));
        verify(patients).selectTcmPatientByIdForUpdate("source");
        verify(patients, never()).selectTcmPatientByIdForUpdate("target");
        verify(files, never()).markTcmPatientFileDeleted(anyLong());
        verify(files, never()).updateTcmPatientFile(any());
        verify(consultations, never()).updatePdfPayloadIfUnchanged(any(), any());
        verify(patients, never()).updateFilePayloadIfUnchanged(any(), any());
    }
    @Test void unassignedFileWhoseConsultationMovedIsRejectedInsteadOfRestoringOldOwner() {
        when(files.selectTcmPatientFileById(1L)).thenReturn(file(null, "c"));
        when(consultations.selectTcmConsultationById("c")).thenReturn(consultation("source"));
        when(files.selectTcmPatientFileByIdForUpdate(1L)).thenReturn(file(null, "c"));
        when(consultations.selectTcmConsultationsByPatientIdForUpdate("source")).thenReturn(Collections.emptyList());
        assertThrows(ServiceException.class, () -> service.deleteTcmPatientFileById(1L));
        verify(files, never()).updateTcmPatientFile(any());
        verify(files, never()).markTcmPatientFileDeleted(anyLong());
    }
    @Test void unassignedFileCanStillRecoverItsUnchangedConsultationOwner() {
        when(files.selectTcmPatientFileById(1L)).thenReturn(file(null, "c"));
        when(consultations.selectTcmConsultationById("c")).thenReturn(consultation("source"));
        when(files.selectTcmPatientFileByIdForUpdate(1L)).thenReturn(file(null, "c"));
        when(consultations.selectTcmConsultationsByPatientIdForUpdate("source")).thenReturn(List.of(consultation("source")));
        assertEquals(1, service.deleteTcmPatientFileById(1L));
        verify(files).updateTcmPatientFile(argThat(file -> "source".equals(file.getPatientId())));
        org.mockito.InOrder order = inOrder(patients, consultations, files);
        order.verify(patients).selectTcmPatientByIdForUpdate("source");
        order.verify(consultations).selectTcmConsultationsByPatientIdForUpdate("source");
        order.verify(files).selectTcmPatientFileByIdForUpdate(1L);
        order.verify(files).updateTcmPatientFile(any());
        order.verify(files).markTcmPatientFileDeleted(1L);
    }
    @Test void retiredHistoricalPatientStillAllowsRepeatedDeletion() {
        TcmPatient retired = new TcmPatient(); retired.setId("source"); retired.setIsActive(0);
        retired.setMergedInto("target"); retired.setDeletedAt("2026-11-01 09:00:00"); retired.setPayload("{}");
        when(patients.selectTcmPatientByIdForUpdate("source")).thenReturn(retired);
        TcmPatientFile tombstone = file("source", "c"); tombstone.setFileType("__deleted__");
        when(files.selectTcmPatientFileById(1L)).thenReturn(tombstone);
        when(files.selectTcmPatientFileByIdForUpdate(1L)).thenReturn(tombstone);
        assertEquals(1, service.deleteTcmPatientFileById(1L));
        assertEquals(1, service.deleteTcmPatientFileById(1L));
        verify(files, times(2)).markTcmPatientFileDeleted(1L);
        verify(patients, never()).updateTcmPatient(any());
        assertEquals("__deleted__", tombstone.getFileType());
    }
    @Test void vanishedFileIsANoop() {
        assertEquals(0, service.deleteTcmPatientFileById(1L));
        verifyNoInteractions(patients, consultations);
        verify(files, never()).markTcmPatientFileDeleted(anyLong());
    }
}
