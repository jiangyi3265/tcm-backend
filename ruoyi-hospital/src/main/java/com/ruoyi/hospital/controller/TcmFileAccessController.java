package com.ruoyi.hospital.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.hospital.util.HospitalFileStorage;
import com.ruoyi.hospital.util.SignedFileUrlService;

@RestController
@RequestMapping("/api/public/files")
public class TcmFileAccessController
{
    private final SignedFileUrlService signedFileUrlService;
    private final HospitalFileStorage hospitalFileStorage;

    public TcmFileAccessController(
            SignedFileUrlService signedFileUrlService,
            HospitalFileStorage hospitalFileStorage)
    {
        this.signedFileUrlService = signedFileUrlService;
        this.hospitalFileStorage = hospitalFileStorage;
    }

    @GetMapping("/access")
    public void access(
            @RequestParam String resource,
            @RequestParam long expires,
            @RequestParam String signature,
            HttpServletResponse response) throws IOException
    {
        if (!signedFileUrlService.isValid(resource, expires, signature) || !FileUtils.checkAllowDownload(resource))
        {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "invalid or expired file access link");
            return;
        }

        Path filePath = hospitalFileStorage.resolve(resource);
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath))
        {
            if (!hospitalFileStorage.restoreResource(resource))
            {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "file not found");
                return;
            }
        }

        String fileName = filePath.getFileName().toString();
        String contentType = Files.probeContentType(filePath);
        if (StringUtils.isBlank(contentType))
        {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", buildDispositionHeader(fileName, contentType));
        Files.copy(filePath, response.getOutputStream());
        response.flushBuffer();
    }

    private String buildDispositionHeader(String fileName, String contentType) throws IOException
    {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20");
        String dispositionType = isInlineContent(contentType) ? "inline" : "attachment";
        return dispositionType + "; filename*=UTF-8''" + encoded;
    }

    private boolean isInlineContent(String contentType)
    {
        return contentType.startsWith("image/")
                || MediaType.APPLICATION_PDF_VALUE.equals(contentType)
                || contentType.startsWith("text/");
    }
}
