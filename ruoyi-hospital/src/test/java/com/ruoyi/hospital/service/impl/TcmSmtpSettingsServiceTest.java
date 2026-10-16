package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import javax.mail.AuthenticationFailedException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mail.javamail.*;
import org.springframework.test.util.ReflectionTestUtils;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.*;
import com.ruoyi.hospital.mapper.*;
import com.ruoyi.hospital.util.SmtpSecretCipher;

class TcmSmtpSettingsServiceTest
{
    @TempDir Path directory;
    private TcmSmtpSettingsService service;
    private TcmClinicSettingMapper mapper;
    private SmtpSecretCipher cipher;
    private final Map<String, TcmClinicSetting> records = new HashMap<>();

    @BeforeEach void setup()
    {
        service = spy(new TcmSmtpSettingsService());
        mapper = mock(TcmClinicSettingMapper.class);
        cipher = new SmtpSecretCipher();
        ReflectionTestUtils.setField(cipher, "keyFile", directory.resolve("smtp-settings.key").toString());
        when(mapper.selectSettingByKey(anyString())).thenAnswer(call -> records.get(call.getArgument(0)));
        when(mapper.insertSetting(any())).thenAnswer(call -> { TcmClinicSetting row = call.getArgument(0); records.put(row.getSettingKey(), row); return 1; });
        ReflectionTestUtils.setField(service, "settingMapper", mapper);
        ReflectionTestUtils.setField(service, "cipher", cipher);
        ReflectionTestUtils.setField(service, "environment", new MockEnvironment()
                .withProperty("spring.mail.host", "smtp.resend.com").withProperty("spring.mail.port", "465")
                .withProperty("spring.mail.username", "resend").withProperty("spring.mail.password", "old-local-test-secret")
                .withProperty("spring.mail.from", "Clinic <clinic@example.invalid>")
                .withProperty("spring.mail.properties.mail.smtp.ssl.enable", "true"));
    }

    private Map<String, Object> settings(String password)
    {
        return new HashMap<>(Map.of("host", "smtp.resend.com", "port", 465, "username", "resend",
                "fromAddress", "Clinic <clinic@example.invalid>", "security", "ssl", "password", password));
    }

    @Test void savesEncryptedPasswordWithoutReturningAnySecretAndSurvivesNewCipherInstance()
    {
        Map<String, Object> status = service.update(settings("local-test-secret<&>"));
        String stored = records.get(TcmSmtpSettingsService.STORAGE_KEY).getSettingValue();
        assertFalse(stored.contains("local-test-secret"));
        assertFalse(JSONObject.toJSONString(status).contains("local-test-secret"));
        assertFalse(status.containsKey("password"));
        assertFalse(status.containsKey("encryptedPassword"));
        assertEquals(true, status.get("passwordConfigured"));
        SmtpSecretCipher restarted = new SmtpSecretCipher();
        ReflectionTestUtils.setField(restarted, "keyFile", directory.resolve("smtp-settings.key").toString());
        assertEquals("local-test-secret<&>", restarted.decrypt(JSONObject.parseObject(stored).getString("encryptedPassword")));
    }

    @Test void keyRotationChangesTheNextTransportAndBlankKeepsTheNewKey()
    {
        service.update(settings("first-local-test-key"));
        JavaMailSenderImpl first = (JavaMailSenderImpl) service.transport().sender;
        service.update(settings("second-local-test-key"));
        assertEquals("second-local-test-key", ((JavaMailSenderImpl) service.transport().sender).getPassword());
        service.update(settings(""));
        JavaMailSenderImpl next = (JavaMailSenderImpl) service.transport().sender;
        assertEquals("second-local-test-key", next.getPassword());
        assertEquals("first-local-test-key", first.getPassword());
        assertEquals("true", next.getJavaMailProperties().getProperty("mail.smtp.ssl.checkserveridentity"));
        assertEquals("5000", next.getJavaMailProperties().getProperty("mail.smtp.timeout"));
        assertEquals("false", next.getJavaMailProperties().getProperty("mail.debug"));
    }

    @Test void preservesRuntimeTransportUntilAnAdminSavesAndCanMigrateRuntimeSecret()
    {
        JavaMailSender runtime = mock(JavaMailSender.class);
        ReflectionTestUtils.setField(service, "runtimeSender", runtime);
        assertSame(runtime, service.transport().sender);
        assertEquals("environment", service.status().get("source"));
        service.update(settings(""));
        assertEquals("admin", service.status().get("source"));
        assertEquals("old-local-test-secret", ((JavaMailSenderImpl) service.transport().sender).getPassword());
    }

    @Test void refusesCredentialReuseAtAnotherHostAndRejectsInvalidTlsHeadersAndPorts()
    {
        service.update(settings("secret-for-original-host"));
        for (Map<String, ?> bad : Arrays.asList(
                Map.of("host", "another.example.invalid"), Map.of("username", "another"),
                Map.of("port", 0), Map.of("port", 65536), Map.of("security", "none"),
                Map.of("host", "https://smtp.resend.com/path"), Map.of("fromAddress", "a@example.invalid\r\nBcc: stolen@example.invalid")))
        {
            Map<String, Object> request = settings(""); request.putAll(bad);
            assertThrows(ServiceException.class, () -> service.update(request));
        }
        assertEquals("secret-for-original-host", ((JavaMailSenderImpl) service.transport().sender).getPassword());
    }

    @Test void rejectsTamperedCiphertextAndNeverFallsBackToTheOldRevokedSecret() throws Exception
    {
        service.update(settings("updated-local-secret"));
        TcmClinicSetting row = records.get(TcmSmtpSettingsService.STORAGE_KEY);
        JSONObject stored = JSONObject.parseObject(row.getSettingValue());
        String encrypted = stored.getString("encryptedPassword");
        stored.put("encryptedPassword", encrypted.substring(0, encrypted.length() - 4) + "AAAA");
        row.setSettingValue(stored.toJSONString());
        assertThrows(ServiceException.class, service::transport);
        assertEquals(false, service.testConnection().get("success"));
        Files.delete(directory.resolve("smtp-settings.key"));
        assertThrows(ServiceException.class, service::transport);
        service.update(settings("recovered-local-secret"));
        assertEquals("recovered-local-secret", ((JavaMailSenderImpl) service.transport().sender).getPassword());
    }

    @Test void testsAuthenticationWithoutSendingAndSanitizesProviderFailures() throws Exception
    {
        JavaMailSenderImpl transport = mock(JavaMailSenderImpl.class);
        doReturn(transport).when(service).buildSender(any(), anyString());
        assertEquals(true, service.testConnection().get("success"));
        verify(transport).testConnection();
        verify(transport, never()).send(any(MimeMessage.class));
        doThrow(new AuthenticationFailedException("secret-provider-response-local-key")).when(transport).testConnection();
        Map<String, Object> result = service.testConnection();
        assertEquals("SMTP_AUTH_FAILED", result.get("code"));
        assertFalse(JSONObject.toJSONString(result).contains("secret-provider-response"));
    }

    @Test void hidesSettingsFromBundlesAndRejectsGenericSettingsWritesBeforeAnyMutation()
    {
        service.update(settings("private-local-test-key"));
        TcmSettingsServiceImpl general = new TcmSettingsServiceImpl();
        ReflectionTestUtils.setField(general, "settingMapper", mapper);
        TcmRoomMapper rooms = mock(TcmRoomMapper.class);
        TcmServiceTypeMapper types = mock(TcmServiceTypeMapper.class);
        TcmPriceListMapper prices = mock(TcmPriceListMapper.class);
        when(rooms.selectTcmRoomList(any())).thenReturn(List.of());
        when(types.selectTcmServiceTypeList(any())).thenReturn(List.of());
        when(prices.selectTcmPriceListList(any())).thenReturn(List.of());
        when(mapper.selectAllSettings()).thenReturn(new ArrayList<>(records.values()));
        ReflectionTestUtils.setField(general, "roomMapper", rooms);
        ReflectionTestUtils.setField(general, "serviceTypeMapper", types);
        ReflectionTestUtils.setField(general, "priceListMapper", prices);
        assertFalse(general.getBundle().containsKey("smtpSettings"));
        assertFalse(general.updateBaseSettings(Map.of()).containsKey("smtpSettings"));
        clearInvocations(mapper);
        assertThrows(ServiceException.class, () -> general.updateBaseSettings(Map.of("clinicName", "changed", "smtpSettings", "plaintext")));
        verify(mapper, never()).insertSetting(any());
        verify(mapper, never()).updateSetting(any());
    }

    @Test void emailDeliveryUsesTheSelectedSenderAndNeverLogsCredentialBearingExceptions() throws Exception
    {
        TcmEmailServiceImpl delivery = new TcmEmailServiceImpl();
        TcmSmtpSettingsService dynamic = mock(TcmSmtpSettingsService.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(call -> new MimeMessage(Session.getInstance(new Properties())));
        when(dynamic.transport()).thenReturn(new TcmSmtpSettingsService.MailTransport(sender, "new@example.invalid"));
        TcmEmailLogMapper logs = mock(TcmEmailLogMapper.class);
        ReflectionTestUtils.setField(delivery, "smtpSettingsService", dynamic);
        ReflectionTestUtils.setField(delivery, "emailLogMapper", logs);
        assertTrue(delivery.sendAndLog("qa@example.invalid", "Test", "Body", "acceptance"));
        ArgumentCaptor<MimeMessage> message = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(message.capture());
        assertEquals("new@example.invalid", message.getValue().getFrom()[0].toString());
        doThrow(new RuntimeException("provider echoed private-local-key")).when(sender).send(any(MimeMessage.class));
        assertFalse(delivery.sendAndLog("qa@example.invalid", "Test", "Body", "acceptance"));
        ArgumentCaptor<TcmEmailLog> captured = ArgumentCaptor.forClass(TcmEmailLog.class);
        verify(logs, times(2)).insertTcmEmailLog(captured.capture());
        assertNull(captured.getAllValues().get(1).getSentAt());
        assertFalse(captured.getAllValues().get(1).getPayload().contains("private-local-key"));
    }
}
