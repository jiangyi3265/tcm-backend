package com.ruoyi.hospital.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final DateTimeFormatter YEAR_FORMATTER = DateTimeFormatter.ofPattern("yyyy");

    @Autowired
    private CloudflareR2StorageService r2StorageService;

    public String store(MultipartFile file, String prefix) throws IOException
    {
        String resource = createResourceKey(prefix, resolveExtension(file.getOriginalFilename()));
        Path target = resolve(resource);
        Files.createDirectories(target.getParent());
        try (InputStream inputStream = file.getInputStream())
        {
            Files.copy(inputStream, target);
        }
        backupResource(resource);
        return resource;
    }

    public String storePatientFile(
            MultipartFile file,
            String patientName,
            String fileType,
            String consultationDate) throws IOException
    {
        String resource = createPatientFileResourceKey(
                patientName,
                fileType,
                consultationDate,
                resolveExtension(file.getOriginalFilename()));
        Path target = resolve(resource);
        Files.createDirectories(target.getParent());
        try (InputStream inputStream = file.getInputStream())
        {
            Files.copy(inputStream, target);
        }
        backupResource(resource);
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

    public String createPatientFileResourceKey(
            String patientName,
            String fileType,
            String consultationDate,
            String extension)
    {
        String patientFolder = sanitizePathSegment(patientName, "Unknown_Patient");
        String category = resolvePatientFileCategory(fileType);
        String safePrefix = sanitizePathSegment(fileType, "file");
        StringBuilder path = new StringBuilder(PRIVATE_PREFIX)
                .append("/")
                .append(patientFolder)
                .append("/")
                .append(category);
        if (patientFileCategoryUsesYear(category))
        {
            path.append("/").append(resolveYear(consultationDate));
        }
        return path.append("/")
                .append(safePrefix)
                .append("_")
                .append(UUID.randomUUID().toString().replace("-", ""))
                .append(normalizeExtension(extension))
                .toString();
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
        backupResource(migratedResource);
        return migratedResource;
    }

    public void backupResource(String resource)
    {
        Path target = resolve(resource);
        r2StorageService.backupResourceQuietly(resource, target, probeContentType(target));
    }

    public boolean restoreResource(String resource)
    {
        Path target = resolve(resource);
        if (Files.exists(target) && Files.isRegularFile(target))
        {
            return true;
        }
        return r2StorageService.restoreResourceQuietly(resource, target);
    }

    private String probeContentType(Path target)
    {
        try
        {
            return Files.probeContentType(target);
        }
        catch (Exception ignored)
        {
            return null;
        }
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

    private String resolvePatientFileCategory(String fileType)
    {
        String normalized = StringUtils.defaultString(fileType)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace("-", "_")
                .replace(" ", "_");
        if (normalized.contains("consent"))
        {
            return "Consent Form";
        }
        if (normalized.contains("receipt") || normalized.contains("invoice"))
        {
            return "Receipt";
        }
        if (normalized.contains("exam") || normalized.contains("examination")
                || normalized.contains("lab") || normalized.contains("image"))
        {
            return "Examination Report";
        }
        if (normalized.contains("report"))
        {
            return "Consultation Report";
        }
        return "Consultation";
    }

    private boolean patientFileCategoryUsesYear(String category)
    {
        return !"Consent Form".equals(category);
    }

    private String resolveYear(String consultationDate)
    {
        String text = StringUtils.defaultString(consultationDate).trim();
        if (text.length() >= 4)
        {
            String candidate = text.substring(0, 4);
            if (candidate.matches("\\d{4}"))
            {
                return candidate;
            }
        }
        return LocalDate.now().format(YEAR_FORMATTER);
    }

    private String sanitizePathSegment(String value, String fallback)
    {
        String cleaned = StringUtils.defaultIfBlank(value, fallback)
                .replace("\\", "_")
                .replace("/", "_")
                .trim()
                .replaceAll("[\\\\/:*?\"<>|]+", "_")
                .replaceAll("\\s+", "_")
                .replaceAll("[^\\p{L}\\p{N}._-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^[._]+|[._]+$", "");
        if (StringUtils.isBlank(cleaned))
        {
            cleaned = fallback;
        }
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }
}
