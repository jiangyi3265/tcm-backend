package com.ruoyi.hospital.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.mail.*;
import javax.mail.internet.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mail.javamail.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import com.alibaba.fastjson2.JSONObject;

/** A loopback-only SMTP fixture; no external network, real mailbox or credentials. */
class TcmSmtpDeliveryDeadlineTest
{
    @TempDir Path directory;

    @Test void slowFinalAcknowledgementReceivesOneCompleteInvoiceWithoutPrematureTimeoutOrResend() throws Exception
    {
        TcmSmtpSettingsService settings = new TcmSmtpSettingsService();
        ReflectionTestUtils.setField(settings, "environment", new MockEnvironment());
        byte[] expected = new byte[32768]; new Random(17).nextBytes(expected);
        Path pdf = directory.resolve("invoice-fixture.pdf"); Files.write(pdf, expected);
        try (FakeSmtp smtp = new FakeSmtp())
        {
            JSONObject config = new JSONObject();
            config.put("host", "127.0.0.1"); config.put("port", smtp.listener.getLocalPort());
            config.put("username", "synthetic"); config.put("security", "ssl");
            JavaMailSenderImpl sender = settings.buildSender(config, "synthetic-unused-password");
            // Only the loopback fixture omits TLS/auth. Keep the production-built timeouts unchanged.
            Properties properties = sender.getJavaMailProperties();
            properties.setProperty("mail.smtp.auth", "false");
            properties.setProperty("mail.smtp.ssl.enable", "false");
            properties.setProperty("mail.smtp.starttls.enable", "false");
            properties.setProperty("mail.smtp.starttls.required", "false");
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom("sender@example.invalid"); helper.setTo("recipient@example.invalid");
            helper.setSubject("Synthetic invoice"); helper.setText("Fixture only");
            helper.addAttachment("invoice-fixture.pdf", pdf.toFile());

            assertDoesNotThrow(() -> sender.send(message),
                    "A valid final acknowledgement arriving after six seconds must not be cut off at five");

            assertEquals(1, smtp.messages, "Do not hide a premature timeout by resending the invoice");
            assertNotNull(smtp.message);
            assertTrue(hasAttachment(new MimeMessage(Session.getInstance(new Properties()),
                    new ByteArrayInputStream(smtp.message)), expected));
        }
    }

    private boolean hasAttachment(Part part, byte[] expected) throws Exception
    {
        if ("invoice-fixture.pdf".equals(part.getFileName()))
            return Arrays.equals(expected, part.getInputStream().readAllBytes());
        if (part.isMimeType("multipart/*"))
        {
            Multipart multipart = (Multipart) part.getContent();
            for (int i = 0; i < multipart.getCount(); i++)
                if (hasAttachment(multipart.getBodyPart(i), expected)) return true;
        }
        return false;
    }

    private static class FakeSmtp implements AutoCloseable
    {
        final ServerSocket listener;
        final ExecutorService worker = Executors.newSingleThreadExecutor();
        final Future<?> running;
        volatile int messages;
        volatile byte[] message;

        FakeSmtp() throws Exception
        {
            listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            listener.setSoTimeout(15000);
            running = worker.submit(() -> {
                try (Socket socket = listener.accept())
                {
                    socket.setSoTimeout(15000);
                    BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                    PrintWriter output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII), true);
                    reply(output, "220 fixture ESMTP");
                    String line;
                    while ((line = input.readLine()) != null)
                    {
                        if (line.startsWith("EHLO") || line.startsWith("HELO")) reply(output, "250 fixture");
                        else if (line.startsWith("MAIL FROM:") || line.startsWith("RCPT TO:") || line.equals("RSET")) reply(output, "250 OK");
                        else if (line.equals("DATA"))
                        {
                            reply(output, "354 send message");
                            StringBuilder body = new StringBuilder();
                            while ((line = input.readLine()) != null && !line.equals("."))
                                body.append(line.startsWith("..") ? line.substring(1) : line).append("\r\n");
                            message = body.toString().getBytes(StandardCharsets.US_ASCII);
                            messages++;
                            Thread.sleep(6000);
                            reply(output, "250 accepted");
                        }
                        else if (line.equals("QUIT")) { reply(output, "221 bye"); break; }
                        else throw new IllegalStateException("Unexpected synthetic SMTP command");
                    }
                }
                catch (SocketException disconnected)
                {
                    // The regression's old five-second client can reset after the fixture receives DATA.
                    if (messages == 0) throw new RuntimeException(disconnected);
                }
                catch (Exception error) { throw new RuntimeException(error); }
            });
        }

        private static void reply(PrintWriter output, String line) { output.print(line + "\r\n"); output.flush(); }

        @Override public void close() throws Exception
        {
            listener.close(); worker.shutdown();
            try { running.get(20, TimeUnit.SECONDS); }
            finally { worker.shutdownNow(); }
        }
    }
}
