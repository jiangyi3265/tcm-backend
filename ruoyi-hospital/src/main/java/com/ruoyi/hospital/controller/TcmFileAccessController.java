package com.ruoyi.hospital.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.hospital.util.SignedFileUrlService;

@RestController
@RequestMapping("/api/public/files")
public class TcmFileAccessController
{
    private final SignedFileUrlService signedFileUrlService;

    public TcmFileAccessController(SignedFileUrlService signedFileUrlService)
    {
        this.signedFileUrlService = signedFileUrlService;
    }

    @GetMapping("/access")
    public void access(@RequestParam String resource,
            @RequestParam long expires,
            @RequestParam String signature,
            HttpServletResponse response) throws Exception
    {
        if (!resource.startsWith(Constants.RESOURCE_PREFIX + "/upload/")
                || !FileUtils.checkAllowDownload(resource)
                || !signedFileUrlService.isValid(resource, expires, signature))
        {
            throw new ServiceException("无效或已过期的文件访问链接");
        }

        String localPath = RuoYiConfig.getProfile();
        String downloadPath = localPath + FileUtils.stripPrefix(resource);
        String downloadName = StringUtils.substringAfterLast(downloadPath, "/");
        String contentType = Files.probeContentType(Paths.get(downloadPath));
        response.setContentType(StringUtils.isNotEmpty(contentType) ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE);
        FileUtils.setAttachmentResponseHeader(response, downloadName);
        FileUtils.writeBytes(downloadPath, response.getOutputStream());
    }
}
