package com.ruoyi.hospital.controller;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.file.FileUploadUtils;
import com.ruoyi.framework.config.ServerConfig;
import com.ruoyi.hospital.domain.TcmPatientFile;
import com.ruoyi.hospital.service.ITcmAuditLogService;
import com.ruoyi.hospital.service.ITcmPatientFileService;

/**
 * 文件上传下载 Controller
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/api/files")
public class TcmFileController
{
    @Autowired
    private ServerConfig serverConfig;

    @Autowired
    private ITcmPatientFileService fileService;

    @Autowired
    private ITcmAuditLogService auditLogService;

    /**
     * 通用文件上传（舌象图片、文档附件等）
     */
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
            String filePath = RuoYiConfig.getUploadPath();
            String fileName = FileUploadUtils.upload(filePath, file);
            String url = serverConfig.getUrl() + fileName;

            // 记录到 tcm_patient_file 表
            TcmPatientFile pf = new TcmPatientFile();
            pf.setPatientId(patientId);
            pf.setConsultationId(consultationId);
            pf.setFileType(fileType);
            pf.setFileName(file.getOriginalFilename());
            pf.setFilePath(fileName);
            fileService.insertTcmPatientFile(pf);
            auditLogService.log("file", String.valueOf(pf.getId()), pf.getFileName(),
                    "UPLOAD_FILE", String.valueOf(SecurityUtils.getUserId()),
                    "上传文件类型: " + fileType);

            Map<String, Object> result = new HashMap<>();
            result.put("url", url);
            result.put("fileName", fileName);
            result.put("originalName", file.getOriginalFilename());
            result.put("fileId", pf.getId());
            return result;
        }
        catch (Exception e)
        {
            throw new ServiceException("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 查询患者文件列表
     */
    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner,apprentice')")
    @GetMapping("/patient/{patientId}")
    public List<TcmPatientFile> listByPatient(@PathVariable String patientId)
    {
        return fileService.selectFilesByPatientId(patientId);
    }

    /**
     * 查询诊疗文件列表
     */
    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner,apprentice')")
    @GetMapping("/consultation/{consultationId}")
    public List<TcmPatientFile> listByConsultation(@PathVariable String consultationId)
    {
        return fileService.selectFilesByConsultationId(consultationId);
    }

    /**
     * 删除文件
     */
    @PreAuthorize("@ss.hasAnyRoles('admin,practitioner')")
    @DeleteMapping("/{id}")
    public Map<String, Object> remove(@PathVariable Long id)
    {
        TcmPatientFile file = fileService.selectTcmPatientFileById(id);
        deletePhysicalFile(file);
        fileService.deleteTcmPatientFileById(id);
        auditLogService.log("file", String.valueOf(id), file != null ? file.getFileName() : String.valueOf(id),
                "DELETE_FILE", String.valueOf(SecurityUtils.getUserId()), "删除患者附件");
        Map<String, Object> r = new HashMap<>();
        r.put("ok", true);
        return r;
    }

    private void deletePhysicalFile(TcmPatientFile file)
    {
        if (file == null || file.getFilePath() == null || file.getFilePath().isEmpty())
        {
            return;
        }
        String relativePath = file.getFilePath().replace("\\", "/");
        if (relativePath.startsWith(Constants.RESOURCE_PREFIX))
        {
            relativePath = relativePath.substring(Constants.RESOURCE_PREFIX.length());
        }
        File physicalFile = new File(RuoYiConfig.getProfile() + relativePath);
        if (physicalFile.exists() && !physicalFile.delete())
        {
            throw new ServiceException("文件删除失败: " + file.getFileName());
        }
    }
}
