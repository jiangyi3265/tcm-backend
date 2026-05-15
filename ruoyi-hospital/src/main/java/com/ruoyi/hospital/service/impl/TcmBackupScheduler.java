package com.ruoyi.hospital.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.hospital.controller.TcmBootstrapController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TcmBackupScheduler
{
    private static final Logger log = LoggerFactory.getLogger(TcmBackupScheduler.class);
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Hong_Kong");

    @Autowired
    private TcmBootstrapController bootstrapController;

    @Value("${ruoyi.profile:./uploadPath}")
    private String profilePath;

    @Value("${tcm.backup.retention-days:30}")
    private int retentionDays;

    @Value("${tcm.backup.b2.enabled:false}")
    private boolean b2Enabled;

    @Value("${tcm.backup.b2.key-id:${B2_KEY_ID:}}")
    private String b2KeyId;

    @Value("${tcm.backup.b2.application-key:${B2_APPLICATION_KEY:}}")
    private String b2ApplicationKey;

    @Value("${tcm.backup.b2.bucket-id:${B2_BUCKET_ID:}}")
    private String b2BucketId;

    @Value("${tcm.backup.b2.file-prefix:${B2_FILE_PREFIX:clinic-backup/}}")
    private String b2FilePrefix;

    @Value("${tcm.backup.b2.lifecycle-enabled:true}")
    private boolean b2LifecycleEnabled;

    @Value("${tcm.backup.b2.auth-url:https://api.backblazeb2.com/b2api/v2/b2_authorize_account}")
    private String b2AuthUrl;

    @Scheduled(cron = "${tcm.backup.cron:0 0 2 * * ?}", zone = "Asia/Hong_Kong")
    public void runDailyBackup()
    {
        try
        {
            Path backupDir = Paths.get(profilePath, "backup");
            Files.createDirectories(backupDir);
            LocalDate today = LocalDate.now(CLINIC_ZONE);
            Path backupFile = backupDir.resolve("clinic-backup-" + today + ".json");
            Map<String, Object> data = bootstrapController.buildFullBackupData();
            byte[] bytes = JSON.toJSONString(data).getBytes(StandardCharsets.UTF_8);
            Files.write(backupFile, bytes);

            LocalDate cutoff = today.minusDays(Math.max(1, retentionDays));
            if (b2Enabled)
            {
                try
                {
                    uploadBackupToB2(backupFile.getFileName().toString(), bytes, cutoff);
                }
                catch (Exception e)
                {
                    log.warn("TCM B2 backup failed: {}", e.getMessage());
                }
            }
            purgeOldBackups(backupDir, cutoff);
            log.info("TCM backup written: {}", backupFile);
        }
        catch (Exception e)
        {
            log.warn("TCM backup failed: {}", e.getMessage());
        }
    }

    private void purgeOldBackups(Path backupDir, LocalDate cutoff) throws java.io.IOException
    {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(backupDir, "clinic-backup-*.json"))
        {
            for (Path path : stream)
            {
                LocalDate backupDate = parseBackupDate(path.getFileName().toString());
                if (backupDate != null && backupDate.isBefore(cutoff))
                {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private LocalDate parseBackupDate(String filename)
    {
        try
        {
            String date = filename.replace("clinic-backup-", "").replace(".json", "");
            return LocalDate.parse(date);
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private void uploadBackupToB2(String fileName, byte[] bytes, LocalDate cutoff) throws Exception
    {
        if (!hasText(b2KeyId) || !hasText(b2ApplicationKey) || !hasText(b2BucketId))
        {
            throw new IllegalStateException("B2 credentials are incomplete");
        }
        JSONObject auth = authorizeB2();
        String prefix = normalizePrefix(b2FilePrefix);
        if (b2LifecycleEnabled)
        {
            try
            {
                syncB2LifecycleRule(auth, prefix);
            }
            catch (Exception e)
            {
                log.warn("TCM B2 lifecycle sync failed: {}", e.getMessage());
            }
        }
        JSONObject upload = getB2UploadUrl(auth);
        String b2FileName = prefix + fileName;
        uploadB2File(upload.getString("uploadUrl"), upload.getString("authorizationToken"), b2FileName, bytes);
        purgeOldB2Backups(auth, prefix, cutoff);
    }

    private JSONObject authorizeB2() throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(b2AuthUrl).openConnection();
        connection.setRequestMethod("GET");
        String credential = b2KeyId + ":" + b2ApplicationKey;
        String encoded = Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8));
        connection.setRequestProperty("Authorization", "Basic " + encoded);
        return readJsonResponse(connection);
    }

    private JSONObject getB2UploadUrl(JSONObject auth) throws Exception
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bucketId", b2BucketId);
        return postB2Json(auth.getString("apiUrl") + "/b2api/v2/b2_get_upload_url",
                auth.getString("authorizationToken"), body);
    }

    private void syncB2LifecycleRule(JSONObject auth, String prefix) throws Exception
    {
        if (!hasText(prefix))
        {
            log.warn("TCM B2 lifecycle sync skipped because file prefix is empty");
            return;
        }
        int days = Math.max(1, retentionDays);
        JSONObject bucket = getB2Bucket(auth);
        JSONArray rules = new JSONArray();
        JSONArray existingRules = bucket.getJSONArray("lifecycleRules");
        if (existingRules != null)
        {
            for (int i = 0; i < existingRules.size(); i++)
            {
                JSONObject rule = existingRules.getJSONObject(i);
                if (rule != null && !prefix.equals(rule.getString("fileNamePrefix")))
                {
                    rules.add(rule);
                }
            }
        }

        JSONObject backupRule = new JSONObject(new LinkedHashMap<>());
        backupRule.put("fileNamePrefix", prefix);
        backupRule.put("daysFromUploadingToHiding", days);
        backupRule.put("daysFromHidingToDeleting", 1);
        rules.add(backupRule);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountId", auth.getString("accountId"));
        body.put("bucketId", b2BucketId);
        body.put("lifecycleRules", rules);
        postB2Json(auth.getString("apiUrl") + "/b2api/v2/b2_update_bucket",
                auth.getString("authorizationToken"), body);
    }

    private JSONObject getB2Bucket(JSONObject auth) throws Exception
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountId", auth.getString("accountId"));
        body.put("bucketId", b2BucketId);
        JSONObject response = postB2Json(auth.getString("apiUrl") + "/b2api/v2/b2_list_buckets",
                auth.getString("authorizationToken"), body);
        JSONArray buckets = response.getJSONArray("buckets");
        if (buckets != null)
        {
            for (int i = 0; i < buckets.size(); i++)
            {
                JSONObject bucket = buckets.getJSONObject(i);
                if (bucket != null && b2BucketId.equals(bucket.getString("bucketId")))
                {
                    return bucket;
                }
            }
        }
        throw new IllegalStateException("B2 bucket not found");
    }

    private void uploadB2File(String uploadUrl, String uploadToken, String fileName, byte[] bytes) throws Exception
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(uploadUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", uploadToken);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("X-Bz-File-Name", encodeB2FileName(fileName));
        connection.setRequestProperty("X-Bz-Content-Sha1", sha1Hex(bytes));
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = connection.getOutputStream())
        {
            out.write(bytes);
        }
        readJsonResponse(connection);
    }

    private void purgeOldB2Backups(JSONObject auth, String prefix, LocalDate cutoff) throws Exception
    {
        String nextFileName = null;
        do
        {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("bucketId", b2BucketId);
            body.put("prefix", prefix);
            body.put("maxFileCount", 1000);
            if (hasText(nextFileName))
            {
                body.put("startFileName", nextFileName);
            }
            JSONObject page = postB2Json(auth.getString("apiUrl") + "/b2api/v2/b2_list_file_names",
                    auth.getString("authorizationToken"), body);
            JSONArray files = page.getJSONArray("files");
            if (files != null)
            {
                for (int i = 0; i < files.size(); i++)
                {
                    JSONObject file = files.getJSONObject(i);
                    String name = file.getString("fileName");
                    LocalDate backupDate = parseBackupDate(name != null && name.startsWith(prefix)
                            ? name.substring(prefix.length())
                            : name);
                    if (backupDate != null && backupDate.isBefore(cutoff))
                    {
                        Map<String, Object> deleteBody = new LinkedHashMap<>();
                        deleteBody.put("fileName", name);
                        deleteBody.put("fileId", file.getString("fileId"));
                        postB2Json(auth.getString("apiUrl") + "/b2api/v2/b2_delete_file_version",
                                auth.getString("authorizationToken"), deleteBody);
                    }
                }
            }
            nextFileName = page.getString("nextFileName");
        }
        while (hasText(nextFileName));
    }

    private JSONObject postB2Json(String url, String authorizationToken, Map<String, Object> body) throws Exception
    {
        byte[] bytes = JSON.toJSONString(body).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", authorizationToken);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = connection.getOutputStream())
        {
            out.write(bytes);
        }
        return readJsonResponse(connection);
    }

    private JSONObject readJsonResponse(HttpURLConnection connection) throws Exception
    {
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        String response = new String(readFully(stream), StandardCharsets.UTF_8);
        if (status < 200 || status >= 300)
        {
            throw new IllegalStateException("B2 HTTP " + status + ": " + response);
        }
        JSONObject parsed = JSON.parseObject(response);
        return parsed != null ? parsed : new JSONObject();
    }

    private byte[] readFully(InputStream input) throws java.io.IOException
    {
        if (input == null)
        {
            return new byte[0];
        }
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream())
        {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1)
            {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }

    private String sha1Hex(byte[] bytes) throws Exception
    {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hash = digest.digest(bytes);
        StringBuilder builder = new StringBuilder();
        for (byte item : hash)
        {
            builder.append(String.format("%02x", item & 0xff));
        }
        return builder.toString();
    }

    private String encodeB2FileName(String value) throws Exception
    {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    }

    private String normalizePrefix(String value)
    {
        if (!hasText(value))
        {
            return "";
        }
        String prefix = value.trim();
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    private boolean hasText(String value)
    {
        return value != null && !value.trim().isEmpty();
    }
}
