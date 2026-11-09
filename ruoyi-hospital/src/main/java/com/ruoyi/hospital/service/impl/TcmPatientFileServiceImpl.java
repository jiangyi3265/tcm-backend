package com.ruoyi.hospital.service.impl;

import java.util.List;
import java.util.Collections;
import java.util.Objects;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.mapper.TcmConsultationMapper;
import com.ruoyi.hospital.mapper.TcmPatientMapper;
import com.ruoyi.hospital.util.PatientFileReferences;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.hospital.domain.TcmPatientFile;
import com.ruoyi.hospital.mapper.TcmPatientFileMapper;
import com.ruoyi.hospital.service.ITcmPatientFileService;

@Service
public class TcmPatientFileServiceImpl implements ITcmPatientFileService
{
    @Autowired
    private TcmPatientFileMapper tcmPatientFileMapper;

    @Autowired
    private TcmConsultationMapper consultationMapper;

    @Autowired
    private TcmPatientMapper patientMapper;

    @Override
    public List<TcmPatientFile> selectDeletedFilesByPatientId(String patientId)
    {
        return tcmPatientFileMapper.selectDeletedFilesByPatientId(patientId);
    }

    @Override
    public List<TcmPatientFile> selectDeletedFilesByPatientIdForUpdate(String patientId)
    {
        return tcmPatientFileMapper.selectDeletedFilesByPatientIdForUpdate(patientId);
    }

    @Override
    public List<TcmPatientFile> selectFilesByPatientId(String patientId)
    {
        return tcmPatientFileMapper.selectFilesByPatientId(patientId);
    }

    @Override
    public List<TcmPatientFile> selectFilesByConsultationId(String consultationId)
    {
        return tcmPatientFileMapper.selectFilesByConsultationId(consultationId);
    }

    @Override
    public TcmPatientFile selectTcmPatientFileById(Long id)
    {
        return tcmPatientFileMapper.selectTcmPatientFileById(id);
    }

    @Override
    public TcmPatientFile selectTcmPatientFileByPath(String filePath)
    {
        TcmPatientFile direct = tcmPatientFileMapper.selectTcmPatientFileByPath(filePath);
        if (direct != null && direct.isDeleted()) return direct;
        String canonical = PatientFileReferences.resource(filePath);
        if (canonical == null) return direct;
        for (String alias : new String[] {canonical, "/" + canonical})
        {
            if (alias.equals(filePath)) continue;
            TcmPatientFile alternate = tcmPatientFileMapper.selectTcmPatientFileByPath(alias);
            if (alternate != null && alternate.isDeleted()) return alternate;
            if (direct == null) direct = alternate;
        }
        return direct;
    }

    @Override
    public int insertTcmPatientFile(TcmPatientFile file)
    {
        if (file.isDeleted()) throw new ServiceException("invalid file type");
        TcmPatientFile previous = selectTcmPatientFileByPath(file.getFilePath());
        if (previous != null && previous.isDeleted()) return 0;
        return tcmPatientFileMapper.insertTcmPatientFile(file);
    }

    @Override
    public int updateTcmPatientFile(TcmPatientFile file)
    {
        return tcmPatientFileMapper.updateTcmPatientFile(file);
    }

    @Override
    public int reassignDeletedFiles(String fromPatientId, String toPatientId)
    {
        if (fromPatientId == null || fromPatientId.trim().isEmpty()
                || toPatientId == null || toPatientId.trim().isEmpty()
                || fromPatientId.equals(toPatientId)) return 0;
        // A patient merge moves ownership only; the deletion marker and path stay intact.
        return tcmPatientFileMapper.reassignDeletedFiles(fromPatientId, toPatientId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteTcmPatientFileById(Long id)
    {
        TcmPatientFile observed = tcmPatientFileMapper.selectTcmPatientFileById(id);
        if (observed == null) return 0;
        String patientId = observed.getPatientId();
        boolean missingOwner = patientId == null || patientId.trim().isEmpty();
        if (missingOwner)
        {
            TcmConsultation owner = observed.getConsultationId() == null ? null
                    : consultationMapper.selectTcmConsultationById(observed.getConsultationId());
            if (owner == null || owner.getPatientId() == null || owner.getPatientId().trim().isEmpty())
                throw new ServiceException("orphan file record");
            patientId = owner.getPatientId();
        }
        // Match clinical writes and patient merge: parent, ordered consultations, file.
        // Current reads are essential after the initial lookup established an RR view.
        TcmPatient patient = patientMapper.selectTcmPatientByIdForUpdate(patientId);
        List<TcmConsultation> consultations = consultationMapper.selectTcmConsultationsByPatientIdForUpdate(patientId);
        TcmPatientFile file = tcmPatientFileMapper.selectTcmPatientFileByIdForUpdate(id);
        if (file == null) return 0;
        if (!Objects.equals(observed.getPatientId(), file.getPatientId())
                || !Objects.equals(observed.getConsultationId(), file.getConsultationId()))
            throw new ServiceException("File ownership changed; reopen the record and retry.");
        if (missingOwner)
        {
            boolean sameOwner = false;
            for (TcmConsultation consultation : consultations)
                if (Objects.equals(file.getConsultationId(), consultation.getId())
                        && Objects.equals(patientId, consultation.getPatientId())) sameOwner = true;
            if (!sameOwner) throw new ServiceException("File ownership changed; reopen the record and retry.");
            file.setPatientId(patientId);
            tcmPatientFileMapper.updateTcmPatientFile(file);
        }
        // Keep the tombstone and original physical/cloud cleanup contract.
        int rows = tcmPatientFileMapper.markTcmPatientFileDeleted(id);
        for (TcmConsultation consultation : consultations) removeConsultationReference(consultation, file);
        removePatientReference(patient, file);
        return rows;
    }

    private void removeConsultationReference(TcmConsultation current, TcmPatientFile file)
    {
        if (current == null || !file.getPatientId().equals(current.getPatientId())) return;
        String before = current.getPayload();
        JSONObject payload = parsePayload(before);
        if (!PatientFileReferences.removeDeleted(payload, Collections.singletonList(file))) return;
        TcmConsultation update = new TcmConsultation();
        update.setId(current.getId());
        update.setPayload(payload.toJSONString());
        if (consultationMapper.updatePdfPayloadIfUnchanged(update, before) != 1)
            throw new ServiceException("File references changed while deleting; please retry.");
    }

    private void removePatientReference(TcmPatient current, TcmPatientFile file)
    {
        if (current == null) return;
        String before = current.getPayload();
        JSONObject payload = parsePayload(before);
        if (!PatientFileReferences.removeDeleted(payload, Collections.singletonList(file))) return;
        TcmPatient update = new TcmPatient();
        update.setId(current.getId());
        update.setPayload(payload.toJSONString());
        if (patientMapper.updateFilePayloadIfUnchanged(update, before) != 1)
            throw new ServiceException("File references changed while deleting; please retry.");
    }

    private JSONObject parsePayload(String raw)
    {
        if (raw == null || raw.trim().isEmpty()) return new JSONObject();
        JSONObject parsed = JSON.parseObject(raw);
        if (parsed == null) throw new ServiceException("Invalid record payload; file deletion was not saved.");
        return parsed;
    }
}
