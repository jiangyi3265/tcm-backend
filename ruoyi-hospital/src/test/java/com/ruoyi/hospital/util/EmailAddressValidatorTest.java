package com.ruoyi.hospital.util;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class EmailAddressValidatorTest
{
    @Test void acceptsSinglePlainAddressesIncludingPlusAndReservedTestDomains()
    {
        for (String address : new String[] {"first.last+family@clinic.ca", "A_B@example.com", "o'connor@clinic.ca",
                "patient@example.invalid", "user@xn--bcher-kva.example", "user@sub.clinic.ca"})
            assertTrue(EmailAddressValidator.isValid(address), address);
    }

    @Test void rejectsNamesListsWhitespaceMissingAtAndInvalidLabelShape()
    {
        for (String address : new String[] {null, "", "Patient Name", "patient", "x@@clinic.ca", "x@clinic.ca,y@clinic.ca",
                "Name <x@clinic.ca>", " x@clinic.ca", "x @clinic.ca", "x\u00a0@clinic.ca", "x@clinic.ca\r\nBcc:other@clinic.ca",
                "x..y@clinic.ca", ".x@clinic.ca", "x@-clinic.ca", "x@clinic..ca", "x@clinic.ca;other@clinic.ca"})
            assertFalse(EmailAddressValidator.isValid(address), String.valueOf(address));
    }

    @Test void boundsLocalPartAndDomainLabelsWithoutDnsLookups()
    {
        assertTrue(EmailAddressValidator.isValid("a".repeat(64) + "@clinic.ca"));
        assertFalse(EmailAddressValidator.isValid("a".repeat(65) + "@clinic.ca"));
        assertFalse(EmailAddressValidator.isValid("a@" + "b".repeat(64) + ".ca"));
    }
}
