package com.ruoyi.hospital.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SignedFileUrlServiceTest
{
    @Test
    void buildAccessUrl_shouldReturnAbsoluteSignedUrl() throws Exception
    {
        SignedFileUrlService service = new SignedFileUrlService();
        ReflectionTestUtils.setField(service, "tokenSecret", "test-secret");
        ReflectionTestUtils.setField(service, "ttlSeconds", 2592000L);
        ReflectionTestUtils.setField(service, "fileAccessBaseUrl", "https://otcm.app/");

        String url = service.buildAccessUrl("hospital-private/2026/05/invoice_test.pdf");

        assertTrue(url.startsWith("https://otcm.app/api/public/files/access?"));
        Map<String, String> query = parseQuery(url.substring(url.indexOf('?') + 1));
        assertEquals("hospital-private/2026/05/invoice_test.pdf", query.get("resource"));
        long expires = Long.parseLong(query.get("expires"));
        assertTrue(expires > System.currentTimeMillis());
        assertTrue(service.isValid(query.get("resource"), expires, query.get("signature")));
    }

    @Test
    void buildAccessUrl_shouldNormalizeBareProductionDomain()
    {
        SignedFileUrlService service = new SignedFileUrlService();
        ReflectionTestUtils.setField(service, "tokenSecret", "test-secret");
        ReflectionTestUtils.setField(service, "ttlSeconds", 2592000L);
        ReflectionTestUtils.setField(service, "fileAccessBaseUrl", "otcm.app");

        String url = service.buildAccessUrl("hospital-private/2026/05/invoice_test.pdf");

        assertTrue(url.startsWith("https://otcm.app/api/public/files/access?"));
    }

    private Map<String, String> parseQuery(String query) throws Exception
    {
        Map<String, String> params = new LinkedHashMap<>();
        for (String part : query.split("&"))
        {
            String[] pair = part.split("=", 2);
            String key = URLDecoder.decode(pair[0], "UTF-8");
            String value = pair.length > 1 ? URLDecoder.decode(pair[1], "UTF-8") : "";
            params.put(key, value);
        }
        return params;
    }
}
