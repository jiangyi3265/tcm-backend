package com.ruoyi.hospital.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;

@Component
public class SignedFileUrlService
{
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${token.secret}")
    private String tokenSecret;

    @Value("${file-access.ttl-seconds:300}")
    private long ttlSeconds;

    public String buildAccessUrl(String resourcePath)
    {
        try
        {
            long expires = System.currentTimeMillis() + ttlSeconds * 1000L;
            String signature = sign(resourcePath, expires);
            return "/api/public/files/access?resource="
                    + URLEncoder.encode(resourcePath, StandardCharsets.UTF_8.name())
                    + "&expires=" + expires
                    + "&signature=" + URLEncoder.encode(signature, StandardCharsets.UTF_8.name());
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