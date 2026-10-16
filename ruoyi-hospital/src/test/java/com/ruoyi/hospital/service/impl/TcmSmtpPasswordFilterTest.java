package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import com.ruoyi.common.filter.XssFilter;

class TcmSmtpPasswordFilterTest
{
    @Test void opaqueSmtpPasswordIsNotHtmlEscaped() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/settings/smtp");
        request.setServletPath("/api/settings/smtp");
        request.setContentType("application/json");
        request.setContent("{\"password\":\"local-test<&>secret\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        XssFilter filter = new XssFilter();
        filter.doFilter(request, new MockHttpServletResponse(), (forwarded, response) -> assertSame(request, forwarded));
    }
}
