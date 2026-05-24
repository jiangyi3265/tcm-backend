package com.ruoyi.hospital.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.hospital.domain.TcmAppointment;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.domain.TcmPatientFile;
import com.ruoyi.hospital.service.ITcmAppointmentService;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.hospital.service.ITcmConsultationService;
import com.ruoyi.hospital.service.ITcmPatientFileService;
import com.ruoyi.hospital.service.ITcmPatientService;
import com.ruoyi.hospital.util.HospitalFileStorage;
import com.ruoyi.hospital.util.SignedFileUrlService;
import com.ruoyi.hospital.utils.PrivacyUtils;

@RestController
@RequestMapping("/api/files")
public class TcmFileController
{
    @Autowired
    private ITcmPatientFileService fileService;

    @Autowired
    private ITcmAuditLogService auditLogService;

    @Autowired
    private ITcmPatientService patientService;

    @Autowired
    private ITcmConsultationService consultationService;

    @Autowired
    private ITcmAppointmentService appointmentService;

    @Autowired
    private SignedFileUrlService signedFileUrlService;

    @Autowired
    private HospitalFileStorage hospitalFileStorage;

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner,apprentice')")
    @PostMapping("/upload")
    public Map<String, Object> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "patientId", required = false) String patientId,
            @RequestParam(value = "consultationId", required = false) String consultationId,
            @RequestParam(value = "fileType", required = false, defaultValue = "document") String fileType)
    {
        try
        {
            TcmPatient patient = resolveAuthorizedPatient(patientId, consultationId);
            TcmConsultation consultation = StringUtils.isBlank(consultationId)
                    ? null
                    : requireConsultation(consultationId);
            String resource = hospitalFileStorage.store(file, fileType);

            TcmPatientFile patientFile = new TcmPatientFile();
            patientFile.setPatientId(patient.getId());
            patientFile.setConsultationId(consultation != null ? consultation.getId() : null);
            patientFile.setFileType(fileType);
            patientFile.setFileName(StringUtils.defaultIfBlank(file.getOriginalFilename(), file.getName()));
            patientFile.setFilePath(resource);
            fileService.insertTcmPatientFile(patientFile);

            auditLogService.log(
                    "file",
                    String.valueOf(patientFile.getId()),
                    patientFile.getFileName(),
                    "UPLOAD_FILE",
                    String.valueOf(SecurityUtils.getUserId()),
                    "upload file type: " + fileType);

            Map<String, Object> result = toFileMap(patientFile, consultation);
            result.put("filePath", resource);
            result.put("resource", resource);
            result.put("url", signedFileUrlService.buildAccessUrl(resource));
            return result;
        }
        catch (IOException e)
        {
            throw new ServiceException("file upload failed: " + e.getMessage());
        }
    }

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner,apprentice')")
    @GetMapping("/patient/{patientId}")
    public List<Map<String, Object>> listByPatient(@PathVariable String patientId)
    {
        TcmPatient patient = requirePatient(patientId);
        ensurePatientAccessible(patient);

        List<Map<String, Object>> result = new ArrayList<>();
        for (TcmPatientFile file : fileService.selectFilesByPatientId(patientId))
        {
            TcmPatientFile normalized = normalizeManagedFile(file);
            TcmConsultation consultation = StringUtils.isBlank(normalized.getConsultationId())
                    ? null
                    : consultationService.selectTcmConsultationById(normalized.getConsultationId());
            if (consultation != null && !canAccessConsultation(consultation))
            {
                continue;
            }
            result.add(toFileMap(normalized, consultation));
        }
        return result;
    }

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner,apprentice')")
    @GetMapping("/patient/{patientId}/download-all")
    public void downloadAllByPatient(@PathVariable String patientId, HttpServletResponse response) throws IOException
    {
        TcmPatient patient = requirePatient(patientId);
        ensurePatientAccessible(patient);

        List<TcmPatientFile> files = fileService.selectFilesByPatientId(patientId);
        String zipName = "patient-" + patientId + "-files.zip";
        response.setContentType("application/zip");
        response.setHeader(
                "Content-Disposition",
                "attachment; filename*=UTF-8''"
                        + URLEncoder.encode(zipName, StandardCharsets.UTF_8.name()).replace("+", "%20"));

        Set<String> usedNames = new HashSet<>();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(response.getOutputStream()))
        {
            for (TcmPatientFile patientFile : files)
            {
                TcmPatientFile normalized = normalizeManagedFile(patientFile);
                TcmConsultation consultation = StringUtils.isBlank(normalized.getConsultationId())
                        ? null
                        : consultationService.selectTcmConsultationById(normalized.getConsultationId());
                if (consultation != null && !canAccessConsultation(consultation))
                {
                    continue;
                }
                hospitalFileStorage.restoreResource(normalized.getFilePath());
                Path physicalFile = resolvePhysicalPath(normalized);
                if (physicalFile == null || !Files.exists(physicalFile) || !Files.isRegularFile(physicalFile))
                {
                    continue;
                }
                String entryName = buildUniqueEntryName(
                        StringUtils.defaultIfBlank(normalized.getFileName(), physicalFile.getFileName().toString()),
                        usedNames);
                zipOutputStream.putNextEntry(new ZipEntry(entryName));
                Files.copy(physicalFile, zipOutputStream);
                zipOutputStream.closeEntry();
            }
            zipOutputStream.finish();
        }

        auditLogService.log(
                "file",
                patientId,
                patient.getName(),
                "DOWNLOAD_ALL_FILES",
                String.valueOf(SecurityUtils.getUserId()),
                "download all patient files as zip");
    }

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner,apprentice')")
    @GetMapping("/consultation/{consultationId}")
    public List<Map<String, Object>> listByConsultation(@PathVariable String consultationId)
    {
        TcmConsultation consultation = requireConsultation(consultationId);
        ensureConsultationAccessible(consultation);

        List<Map<String, Object>> result = new ArrayList<>();
        for (TcmPatientFile file : fileService.selectFilesByConsultationId(consultationId))
        {
            result.add(toFileMap(normalizeManagedFile(file), consultation));
        }
        return result;
    }

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner,apprentice')")
    @GetMapping("/access-url")
    public Map<String, Object> accessUrl(@RequestParam String resource)
    {
        TcmPatientFile file = requireManagedFile(resource);
        authorizeFileAccess(file);
        TcmPatientFile normalized = normalizeManagedFile(file);
        Map<String, Object> result = new HashMap<>();
        result.put("resource", normalized.getFilePath());
        result.put("url", signedFileUrlService.buildAccessUrl(normalized.getFilePath()));
        return result;
    }

    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner')")
    @DeleteMapping("/{id}")
    public Map<String, Object> remove(@PathVariable Long id)
    {
        TcmPatientFile file = fileService.selectTcmPatientFileById(id);
        if (file == null)
        {
            throw new ServiceException("file not found");
        }
        authorizeFileAccess(file);
        TcmPatientFile normalized = normalizeManagedFile(file);
        deletePhysicalFile(normalized);
        fileService.deleteTcmPatientFileById(id);
        auditLogService.log(
                "file",
                String.valueOf(id),
                normalized.getFileName(),
                "DELETE_FILE",
                String.valueOf(SecurityUtils.getUserId()),
                "delete patient file");
        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        return result;
    }

    private Map<String, Object> toFileMap(TcmPatientFile file, TcmConsultation consultation)
    {
        Map<String, Object> result = new HashMap<>();
        result.put("id", file.getId());
        result.put("patientId", file.getPatientId());
        result.put("consultationId", file.getConsultationId());
        result.put("fileType", file.getFileType());
        result.put("fileName", file.getFileName());
        result.put("filePath", file.getFilePath());
        result.put("resource", file.getFilePath());
        result.put("url", signedFileUrlService.buildAccessUrl(file.getFilePath()));
        result.put("uploadTime", file.getUploadTime());
        if (consultation != null)
        {
            result.put("consultDate", consultation.getConsultDate());
        }
        Path physicalPath = resolvePhysicalPath(file);
        try
        {
            if (physicalPath != null && Files.exists(physicalPath))
            {
                result.put("fileSize", Files.size(physicalPath));
            }
        }
        catch (IOException ignored)
        {
            // best-effort metadata only
        }
        return result;
    }

    private void deletePhysicalFile(TcmPatientFile file)
    {
        try
        {
            Path physicalFile = resolvePhysicalPath(file);
            if (physicalFile != null && Files.exists(physicalFile))
            {
                Files.delete(physicalFile);
            }
        }
        catch (IOException e)
        {
            throw new ServiceException("file delete failed: " + e.getMessage());
        }
    }

    private Path resolvePhysicalPath(TcmPatientFile file)
    {
        if (file == null || StringUtils.isBlank(file.getFilePath()))
        {
            return null;
        }
        return hospitalFileStorage.resolve(file.getFilePath());
    }

    private TcmPatientFile normalizeManagedFile(TcmPatientFile file)
    {
        if (file == null || StringUtils.isBlank(file.getFilePath()))
        {
            return file;
        }
        try
        {
            if (hospitalFileStorage.isLegacyPublicUpload(file.getFilePath()))
            {
                String migratedResource = hospitalFileStorage.moveLegacyPublicUpload(file.getFilePath(), file.getFileName());
                if (!StringUtils.equals(migratedResource, file.getFilePath()))
                {
                    file.setFilePath(migratedResource);
                    fileService.updateTcmPatientFile(file);
                }
            }
            return file;
        }
        catch (IOException e)
        {
            throw new ServiceException("file migration failed: " + e.getMessage());
        }
    }

    private TcmPatient resolveAuthorizedPatient(String patientId, String consultationId)
    {
        TcmConsultation consultation = null;
        TcmPatient patient = null;
        if (StringUtils.isNotBlank(consultationId))
        {
            consultation = requireConsultation(consultationId);
            ensureConsultationAccessible(consultation);
            patient = requirePatient(consultation.getPatientId());
        }
        if (StringUtils.isNotBlank(patientId))
        {
            TcmPatient requestedPatient = requirePatient(patientId);
            ensurePatientAccessible(requestedPatient);
            if (patient != null && !StringUtils.equals(patient.getId(), requestedPatient.getId()))
            {
                throw new ServiceException("consultation does not belong to the specified patient");
            }
            patient = requestedPatient;
        }
        if (patient == null)
        {
            throw new ServiceException("patientId or consultationId is required");
        }
        return patient;
    }

    private TcmPatient requirePatient(String patientId)
    {
        TcmPatient patient = patientService.selectTcmPatientById(patientId);
        if (patient == null)
        {
            throw new ServiceException("patient not found");
        }
        return patient;
    }

    private TcmConsultation requireConsultation(String consultationId)
    {
        TcmConsultation consultation = consultationService.selectTcmConsultationById(consultationId);
        if (consultation == null)
        {
            throw new ServiceException("consultation not found");
        }
        return consultation;
    }

    private TcmPatientFile requireManagedFile(String resource)
    {
        TcmPatientFile file = fileService.selectTcmPatientFileByPath(resource);
        if (file == null)
        {
            file = fileService.selectTcmPatientFileByPath(StringUtils.removeStart(resource, "/"));
        }
        if (file == null)
        {
            throw new ServiceException("file not found");
        }
        return file;
    }

    private void authorizeFileAccess(TcmPatientFile file)
    {
        if (StringUtils.isNotBlank(file.getConsultationId()))
        {
            ensureConsultationAccessible(requireConsultation(file.getConsultationId()));
            return;
        }
        if (StringUtils.isNotBlank(file.getPatientId()))
        {
            ensurePatientAccessible(requirePatient(file.getPatientId()));
            return;
        }
        throw new ServiceException("orphan file record");
    }

    private void ensurePatientAccessible(TcmPatient patient)
    {
        List<TcmConsultation> consultations = consultationService.selectTcmConsultationList(new TcmConsultation());
        List<TcmAppointment> appointments = appointmentService.selectTcmAppointmentList(new TcmAppointment());
        if (!PrivacyUtils.canAccessPatient(patient, consultations, appointments))
        {
            throw new ServiceException("access denied");
        }
    }

    private void ensureConsultationAccessible(TcmConsultation consultation)
    {
        if (!canAccessConsultation(consultation))
        {
            throw new ServiceException("access denied");
        }
    }

    private boolean canAccessConsultation(TcmConsultation consultation)
    {
        TcmPatient patient = requirePatient(consultation.getPatientId());
        List<TcmAppointment> appointments = appointmentService.selectTcmAppointmentList(new TcmAppointment());
        return PrivacyUtils.canAccessConsultation(consultation, patient, appointments);
    }

    private String buildUniqueEntryName(String originalName, Set<String> usedNames)
    {
        String candidate = originalName;
        if (!usedNames.contains(candidate))
        {
            usedNames.add(candidate);
            return candidate;
        }

        int dot = originalName.lastIndexOf('.');
        String baseName = dot >= 0 ? originalName.substring(0, dot) : originalName;
        String extension = dot >= 0 ? originalName.substring(dot) : "";
        int suffix = 2;
        while (usedNames.contains(candidate))
        {
            candidate = baseName + "-" + suffix + extension;
            suffix++;
        }
        usedNames.add(candidate);
        return candidate;
    }
}
