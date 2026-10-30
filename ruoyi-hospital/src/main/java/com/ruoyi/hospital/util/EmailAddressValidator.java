package com.ruoyi.hospital.util;

import java.util.regex.Pattern;

/** Single plain mailbox syntax only; never resolves DNS or rewrites an address. */
public final class EmailAddressValidator
{
    public static final String INVALID_RECIPIENT = "INVALID_RECIPIENT";
    private static final String ATOM = "[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+";
    private static final String LABEL = "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?";
    private static final Pattern MAILBOX = Pattern.compile(ATOM + "(?:\\." + ATOM + ")*@" + LABEL + "(?:\\." + LABEL + ")+");

    private EmailAddressValidator() { }

    public static boolean isValid(String value)
    {
        if (value == null || value.length() > 254) return false;
        int at = value.indexOf('@');
        return at > 0 && at <= 64 && at == value.lastIndexOf('@') && MAILBOX.matcher(value).matches();
    }
}
