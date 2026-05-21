package com.ruoyi.hospital.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import com.ruoyi.common.exception.ServiceException;

@Component
public class SignedFileUrlService
{
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${token.secret}")
    private String tokenSecret;

    @Value("${file-access.ttl-seconds:2592000}")
    private long ttlSeconds;

    @Value("${file-access.base-url:}")
    private String fileAccessBaseUrl;

    @Value("${public.app-base-url:${PUBLIC_APP_BASE_URL:http://127.0.0.1:5173}}")
    private String publicAppBaseUrl;

    public String buildAccessUrl(String resourcePath)
    {
        try
        {
            long expires = System.currentTimeMillis() + ttlSeconds * 1000L;
            String signature = sign(resourcePath, expires);
            String path = "/api/public/files/access?resource="
                    + URLEncoder.encode(resourcePath, StandardCharsets.UTF_8.name())
                    + "&expires=" + expires
                    + "&signature=" + URLEncoder.encode(signature, StandardCharsets.UTF_8.name());
            return toAbsoluteUrl(path);
        }
        catch (UnsupportedEncodingException e)
        {
            throw new ServiceException("Failed to encode file access URL");
        }
    }

    public boolean isValid(String resourcePath, long expires, String signature)
    {
        if (resourcePath == null || signature == null || expires < System.currentTimeMillis())
        {
            return false;
        }
        return sign(resourcePath, expires).equals(signature);
    }

    private String toAbsoluteUrl(String path)
    {
        String baseUrl = firstNonBlank(fileAccessBaseUrl, currentRequestBaseUrl(), publicAppBaseUrl);
        if (baseUrl.isEmpty() || "/".equals(baseUrl))
        {
            return path;
        }
        while (baseUrl.endsWith("/"))
        {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://"))
        {
            baseUrl = (baseUrl.startsWith("localhost") || baseUrl.startsWith("127."))
                    ? "http://" + baseUrl
                    : "https://" + baseUrl;
        }
        return baseUrl + path;
    }

    private String currentRequestBaseUrl()
    {
        try
        {
            return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        }
        catch (Exception ignored)
        {
            return "";
        }
    }

    private String firstNonBlank(String... values)
    {
        if (values == null)
        {
            return "";
        }
        for (String value : values)
        {
            if (value != null && !value.trim().isEmpty())
            {
                return value.trim();
            }
        }
        return "";
    }

    private String sign(String resourcePath, long expires)
    {
        try
        {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(tokenSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] digest = mac.doFinal((resourcePath + ":" + expires).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        }
        catch (Exception e)
        {
            throw new ServiceException("Failed to sign file access URL");
        }
    }
}
