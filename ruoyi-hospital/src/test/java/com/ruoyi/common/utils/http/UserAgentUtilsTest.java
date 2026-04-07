package com.ruoyi.common.utils.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class UserAgentUtilsTest
{
    @Test
    void getUserAgentInfo_shouldResolveBrowserAndOperatingSystemOnce()
    {
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36";

        UserAgentUtils.UserAgentInfo userAgentInfo = UserAgentUtils.getUserAgentInfo(userAgent);

        assertNotNull(userAgentInfo);
        assertTrue(userAgentInfo.getBrowser().contains("Chrome"));
        assertTrue(userAgentInfo.getOperatingSystem().contains("Windows"));
        assertEquals(userAgentInfo.getBrowser(), UserAgentUtils.getBrowser(userAgent));
        assertEquals(userAgentInfo.getOperatingSystem(), UserAgentUtils.getOperatingSystem(userAgent));
    }

    @Test
    void getUserAgentInfo_shouldPreferFastPathForEdgeChromium()
    {
        String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 Edg/123.0.0.0";

        UserAgentUtils.UserAgentInfo userAgentInfo = UserAgentUtils.getUserAgentInfo(userAgent);

        assertEquals("Edge123", userAgentInfo.getBrowser());
        assertEquals("Windows10", userAgentInfo.getOperatingSystem());
    }

    @Test
    void getUserAgentInfo_shouldNotTreatIPhoneAsMacOs()
    {
        String userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 "
                + "(KHTML, like Gecko) Version/17.4 Mobile/15E148 Safari/604.1";

        UserAgentUtils.UserAgentInfo userAgentInfo = UserAgentUtils.getUserAgentInfo(userAgent);

        assertEquals("Safari17", userAgentInfo.getBrowser());
        assertEquals("iOS17", userAgentInfo.getOperatingSystem());
    }

    @Test
    void getUserAgentInfo_shouldReturnEmptyValuesForBlankInput()
    {
        UserAgentUtils.UserAgentInfo userAgentInfo = UserAgentUtils.getUserAgentInfo("   ");

        assertEquals(UserAgentUtils.UNKNOWN, userAgentInfo.getBrowser());
        assertEquals(UserAgentUtils.UNKNOWN, userAgentInfo.getOperatingSystem());
    }
}
