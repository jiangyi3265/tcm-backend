package com.ruoyi.hospital.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
    void buildAccessUrl_usesConfiguredHttpsOriginBehindAnHttpReverseProxy()
    {
        SignedFileUrlService service = new SignedFileUrlService();
        ReflectionTestUtils.setField(service, "tokenSecret", "test-secret");
        ReflectionTestUtils.setField(service, "ttlSeconds", 3600L);
        ReflectionTestUtils.setField(service, "fileAccessBaseUrl", "");
        ReflectionTestUtils.setField(service, "publicAppBaseUrl", "https://www.otcm.app");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("http");
        request.setServerName("www.otcm.app");
        request.setServerPort(8006);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try
        {
            assertTrue(service.buildAccessUrl("hospital-private/qa/image.png")
                    .startsWith("https://www.otcm.app/api/public/files/access?"));
        }
        finally
        {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void explicitFileOriginStillOverridesThePublicApplicationOrigin()
    {
        SignedFileUrlService service = new SignedFileUrlService();
        ReflectionTestUtils.setField(service, "tokenSecret", "test-secret");
        ReflectionTestUtils.setField(service, "ttlSeconds", 3600L);
        ReflectionTestUtils.setField(service, "fileAccessBaseUrl", "https://files.example.test/");
        ReflectionTestUtils.setField(service, "publicAppBaseUrl", "https://www.otcm.app");
        assertTrue(service.buildAccessUrl("hospital-private/qa/image.png")
                .startsWith("https://files.example.test/api/public/files/access?"));
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
