package com.ruoyi.hospital.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.exception.ServiceException;

@Component
public class HospitalFileStorage
{
    public static final String PRIVATE_PREFIX = "hospital-private";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    public String store(MultipartFile file, String prefix) throws IOException
    {
        String resource = createResourceKey(prefix, resolveExtension(file.getOriginalFilename()));
        Path target = resolve(resource);
        Files.createDirectories(target.getParent());
        try (InputStream inputStream = file.getInputStream())
        {
            Files.copy(inputStream, target);
        }
        return resource;
    }

    public String createResourceKey(String prefix, String extension)
    {
        String datePath = LocalDate.now().format(DATE_FORMATTER);
        String safePrefix = StringUtils.defaultIfBlank(prefix, "file").replaceAll("[^A-Za-z0-9_-]", "_");
        String safeExtension = normalizeExtension(extension);
        return PRIVATE_PREFIX + "/" + datePath + "/" + safePrefix + "_"
                + UUID.randomUUID().toString().replace("-", "") + safeExtension;
    }

    public Path resolve(String resource)
    {
        String normalized = normalizeResource(resource);
        if (normalized.startsWith(PRIVATE_PREFIX + "/"))
        {
            return getPrivateRoot().resolve(normalized.substring((PRIVATE_PREFIX + "/").length())).normalize();
        }
        if (normalized.startsWith(Constants.RESOURCE_PREFIX + "/"))
        {
            return Paths.get(RuoYiConfig.getProfile() + normalized.substring(Constants.RESOURCE_PREFIX.length()))
                    .normalize();
        }
        throw new ServiceException("unsupported file resource: " + resource);
    }

    public boolean isLegacyPublicUpload(String resource)
    {
        return normalizeResource(resource).startsWith(Constants.RESOURCE_PREFIX + "/upload/");
    }

    public String moveLegacyPublicUpload(String resource, String preferredName) throws IOException
    {
        String normalized = normalizeResource(resource);
        if (!isLegacyPublicUpload(normalized))
        {
            return normalized;
        }
        Path source = resolve(normalized);
        if (!Files.exists(source))
        {
            return normalized;
        }
        String extension = resolveExtension(StringUtils.defaultIfBlank(preferredName, source.getFileName().toString()));
        String migratedResource = createResourceKey("file", extension);
        Path target = resolve(migratedResource);
        Files.createDirectories(target.getParent());
        Files.move(source, target);
        return migratedResource;
    }

    private Path getPrivateRoot()
    {
        return Paths.get(RuoYiConfig.getProfile()).toAbsolutePath().normalize().resolveSibling(PRIVATE_PREFIX);
    }

    private String normalizeResource(String resource)
    {
        String normalized = StringUtils.defaultString(resource).replace("\\", "/").trim();
        if (normalized.startsWith("/" + PRIVATE_PREFIX + "/"))
        {
            return normalized.substring(1);
        }
        return normalized;
    }

    private String resolveExtension(String fileName)
    {
        String safeName = StringUtils.defaultString(fileName);
        int dotIndex = safeName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == safeName.length() - 1)
        {
            return "";
        }
        return safeName.substring(dotIndex);
    }

    private String normalizeExtension(String extension)
    {
        if (StringUtils.isBlank(extension))
        {
            return "";
        }
        return extension.startsWith(".") ? extension : "." + extension;
    }
}
