package com.ruoyi.hospital.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HospitalFileStorageTest
{
    private final HospitalFileStorage storage = new HospitalFileStorage();

    @Test
    void createPatientFileResourceKey_shouldGroupReceiptsByPatientAndYear()
    {
        String key = storage.createPatientFileResourceKey(
                "Alice Zhang",
                "invoice_pdf",
                "2026-06-08 10:30:00",
                ".pdf");

        assertTrue(key.startsWith("hospital-private/Alice_Zhang/Receipt/2026/invoice_pdf_"));
        assertTrue(key.endsWith(".pdf"));
        assertFalse(key.contains("/2026/06/08/"));
    }

    @Test
    void createPatientFileResourceKey_shouldKeepConsentFormsWithoutYearFolder()
    {
        String key = storage.createPatientFileResourceKey(
                "张三",
                "consent_pdf",
                "2026-06-08",
                ".pdf");

        assertTrue(key.startsWith("hospital-private/张三/Consent Form/consent_pdf_"));
        assertTrue(key.endsWith(".pdf"));
        assertFalse(key.contains("/Consent Form/2026/"));
    }

    @Test
    void createPatientFileResourceKey_shouldPlaceGenericDocumentsUnderConsultationYear()
    {
        String key = storage.createPatientFileResourceKey(
                "Patient / Unsafe:Name",
                "document",
                "2025-12-01",
                ".png");

        assertTrue(key.startsWith("hospital-private/Patient_Unsafe_Name/Consultation/2025/document_"));
        assertTrue(key.endsWith(".png"));
    }
}
