package com.ruoyi.hospital.service.impl;

import java.util.List;
import java.util.Collections;
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
        TcmPatientFile file = tcmPatientFileMapper.selectTcmPatientFileById(id);
        if (file == null) return 0;
        if (file.getPatientId() == null || file.getPatientId().trim().isEmpty())
        {
            TcmConsultation owner = file.getConsultationId() == null ? null
                    : consultationMapper.selectTcmConsultationById(file.getConsultationId());
            if (owner == null || owner.getPatientId() == null) throw new ServiceException("orphan file record");
            file.setPatientId(owner.getPatientId());
            tcmPatientFileMapper.updateTcmPatientFile(file);
        }
        // Keep a durable tombstone: an old editor must not reinsert this path, and
        // a still-valid signed URL must not restore it from the cloud backup.
        int rows = tcmPatientFileMapper.markTcmPatientFileDeleted(id);
        TcmConsultation query = new TcmConsultation();
        query.setPatientId(file.getPatientId());
        query.setDeletedAt("ANY");
        for (TcmConsultation consultation : consultationMapper.selectTcmConsultationList(query))
            removeConsultationReference(consultation.getId(), file);
        removePatientReference(file);
        return rows;
    }

    private void removeConsultationReference(String id, TcmPatientFile file)
    {
        for (int attempt = 0; attempt < 3; attempt++)
        {
            TcmConsultation latest = consultationMapper.selectTcmConsultationById(id);
            if (latest == null || !file.getPatientId().equals(latest.getPatientId())) return;
            String before = latest.getPayload();
            JSONObject payload = parsePayload(before);
            if (!PatientFileReferences.removeDeleted(payload, Collections.singletonList(file))) return;
            TcmConsultation update = new TcmConsultation();
            update.setId(id);
            update.setPayload(payload.toJSONString());
            if (consultationMapper.updatePdfPayloadIfUnchanged(update, before) == 1) return;
        }
        throw new ServiceException("File references changed while deleting; please retry.");
    }

    private void removePatientReference(TcmPatientFile file)
    {
        for (int attempt = 0; attempt < 3; attempt++)
        {
            TcmPatient latest = patientMapper.selectTcmPatientById(file.getPatientId());
            if (latest == null) return;
            String before = latest.getPayload();
            JSONObject payload = parsePayload(before);
            if (!PatientFileReferences.removeDeleted(payload, Collections.singletonList(file))) return;
            TcmPatient update = new TcmPatient();
            update.setId(latest.getId());
            update.setPayload(payload.toJSONString());
            if (patientMapper.updateFilePayloadIfUnchanged(update, before) == 1) return;
        }
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
