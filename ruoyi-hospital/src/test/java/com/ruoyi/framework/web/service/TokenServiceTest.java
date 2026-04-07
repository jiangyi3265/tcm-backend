package com.ruoyi.framework.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.utils.http.UserAgentUtils;

class TokenServiceTest
{
    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void setUserAgent_shouldPopulateBrowserAndOperatingSystemFromSingleResolvedInfo()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36");
        request.setRemoteAddr("127.0.0.1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        TokenService tokenService = new TokenService();
        LoginUser loginUser = new LoginUser();
        tokenService.setUserAgent(loginUser);

        assertEquals("内网IP", loginUser.getLoginLocation());
        assertEquals(UserAgentUtils.getBrowser(request.getHeader("User-Agent")), loginUser.getBrowser());
        assertEquals(UserAgentUtils.getOperatingSystem(request.getHeader("User-Agent")), loginUser.getOs());
    }
}
