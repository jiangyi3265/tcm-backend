package com.ruoyi.hospital.util;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CloudflareR2StorageService
{
    private static final Logger log = LoggerFactory.getLogger(CloudflareR2StorageService.class);
    private static final String REGION = "auto";
    private static final String SERVICE = "s3";
    private static final String TERMINATOR = "aws4_request";
    private static final String EMPTY_BODY_SHA256 = sha256Hex(new byte[0]);
    private static final DateTimeFormatter AMZ_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    @Value("${tcm.r2.enabled:false}")
    private boolean enabled;

    @Value("${tcm.r2.endpoint:}")
    private String endpoint;

    @Value("${tcm.r2.access-key-id:}")
    private String accessKeyId;

    @Value("${tcm.r2.secret-access-key:}")
    private String secretAccessKey;

    @Value("${tcm.r2.bucket:}")
    private String bucket;

    @Value("${tcm.r2.object-prefix:clinic-files/}")
    private String objectPrefix;

    @Value("${tcm.r2.backup-on-write:true}")
    private boolean backupOnWrite;

    @Value("${tcm.r2.restore-on-read:true}")
    private boolean restoreOnRead;

    public boolean isBackupEnabled()
    {
        return enabled && backupOnWrite && isConfigured();
    }

    public boolean isRestoreEnabled()
    {
        return enabled && restoreOnRead && isConfigured();
    }

    public void backupResourceQuietly(String resource, Path source, String contentType)
    {
        if (!isBackupEnabled() || StringUtils.isBlank(resource) || source == null)
        {
            return;
        }
        try
        {
            uploadObject(toObjectKey(resource), source, contentType);
        }
        catch (Exception e)
        {
            log.warn("Cloudflare R2 backup failed for {}: {}", resource, e.getMessage());
        }
    }

    public boolean restoreResourceQuietly(String resource, Path target)
    {
        if (!isRestoreEnabled() || StringUtils.isBlank(resource) || target == null)
        {
            return false;
        }
        try
        {
            downloadObject(toObjectKey(resource), target);
            return true;
        }
        catch (Exception e)
        {
            log.warn("Cloudflare R2 restore failed for {}: {}", resource, e.getMessage());
            return false;
        }
    }

    public void backupObjectQuietly(String objectName, Path source, String contentType)
    {
        if (!isBackupEnabled() || StringUtils.isBlank(objectName) || source == null)
        {
            return;
        }
        try
        {
            uploadObject(normalizeObjectKey(objectName), source, contentType);
        }
        catch (Exception e)
        {
            log.warn("Cloudflare R2 backup failed for {}: {}", objectName, e.getMessage());
        }
    }

    private void uploadObject(String objectKey, Path source, String contentType) throws Exception
    {
        if (!Files.exists(source) || !Files.isRegularFile(source))
        {
            throw new IllegalStateException("local file not found");
        }
        byte[] bytes = Files.readAllBytes(source);
        String resolvedContentType = StringUtils.defaultIfBlank(contentType, probeContentType(source));
        HttpURLConnection connection = openSignedConnection(
                "PUT",
                objectKey,
                bytes,
                StringUtils.defaultIfBlank(resolvedContentType, "application/octet-stream"));
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream outputStream = connection.getOutputStream())
        {
            outputStream.write(bytes);
        }
        expectSuccess(connection, "upload");
    }

    private void downloadObject(String objectKey, Path target) throws Exception
    {
        HttpURLConnection connection = openSignedConnection("GET", objectKey, new byte[0], null);
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300)
        {
            throw new IllegalStateException("download HTTP " + status + ": " + readResponse(connection));
        }
        Files.createDirectories(target.getParent());
        try (InputStream inputStream = connection.getInputStream(); OutputStream outputStream = Files.newOutputStream(target))
        {
            copy(inputStream, outputStream);
        }
    }

    private HttpURLConnection openSignedConnection(String method, String objectKey, byte[] body, String contentType)
            throws Exception
    {
        String normalizedEndpoint = StringUtils.removeEnd(StringUtils.trim(endpoint), "/");
        String canonicalUri = "/" + encodePathPart(bucket) + "/" + encodeKeyPath(objectKey);
        URL url = new URL(normalizedEndpoint + canonicalUri);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setDoInput(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        if ("PUT".equals(method))
        {
            connection.setDoOutput(true);
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = AMZ_DATE_FORMATTER.format(now);
        String dateStamp = DATE_STAMP_FORMATTER.format(now);
        String payloadHash = "GET".equals(method) ? EMPTY_BODY_SHA256 : sha256Hex(body);
        String host = url.getHost();

        connection.setRequestProperty("x-amz-date", amzDate);
        connection.setRequestProperty("x-amz-content-sha256", payloadHash);
        if (StringUtils.isNotBlank(contentType))
        {
            connection.setRequestProperty("Content-Type", contentType);
        }

        String signedHeaders = StringUtils.isNotBlank(contentType)
                ? "content-type;host;x-amz-content-sha256;x-amz-date"
                : "host;x-amz-content-sha256;x-amz-date";
        String canonicalHeaders = StringUtils.isNotBlank(contentType)
                ? "content-type:" + contentType + "\n"
                : "";
        canonicalHeaders += "host:" + host + "\n"
                + "x-amz-content-sha256:" + payloadHash + "\n"
                + "x-amz-date:" + amzDate + "\n";

        String canonicalRequest = method + "\n"
                + canonicalUri + "\n"
                + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + payloadHash;
        String credentialScope = dateStamp + "/" + REGION + "/" + SERVICE + "/" + TERMINATOR;
        String stringToSign = "AWS4-HMAC-SHA256\n"
                + amzDate + "\n"
                + credentialScope + "\n"
                + sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        String signature = bytesToHex(hmac(signingKey(dateStamp), stringToSign));
        String authorization = "AWS4-HMAC-SHA256 Credential=" + accessKeyId + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;
        connection.setRequestProperty("Authorization", authorization);
        return connection;
    }

    private byte[] signingKey(String dateStamp) throws Exception
    {
        byte[] dateKey = hmac(("AWS4" + secretAccessKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] regionKey = hmac(dateKey, REGION);
        byte[] serviceKey = hmac(regionKey, SERVICE);
        return hmac(serviceKey, TERMINATOR);
    }

    private void expectSuccess(HttpURLConnection connection, String action) throws Exception
    {
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300)
        {
            throw new IllegalStateException(action + " HTTP " + status + ": " + readResponse(connection));
        }
        readResponse(connection);
    }

    private String readResponse(HttpURLConnection connection) throws Exception
    {
        InputStream stream = connection.getResponseCode() >= 200 && connection.getResponseCode() < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (stream == null)
        {
            return "";
        }
        try (InputStream inputStream = stream; ByteArrayOutputStream outputStream = new ByteArrayOutputStream())
        {
            copy(inputStream, outputStream);
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private String toObjectKey(String resource)
    {
        return normalizeObjectKey(StringUtils.defaultString(resource).replace("\\", "/"));
    }

    private String normalizeObjectKey(String value)
    {
        String key = StringUtils.removeStart(StringUtils.defaultString(value).replace("\\", "/").trim(), "/");
        String prefix = StringUtils.defaultString(objectPrefix).replace("\\", "/").trim();
        prefix = StringUtils.removeStart(prefix, "/");
        if (StringUtils.isBlank(prefix))
        {
            return key;
        }
        if (!prefix.endsWith("/"))
        {
            prefix += "/";
        }
        return prefix + key;
    }

    private boolean isConfigured()
    {
        return StringUtils.isNotBlank(endpoint)
                && StringUtils.isNotBlank(accessKeyId)
                && StringUtils.isNotBlank(secretAccessKey)
                && StringUtils.isNotBlank(bucket);
    }

    private String probeContentType(Path path)
    {
        try
        {
            String detected = Files.probeContentType(path);
            if (StringUtils.isNotBlank(detected))
            {
                return detected;
            }
        }
        catch (Exception ignored)
        {
        }
        String filename = path.getFileName() != null ? path.getFileName().toString().toLowerCase(Locale.ROOT) : "";
        if (filename.endsWith(".pdf")) return "application/pdf";
        if (filename.endsWith(".png")) return "image/png";
        if (filename.endsWith(".jpg") || filename.endsWith(".jpeg")) return "image/jpeg";
        if (filename.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    private static String encodeKeyPath(String key)
    {
        String[] parts = StringUtils.defaultString(key).split("/");
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++)
        {
            if (i > 0)
            {
                builder.append('/');
            }
            builder.append(encodePathPart(parts[i]));
        }
        return builder.toString();
    }

    private static String encodePathPart(String value)
    {
        StringBuilder builder = new StringBuilder();
        byte[] bytes = StringUtils.defaultString(value).getBytes(StandardCharsets.UTF_8);
        for (byte item : bytes)
        {
            int c = item & 0xff;
            if ((c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-' || c == '_' || c == '.' || c == '~')
            {
                builder.append((char) c);
            }
            else
            {
                builder.append('%');
                builder.append(String.format("%02X", c));
            }
        }
        return builder.toString();
    }

    private static String sha256Hex(byte[] bytes)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest(bytes));
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] hmac(byte[] key, String data) throws Exception
    {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String bytesToHex(byte[] bytes)
    {
        StringBuilder builder = new StringBuilder();
        for (byte item : bytes)
        {
            builder.append(String.format("%02x", item & 0xff));
        }
        return builder.toString();
    }

    private static void copy(InputStream inputStream, OutputStream outputStream) throws java.io.IOException
    {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1)
        {
            outputStream.write(buffer, 0, read);
        }
    }
}
