package com.ruoyi.hospital.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.mail.MessagingException;
import org.springframework.mail.MailSendException;
import com.sun.mail.smtp.SMTPAddressFailedException;
import com.sun.mail.smtp.SMTPSenderFailedException;
import com.sun.mail.smtp.SMTPSendFailedException;

/** Fixed diagnostic values only: exception messages can contain credentials and patient data. */
public final class EmailFailureDiagnostics
{
    private static final Set<String> ALLOWED_TYPES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "org.springframework.mail.MailSendException", "org.springframework.mail.MailAuthenticationException",
            "org.springframework.mail.MailParseException", "org.springframework.mail.MailPreparationException",
            "javax.mail.AuthenticationFailedException", "javax.mail.MessagingException", "javax.mail.SendFailedException",
            "javax.mail.NoSuchProviderException", "javax.mail.internet.AddressException",
            "com.sun.mail.util.MailConnectException", "com.sun.mail.smtp.SMTPAddressFailedException",
            "com.sun.mail.smtp.SMTPSenderFailedException", "com.sun.mail.smtp.SMTPSendFailedException",
            "java.net.SocketTimeoutException", "java.net.ConnectException", "java.net.UnknownHostException",
            "java.net.SocketException", "javax.net.ssl.SSLHandshakeException", "javax.net.ssl.SSLException",
            "java.io.IOException", "java.io.FileNotFoundException", "java.io.UnsupportedEncodingException",
            "java.nio.file.NoSuchFileException", "java.nio.file.AccessDeniedException",
            "java.lang.IllegalArgumentException", "java.lang.IllegalStateException", "java.lang.NullPointerException")));

    private EmailFailureDiagnostics() { }

    public static Map<String, Object> describe(Throwable failure)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        String causeType = safeType(failure);
        result.put("deliveryErrorType", causeType);
        List<Throwable> pending = new ArrayList<>();
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        enqueue(pending, seen, failure);
        for (int index = 0; index < pending.size() && index < 16; index++)
        {
            Throwable error = pending.get(index);
            String type = safeType(error);
            if (!"UnknownException".equals(type) && !result.containsKey("smtpCode")) causeType = type;
            int code = error instanceof SMTPSendFailedException ? ((SMTPSendFailedException) error).getReturnCode()
                    : error instanceof SMTPAddressFailedException ? ((SMTPAddressFailedException) error).getReturnCode()
                    : error instanceof SMTPSenderFailedException ? ((SMTPSenderFailedException) error).getReturnCode() : 0;
            if (code >= 400 && code <= 599 && !result.containsKey("smtpCode"))
            {
                result.put("smtpCode", Integer.toString(code));
                causeType = type;
            }
            enqueue(pending, seen, error.getCause());
            if (error instanceof MessagingException)
                enqueue(pending, seen, ((MessagingException) error).getNextException());
            if (error instanceof MailSendException)
                for (Exception nested : ((MailSendException) error).getMessageExceptions())
                    enqueue(pending, seen, nested);
        }
        result.put("deliveryCauseType", causeType);
        return result;
    }

    private static void enqueue(List<Throwable> pending, Set<Throwable> seen, Throwable error)
    {
        if (error != null && pending.size() < 16 && seen.add(error)) pending.add(error);
    }

    private static String safeType(Throwable error)
    {
        if (error == null || !ALLOWED_TYPES.contains(error.getClass().getName())) return "UnknownException";
        return error.getClass().getSimpleName();
    }
}
