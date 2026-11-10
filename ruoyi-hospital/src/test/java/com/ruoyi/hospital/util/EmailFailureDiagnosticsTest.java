package com.ruoyi.hospital.util;

import static org.junit.jupiter.api.Assertions.*;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import com.sun.mail.smtp.SMTPAddressFailedException;
import com.sun.mail.smtp.SMTPSendFailedException;
import com.alibaba.fastjson2.JSON;

class EmailFailureDiagnosticsTest
{
    @Test void providerCodeSurvivesSpringFailedMessageAndJavaMailExceptionChains() throws Exception
    {
        SMTPAddressFailedException rejected = new SMTPAddressFailedException(
                new InternetAddress("private@example.invalid"), "RCPT private", 550, "private password-token");
        MessagingException nested = new MessagingException("private provider body", rejected);
        Object privateMessage = new Object() {
            @Override public String toString() { fail("Message content must never be inspected"); return ""; }
        };
        MailSendException failure = new MailSendException(Collections.singletonMap(privateMessage, nested));

        Map<String, Object> report = EmailFailureDiagnostics.describe(failure);

        assertEquals("MailSendException", report.get("deliveryErrorType"));
        assertEquals("SMTPAddressFailedException", report.get("deliveryCauseType"));
        assertEquals("550", report.get("smtpCode"));
        assertEquals(3, report.size());
        assertFalse(JSON.toJSONString(report).contains("private"));
        assertFalse(JSON.toJSONString(report).contains("password-token"));
    }

    @Test void nestedConnectionFailureRemainsSpecificWithoutInventingSmtpCode()
    {
        MailSendException failure = new MailSendException("private", new MessagingException(
                "private", new SocketTimeoutException("private remote IP")));
        Map<String, Object> report = EmailFailureDiagnostics.describe(failure);
        assertEquals("SocketTimeoutException", report.get("deliveryCauseType"));
        assertFalse(report.containsKey("smtpCode"));
        assertFalse(JSON.toJSONString(report).contains("private"));
    }

    @Test void onlyFailureCodesFromTypedProviderExceptionsAreRetained()
    {
        for (int code : new int[] {-1, 250, 600, 12345})
        {
            SMTPSendFailedException failure = new SMTPSendFailedException("private", code,
                    "private body says 550; do not parse it", null, null, null, null);
            assertFalse(EmailFailureDiagnostics.describe(failure).containsKey("smtpCode"));
        }
        SMTPSendFailedException deferred = new SMTPSendFailedException("private", 451,
                "private provider response", null, null, null, null);
        assertEquals("451", EmailFailureDiagnostics.describe(deferred).get("smtpCode"));
    }

    @Test void unknownTypesAndCyclicCausesRemainBoundedAndDoNotLeakTheirNames()
    {
        class PrivatePatientException extends RuntimeException { }
        RuntimeException first = new PrivatePatientException(), second = new RuntimeException("private");
        first.initCause(second);
        second.initCause(first);
        Map<String, Object> report = assertTimeoutPreemptively(Duration.ofSeconds(2),
                () -> EmailFailureDiagnostics.describe(first));
        assertEquals("UnknownException", report.get("deliveryErrorType"));
        assertEquals("UnknownException", report.get("deliveryCauseType"));
        assertEquals(2, report.size());
        assertFalse(JSON.toJSONString(report).contains("PrivatePatient"));
    }
}
