package com.ruoyi.hospital.service.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.io.image.ImageDataFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.hospital.domain.TcmClinicSetting;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.domain.TcmPatientFile;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
import com.ruoyi.hospital.mapper.TcmConsultationMapper;
import com.ruoyi.hospital.mapper.TcmPatientMapper;
import com.ruoyi.hospital.service.ITcmPdfService;
import com.ruoyi.hospital.service.ITcmPatientFileService;
import com.ruoyi.hospital.util.ConsentDocumentTemplate;
import com.ruoyi.hospital.util.HospitalFileStorage;
import com.ruoyi.hospital.util.SignedFileUrlService;
import com.ruoyi.system.service.ISysUserService;

@Service
public class TcmPdfServiceImpl implements ITcmPdfService
{
    private static final Logger log = LoggerFactory.getLogger(TcmPdfServiceImpl.class);
    private static final DeviceRgb PRIMARY_COLOR = new DeviceRgb(45, 106, 79);
    private static final DeviceRgb SOFT_GREEN = new DeviceRgb(239, 246, 241);
    private static final DeviceRgb BORDER_GREEN = new DeviceRgb(220, 232, 224);
    private static final DeviceRgb MUTED_TEXT = new DeviceRgb(89, 98, 92);

    @Autowired
    private TcmConsultationMapper consultationMapper;
    @Autowired
    private TcmPatientMapper patientMapper;
    @Autowired
    private TcmClinicSettingMapper settingMapper;
    @Autowired
    private SignedFileUrlService signedFileUrlService;
    @Autowired
    private HospitalFileStorage hospitalFileStorage;
    @Autowired
    private ISysUserService userService;

    @Autowired
    private ITcmPatientFileService patientFileService;

    @Override
    public Map<String, String> generateConsultationReport(String consultationId)
    {
        TcmConsultation consultation = consultationMapper.selectTcmConsultationById(consultationId);
        if (consultation == null)
        {
            throw new ServiceException("consultation not found");
        }

        JSONObject payload = parsePayload(consultation.getPayload());
        TcmPatient patient = patientMapper.selectTcmPatientById(consultation.getPatientId());
        JSONObject patientPayload = parsePayload(patient != null ? patient.getPayload() : null);
        String clinicName = getClinicName();
        JSONObject practitionerProfile = resolvePractitionerProfile(consultation);
        String resourcePath = hospitalFileStorage.createResourceKey("report", ".pdf");
        String filePath = hospitalFileStorage.resolve(resourcePath).toString();
        ensureDir(filePath);

        try
        {
            PdfWriter writer = new PdfWriter(new FileOutputStream(filePath));
            PdfDocument pdf = new PdfDocument(writer);
            Document doc = new Document(pdf);
            PdfFont font = createFont();

            addHeader(doc, font, clinicName, "Clinical Record: Consultation");
            addReportClinicInfo(doc, font, clinicName);
            addPractitionerReimbursementLine(doc, font, practitionerProfile);
            addConsultationInfo(doc, font, consultation, patient, patientPayload);
            addReportPractitionerInfo(doc, font, practitionerProfile);
            addChiefComplaintSection(doc, font, payload);
            addInitialIntakeSection(doc, font, patientPayload, payload);
            addConsultationRecordSection(doc, font, payload);
            addDifferentiationSection(doc, font, payload);
            addAcupunctureSection(doc, font, payload.getJSONArray("acupuncture"));
            addTreatmentInfoSection(doc, font, payload);
            addFormulaCompositionSection(doc, font, payload);
            addPrescriptionSummary(doc, font, payload.getJSONArray("prescriptions"));
            addOptionalSection(doc, font, "Prognosis", safeStr(payload, "prognosis"));
            addOptionalSection(doc, font, "Follow Up", firstNonBlank(
                    safeStr(payload, "followUp"),
                    safeStr(payload, "followUpAdvice"),
                    safeStr(payload, "aftercare")));
            addPractitionerSignature(doc, font, practitionerProfile);
            addClinicSeal(doc);
            doc.close();
            log.info("Consultation report PDF generated: {}", filePath);
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            log.error("Failed to generate consultation report PDF", e);
            throw new ServiceException("PDF generation failed: " + e.getMessage());
        }

        Map<String, String> result = buildResult(resourcePath);
        updateConsultationPdfMeta(consultation, result, "report");
        insertConsultationFileRecord(consultation, "consultation_report_pdf", "consultation-report", resourcePath);
        return result;
    }

    @Override
    public Map<String, String> generateInvoice(String consultationId)
    {
        TcmConsultation consultation = consultationMapper.selectTcmConsultationById(consultationId);
        if (consultation == null)
        {
            throw new ServiceException("consultation not found");
        }

        TcmPatient patient = patientMapper.selectTcmPatientById(consultation.getPatientId());
        JSONObject payload = parsePayload(consultation.getPayload());
        String currency = resolveCurrency(payload);
        JSONObject patientPayload = parsePayload(patient != null ? patient.getPayload() : null);
        String clinicName = getClinicName();
        String clinicAddress = getClinicSetting("clinicAddress");
        String clinicPhone = getClinicSetting("clinicPhone");

        JSONObject practitionerProfile = resolvePractitionerProfile(consultation);

        String resourcePath = hospitalFileStorage.createResourceKey("invoice", ".pdf");
        String filePath = hospitalFileStorage.resolve(resourcePath).toString();
        ensureDir(filePath);

        try
        {
            PdfWriter writer = new PdfWriter(new FileOutputStream(filePath));
            PdfDocument pdf = new PdfDocument(writer);
            Document doc = new Document(pdf);
            PdfFont font = createFont();

            addHeader(doc, font, clinicName, "Invoice");
            addInvoiceClinicInfo(doc, font, clinicName, clinicAddress, clinicPhone, practitionerProfile);
            addPractitionerReimbursementLine(doc, font, practitionerProfile);
            addConsultationInfo(doc, font, consultation, patient, patientPayload);
            addInvoiceBillTo(doc, font, patient, patientPayload);
            addInvoiceItems(doc, font, payload, currency);
            addInvoiceTotals(doc, font, payload, currency);
            addPractitionerSignature(doc, font, practitionerProfile);
            addClinicSeal(doc);
            addConfiguredFooterImage(doc);
            doc.close();
            log.info("Invoice PDF generated: {}", filePath);
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            log.error("Failed to generate invoice PDF", e);
            throw new ServiceException("PDF generation failed: " + e.getMessage());
        }

        Map<String, String> result = buildResult(resourcePath);
        insertConsultationFileRecord(consultation, "invoice_pdf", "invoice", resourcePath);
        return result;
    }

    @Override
    public Map<String, String> generateConsentForm(String patientId, String signatureName)
    {
        TcmPatient patient = patientMapper.selectTcmPatientById(patientId);
        if (patient == null)
        {
            throw new ServiceException("patient not found");
        }

        String clinicName = getClinicName();
        String clinicAddress = getClinicSetting("clinicAddress");
        String clinicPhone = getClinicSetting("clinicPhone");
        String resourcePath = hospitalFileStorage.createResourceKey("consent", ".pdf");
        String filePath = hospitalFileStorage.resolve(resourcePath).toString();
        ensureDir(filePath);

        String signedAt = patient.getConsentSignedAt() != null
                ? patient.getConsentSignedAt()
                : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        JSONObject patientPayload = parsePayload(patient.getPayload());
        Object configuredConsentTemplate = getClinicSetting("consentTemplate");
        String consentTitle = StringUtils.defaultIfBlank(
                patientPayload.getString("consentDocumentTitle"),
                ConsentDocumentTemplate.getTitle(configuredConsentTemplate));
        String consentVersion = StringUtils.defaultIfBlank(
                patientPayload.getString("consentVersion"),
                ConsentDocumentTemplate.getVersion(configuredConsentTemplate));
        String displaySignature = StringUtils.defaultIfBlank(
                StringUtils.defaultIfBlank(signatureName, patientPayload.getString("consentSignatureName")),
                patient.getName());
        List<Map<String, Object>> sections = extractConsentSections(
                patientPayload.get("consentDocumentSections"),
                configuredConsentTemplate);

        try
        {
            PdfWriter writer = new PdfWriter(new FileOutputStream(filePath));
            PdfDocument pdf = new PdfDocument(writer);
            Document doc = new Document(pdf);
            PdfFont font = createFont();

            addHeader(doc, font, clinicName, consentTitle);
            doc.add(new Paragraph("Patient / 患者：" + safeValue(patient.getName())).setFont(font).setFontSize(11));
            doc.add(new Paragraph("Version / 版本：" + safeValue(consentVersion)).setFont(font).setFontSize(10));
            if (StringUtils.isNotBlank(clinicAddress))
            {
                doc.add(new Paragraph("Clinic address / 诊所地址：" + clinicAddress).setFont(font).setFontSize(10));
            }
            if (StringUtils.isNotBlank(clinicPhone))
            {
                doc.add(new Paragraph("Phone / 联系电话：" + clinicPhone).setFont(font).setFontSize(10));
            }

            addSectionTitle(doc, font, "Consent Content / 同意书内容");
            int index = 1;
            for (Map<String, Object> section : sections)
            {
                String sectionTitle = section.get("title") != null ? String.valueOf(section.get("title")) : ("Section " + index);
                addConsentSectionHeader(doc, font, index, sectionTitle);
                Object paragraphsObj = section.get("paragraphs");
                if (paragraphsObj instanceof List<?>)
                {
                    for (Object item : (List<?>) paragraphsObj)
                    {
                        if (item != null && !String.valueOf(item).trim().isEmpty())
                        {
                            doc.add(new Paragraph(String.valueOf(item).trim()).setFont(font).setFontSize(10));
                        }
                    }
                }
                addConsentAcknowledgement(doc, font, true);
                index++;
            }

            addClinicSeal(doc);
            addConsentSignatureCard(doc, font, displaySignature, signedAt);
            doc.close();
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            log.error("Failed to generate consent PDF", e);
            throw new ServiceException("PDF generation failed: " + e.getMessage());
        }

        Map<String, String> result = buildResult(resourcePath);
        updatePatientConsentMeta(patient, result);
        insertConsentFileRecord(patient, resourcePath);
        return result;
    }

    private List<Map<String, Object>> extractConsentSections(Object sectionsObj, Object configuredConsentTemplate)
    {
        if (sectionsObj instanceof List<?>)
        {
            List<Map<String, Object>> sections = new java.util.ArrayList<>();
            for (Object item : (List<?>) sectionsObj)
            {
                if (item instanceof Map<?, ?>)
                {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> section = (Map<String, Object>) item;
                    sections.add(section);
                }
                else if (item instanceof JSONObject)
                {
                    sections.add(new HashMap<>((JSONObject) item));
                }
            }
            if (!sections.isEmpty())
            {
                return sections;
            }
        }
        return ConsentDocumentTemplate.toResponseSections(configuredConsentTemplate);
    }

    private void addConsentSectionHeader(Document doc, PdfFont font, int index, String title)
    {
        Table header = new Table(UnitValue.createPercentArray(new float[] { 0.4f, 5f })).useAllAvailableWidth();
        header.setMarginTop(8).setMarginBottom(4);
        header.addCell(new Cell()
                .add(new Paragraph(String.valueOf(index)).setFont(font).setFontSize(11).setBold()
                        .setFontColor(ColorConstants.WHITE).setTextAlignment(TextAlignment.CENTER))
                .setBackgroundColor(PRIMARY_COLOR)
                .setBorder(Border.NO_BORDER)
                .setPadding(6));
        header.addCell(new Cell()
                .add(new Paragraph(title).setFont(font).setFontSize(11).setBold().setFontColor(PRIMARY_COLOR))
                .setBackgroundColor(new DeviceRgb(248, 251, 248))
                .setBorder(new SolidBorder(new DeviceRgb(220, 232, 224), 1))
                .setPadding(6));
        doc.add(header);
    }

    private void addConsentAcknowledgement(Document doc, PdfFont font, boolean agreed)
    {
        Table table = new Table(UnitValue.createPercentArray(new float[] { 0.25f, 5f })).useAllAvailableWidth();
        table.setMarginTop(6).setMarginBottom(6);
        table.addCell(new Cell()
                .add(new Paragraph(agreed ? "X" : "").setFont(font).setFontSize(9).setBold()
                        .setTextAlignment(TextAlignment.CENTER))
                .setBorder(new SolidBorder(PRIMARY_COLOR, 1))
                .setBackgroundColor(agreed ? new DeviceRgb(232, 244, 237) : ColorConstants.WHITE)
                .setPadding(2));
        table.addCell(new Cell()
                .add(new Paragraph("I have read and agree. / 我已阅读并同意")
                        .setFont(font).setFontSize(10).setBold().setFontColor(new DeviceRgb(39, 68, 55)))
                .setBorder(Border.NO_BORDER)
                .setPaddingLeft(8));
        doc.add(table);
    }

    private void addConsentSignatureCard(Document doc, PdfFont font, String displaySignature, String signedAt)
    {
        PdfFont signatureFont = createSignatureFont(displaySignature, font);
        Table signature = new Table(UnitValue.createPercentArray(new float[] { 1 })).useAllAvailableWidth();
        signature.setMarginTop(28).setMarginBottom(4).setKeepTogether(true);
        signature.addCell(new Cell()
                .add(new Paragraph("Signature / 签名").setFont(font).setFontSize(9).setBold().setFontColor(MUTED_TEXT)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBorder(Border.NO_BORDER)
                .setPadding(0));
        signature.addCell(new Cell()
                .add(new Paragraph(safeValue(displaySignature)).setFont(signatureFont).setFontSize(28)
                        .setFontColor(PRIMARY_COLOR).setTextAlignment(TextAlignment.RIGHT))
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(PRIMARY_COLOR, 0.8f))
                .setPadding(4));
        signature.addCell(new Cell()
                .add(new Paragraph("Date / 日期时间: " + safeValue(signedAt)).setFont(font).setFontSize(10)
                        .setFontColor(MUTED_TEXT).setTextAlignment(TextAlignment.RIGHT))
                .setBorder(Border.NO_BORDER)
                .setPaddingTop(6));
        doc.add(signature);
    }

    private Map<String, String> buildResult(String resourcePath)
    {
        Map<String, String> result = new HashMap<>();
        result.put("filePath", resourcePath);
        result.put("resource", resourcePath);
        result.put("url", signedFileUrlService.buildAccessUrl(resourcePath));
        return result;
    }

    private void addInvoiceClinicInfo(Document doc, PdfFont font, String clinicName, String clinicAddress, String clinicPhone, JSONObject practitionerProfile)
    {
        StringBuilder clinic = new StringBuilder();
        clinic.append(clinicName);
        appendLine(clinic, clinicAddress);
        appendLine(clinic, clinicPhone);
        appendLine(clinic, getClinicSetting("clinicEmail"));
        appendLine(clinic, getClinicSetting("clinicOrganization"));
        appendLine(clinic, getClinicSetting("clinicOrganizationNumber"));
        appendLabeledLine(clinic, "HST No.", getClinicSetting("clinicTaxNumber"));
        appendLabeledLine(clinic, "Business No.", getClinicSetting("invoiceBusinessNumber"));

        StringBuilder practitioner = new StringBuilder();
        appendLabeledLine(practitioner, "Practitioner / 中医师", practitionerProfile.getString("practitionerName"));
        appendLabeledLine(practitioner, "Title / 职称", practitionerProfile.getString("title"));
        appendLabeledLine(practitioner, "Practitioner Email / 邮箱", practitionerProfile.getString("practitionerEmail"));
        appendLabeledLine(practitioner, "Practitioner Phone / 电话", practitionerProfile.getString("practitionerPhone"));
        appendLabeledLine(practitioner, "Organization / 注册机构", practitionerProfile.getString("organization"));
        appendLabeledLine(practitioner, "Organization No. / 注册号", practitionerProfile.getString("organizationNumber"));
        String regBody = cleanText(practitionerProfile.getString("regulatoryBody"));
        String regNumber = cleanText(practitionerProfile.getString("registrationNumber"));
        if (regBody != null || regNumber != null)
        {
            practitioner.append(practitioner.length() > 0 ? "\n" : "");
            practitioner.append("Registration / 执业注册: ");
            if (regBody != null) practitioner.append(regBody);
            if (regBody != null && regNumber != null) practitioner.append(" # ");
            if (regNumber != null) practitioner.append(regNumber);
        }

        Table table = new Table(UnitValue.createPercentArray(new float[] { 1, 1 })).useAllAvailableWidth();
        table.setMarginBottom(8);
        table.addCell(new Cell()
                .add(new Paragraph(clinic.toString()).setFont(font).setFontSize(10))
                .setBorder(new SolidBorder(BORDER_GREEN, 0.8f))
                .setBackgroundColor(new DeviceRgb(250, 252, 250))
                .setPadding(8));
        table.addCell(new Cell()
                .add(new Paragraph(practitioner.length() > 0 ? practitioner.toString() : "-").setFont(font).setFontSize(10))
                .setBorder(new SolidBorder(BORDER_GREEN, 0.8f))
                .setBackgroundColor(new DeviceRgb(250, 252, 250))
                .setPadding(8));
        doc.add(table);
    }

    private void appendLine(StringBuilder sb, String value)
    {
        if (hasMeaningfulValue(value))
        {
            sb.append("\n").append(value.trim());
        }
    }

    private void appendLabeledLine(StringBuilder sb, String label, String value)
    {
        if (hasMeaningfulValue(value))
        {
            sb.append("\n").append(label).append(": ").append(String.valueOf(value).trim());
        }
    }

    private void addInvoiceBillTo(Document doc, PdfFont font, TcmPatient patient, JSONObject patientPayload)
    {
        if (patient == null) return;
        StringBuilder sb = new StringBuilder("Bill To: ");
        sb.append(safeValue(patient.getName()));
        if (patient.getEmail() != null && !patient.getEmail().isEmpty()) sb.append("  |  ").append(patient.getEmail());
        if (patient.getPhone() != null && !patient.getPhone().isEmpty()) sb.append("  |  ").append(patient.getPhone());
        String street = patientPayload.getString("addressStreet");
        if (street == null || street.isEmpty()) street = patientPayload.getString("address");
        String city = patientPayload.getString("addressCity");
        String state = patientPayload.getString("addressState");
        String postal = patientPayload.getString("addressPostal");
        StringBuilder addr = new StringBuilder();
        if (street != null && !street.isEmpty()) addr.append(street);
        if (city != null && !city.isEmpty()) { if (addr.length() > 0) addr.append(", "); addr.append(city); }
        if (state != null && !state.isEmpty()) { if (addr.length() > 0) addr.append(", "); addr.append(state); }
        if (postal != null && !postal.isEmpty()) { if (addr.length() > 0) addr.append(", "); addr.append(postal); }
        if (addr.length() > 0) sb.append("\n").append(addr);
        doc.add(new Paragraph(sb.toString()).setFont(font).setFontSize(10)
                .setBackgroundColor(new DeviceRgb(249, 249, 249)).setPadding(6).setMarginBottom(8));
    }

    private void addReportClinicInfo(Document doc, PdfFont font, String clinicName)
    {
        Table table = new Table(UnitValue.createPercentArray(new float[] { 1, 1 })).useAllAvailableWidth();
        addInfoRow(table, font, "Clinic / 诊所", clinicName);
        addInfoRow(table, font, "Clinic Address / 诊所地址", getClinicSetting("clinicAddress"));
        addInfoRow(table, font, "Clinic Phone / 诊所电话", getClinicSetting("clinicPhone"));
        addInfoRow(table, font, "Clinic Email / 诊所邮箱", getClinicSetting("clinicEmail"));
        addInfoRow(table, font, "Clinic Organization / 诊所机构", getClinicSetting("clinicOrganization"));
        addInfoRow(table, font, "Business / HST No.", firstNonBlank(
                getClinicSetting("clinicTaxNumber"),
                getClinicSetting("invoiceBusinessNumber"),
                getClinicSetting("clinicOrganizationNumber")));
        doc.add(table);
    }

    private void addConsultationInfo(Document doc, PdfFont font, TcmConsultation consultation, TcmPatient patient, JSONObject patientPayload)
    {
        Table infoTable = new Table(UnitValue.createPercentArray(new float[] { 1, 1 })).useAllAvailableWidth();
        addInfoRow(infoTable, font, "Consultation ID", consultation.getConsultationId());
        addInfoRow(infoTable, font, "Consultation Date", consultation.getConsultDate());
        addInfoRow(infoTable, font, "Patient Name", patient != null ? patient.getName() : "-");
        addInfoRow(infoTable, font, "Date of Birth", patientPayload != null ? firstNonBlank(
                patientPayload.getString("dateOfBirth"),
                patientPayload.getString("birthDate"),
                patientPayload.getString("dob")) : null);
        addInfoRow(infoTable, font, "Patient Phone", firstNonBlank(
                patient != null ? patient.getPhone() : null,
                patientPayload != null ? patientPayload.getString("phone") : null,
                patientPayload != null ? patientPayload.getString("phoneNumber") : null,
                patientPayload != null ? patientPayload.getString("mobile") : null));
        addInfoRow(infoTable, font, "Patient Email", firstNonBlank(
                patient != null ? patient.getEmail() : null,
                patientPayload != null ? patientPayload.getString("email") : null));
        addInfoRow(infoTable, font, "Patient Address", buildPatientAddress(patientPayload));
        addInfoRow(infoTable, font, "Practitioner ID", consultation.getPractitionerId());
        addInfoRow(infoTable, font, "Status", consultation.getStatus());
        addInfoRow(infoTable, font, "Record Type", "Consultation");
        doc.add(infoTable);
    }

    private String buildPatientAddress(JSONObject patientPayload)
    {
        if (patientPayload == null)
        {
            return "-";
        }
        List<String> parts = new ArrayList<>();
        String street = firstNonBlank(
                patientPayload.getString("addressStreet"),
                patientPayload.getString("street"),
                patientPayload.getString("address"));
        if (hasMeaningfulValue(street))
        {
            parts.add(street);
        }
        addAddressPart(parts, patientPayload.getString("addressCity"));
        addAddressPart(parts, patientPayload.getString("city"));
        addAddressPart(parts, patientPayload.getString("addressState"));
        addAddressPart(parts, patientPayload.getString("province"));
        addAddressPart(parts, patientPayload.getString("addressPostal"));
        addAddressPart(parts, patientPayload.getString("postalCode"));
        if (!parts.isEmpty())
        {
            return String.join(", ", parts);
        }
        return firstNonBlank(patientPayload.getString("fullAddress"), patientPayload.getString("mailingAddress"));
    }

    private void addAddressPart(List<String> parts, String value)
    {
        if (hasMeaningfulValue(value) && !parts.contains(value.trim()))
        {
            parts.add(value.trim());
        }
    }

    private void addReportPractitionerInfo(Document doc, PdfFont font, JSONObject practitionerProfile)
    {
        if (practitionerProfile == null || practitionerProfile.isEmpty())
        {
            return;
        }
        Table table = new Table(UnitValue.createPercentArray(new float[] { 1, 1 })).useAllAvailableWidth();
        addInfoRow(table, font, "Practitioner / 中医师", practitionerProfile.getString("practitionerName"));
        addInfoRow(table, font, "Title / 职称", practitionerProfile.getString("title"));
        addInfoRow(table, font, "Organization / 注册机构", firstNonBlank(
                practitionerProfile.getString("organization"),
                practitionerProfile.getString("regulatoryBody")));
        addInfoRow(table, font, "Organization No. / 注册号", firstNonBlank(
                practitionerProfile.getString("organizationNumber"),
                practitionerProfile.getString("registrationNumber")));
        addInfoRow(table, font, "Practitioner Email / 邮箱", practitionerProfile.getString("practitionerEmail"));
        addInfoRow(table, font, "Practitioner Phone / 电话", practitionerProfile.getString("practitionerPhone"));
        doc.add(table);
    }

    private void addPractitionerReimbursementLine(Document doc, PdfFont font, JSONObject practitionerProfile)
    {
        if (practitionerProfile == null || practitionerProfile.isEmpty())
        {
            return;
        }

        List<String> parts = new ArrayList<>();
        appendLabeledPart(parts, "Practitioner", firstNonBlank(
                practitionerProfile.getString("practitionerName"),
                practitionerProfile.getString("name")));
        appendLabeledPart(parts, "Organization", firstNonBlank(
                practitionerProfile.getString("organization"),
                practitionerProfile.getString("regulatoryBody")));
        appendLabeledPart(parts, "Registration No.", firstNonBlank(
                practitionerProfile.getString("organizationNumber"),
                practitionerProfile.getString("registrationNumber"),
                practitionerProfile.getString("licenseNumber")));
        if (parts.isEmpty())
        {
            return;
        }

        doc.add(new Paragraph(String.join("  |  ", parts))
                .setFont(font)
                .setFontSize(10)
                .setBold()
                .setFontColor(PRIMARY_COLOR)
                .setBackgroundColor(new DeviceRgb(249, 249, 249))
                .setBorder(new SolidBorder(BORDER_GREEN, 0.8f))
                .setPadding(6)
                .setMarginBottom(8));
    }

    private void appendLabeledPart(List<String> parts, String label, String value)
    {
        String text = cleanText(value);
        if (text != null)
        {
            parts.add(label + ": " + text);
        }
    }

    private void addChiefComplaintSection(Document doc, PdfFont font, JSONObject payload)
    {
        List<String> lines = new ArrayList<>();
        appendKeyValue(lines, "Chief Complaint / 主诉", payload.get("chiefComplaint"));
        appendKeyValue(lines, "Duration / 持续时间", payload.get("chiefComplaintDuration"));
        appendKeyValue(lines, "Description / 描述", payload.get("chiefComplaintDescription"));
        appendKeyValue(lines, "Progress / 病程变化", payload.get("progressOfDisease"));
        addLinesSection(doc, font, "Chief Complaint", lines);
    }

    private void addInitialIntakeSection(Document doc, PdfFont font, JSONObject patientPayload, JSONObject consultationPayload)
    {
        JSONObject latestIntake = toJsonObject(patientPayload != null ? patientPayload.get("latestIntakeFormData") : null);
        if (latestIntake.isEmpty())
        {
            latestIntake = toJsonObject(consultationPayload.get("intakeFormData"));
        }

        List<String> lines = new ArrayList<>();
        appendKeyValue(lines, "Submitted At / 提交时间", patientPayload != null ? patientPayload.get("latestIntakeSubmittedAt") : null);
        appendKeyValue(lines, "Source / 来源", patientPayload != null ? patientPayload.get("latestIntakeSource") : null);

        appendFirstAvailable(lines, "Chief Complaint / 主诉", latestIntake, patientPayload, "chiefComplaint");
        appendFirstAvailable(lines, "Duration / 持续时间", latestIntake, patientPayload, "chiefComplaintDuration");
        appendFirstAvailable(lines, "Description / 描述", latestIntake, patientPayload, "chiefComplaintDescription");
        appendFirstAvailable(lines, "Progress / 病程", latestIntake, patientPayload, "progressOfDisease");
        appendFirstAvailable(lines, "Allergies / 过敏史", latestIntake, patientPayload, "allergies");
        appendFirstAvailable(lines, "Drug Allergies / 药物过敏", latestIntake, patientPayload, "drugAllergies");
        appendFirstAvailable(lines, "Other Allergies / 其他过敏", latestIntake, patientPayload, "otherAllergies");
        appendFirstAvailable(lines, "Medical History / 既往病史", latestIntake, patientPayload, "medicalHistory");
        appendFirstAvailable(lines, "Medical History Selections / 病史勾选", latestIntake, patientPayload, "medicalHistorySelections");
        appendFirstAvailable(lines, "Additional Medical History / 病史补充", latestIntake, patientPayload, "otherMedicalHistory");
        appendFirstAvailable(lines, "Current Medications / 当前用药", latestIntake, patientPayload, "currentMedications");
        appendFirstAvailable(lines, "Medication Selections / 用药勾选", latestIntake, patientPayload, "currentMedicationSelections");
        appendFirstAvailable(lines, "Medication Details / 用药详情", latestIntake, patientPayload, "medicationDetails");
        appendFirstAvailable(lines, "Family History / 家族史", latestIntake, patientPayload, "familyHistory");
        appendFirstAvailable(lines, "Metal Implants / 金属植入部位", latestIntake, patientPayload, "metalImplantsLocation");
        appendFirstAvailable(lines, "Implant Type / 植入物类型", latestIntake, patientPayload, "implantType");
        appendFirstAvailable(lines, "Smoking / 吸烟", latestIntake, patientPayload, "smokingStatus");
        appendFirstAvailable(lines, "Alcohol / 饮酒", latestIntake, patientPayload, "alcoholStatus");
        appendFirstAvailable(lines, "Exercise / 运动", latestIntake, patientPayload, "exerciseStatus");
        appendFirstAvailable(lines, "Lifestyle / 生活方式", latestIntake, patientPayload, "lifestyle");
        appendFirstAvailable(lines, "Lifestyle Notes / 生活方式补充", latestIntake, patientPayload, "lifestyleNotes");
        appendFirstAvailable(lines, "Currently Pregnant / 是否怀孕", latestIntake, patientPayload, "currentlyPregnant");
        appendFirstAvailable(lines, "Breastfeeding / 是否哺乳", latestIntake, patientPayload, "breastfeeding");
        appendFirstAvailable(lines, "Female Health / 女性专项", latestIntake, patientPayload, "femaleHealthSummary");
        appendFirstAvailable(lines, "Additional Notes / 其他补充", latestIntake, patientPayload, "additionalNotes");
        appendFirstAvailable(lines, "Signature / 签名", latestIntake, patientPayload, "signatureName");
        appendFirstAvailable(lines, "Signed Date / 签署日期", latestIntake, patientPayload, "signedDate");

        addLinesSection(doc, font, "Initial Intake / 初诊问诊记录", lines);
    }

    private void addConsultationRecordSection(Document doc, PdfFont font, JSONObject payload)
    {
        List<String> lines = new ArrayList<>();
        appendKeyValue(lines, "History and Medication / 病史与用药", firstMeaningful(
                payload.get("historyAndMedicationSnapshot"),
                payload.get("historyAndMedication"),
                payload.get("medicalHistory")));
        appendKeyValue(lines, "Assessment / 评估", payload.get("assessment"));
        appendKeyValue(lines, "Diagnosis / 诊断", payload.get("diagnosis"));
        appendKeyValue(lines, "Clinical Notes / 临床记录", payload.get("notes"));
        appendKeyValue(lines, "Comments / 备注", payload.get("comments"));
        appendKeyValue(lines, "Previous Treatment Review / 上次治疗反馈", payload.get("previousPrognosisReview"));
        appendKeyValue(lines, "Current Feedback / 本次反馈", payload.get("feedback"));
        addLinesSection(doc, font, "Consultation Record / 问诊记录", lines);
    }

    private void addDifferentiationSection(Document doc, PdfFont font, JSONObject payload)
    {
        JSONObject diff = payload.getJSONObject("diff");
        List<String> lines = new ArrayList<>();
        appendKeyValue(lines, "Differentiation / 辨证", payload.get("differentiation"));

        if (diff != null)
        {
            JSONArray conclusions = diff.getJSONArray("conclusions");
            if (conclusions != null)
            {
                for (int i = 0; i < conclusions.size(); i++)
                {
                    JSONObject conclusion = conclusions.getJSONObject(i);
                    if (conclusion == null)
                    {
                        continue;
                    }
                    List<String> parts = new ArrayList<>();
                    appendValuePart(parts, conclusion.get("name"));
                    if (hasMeaningfulValue(conclusion.get("treatment")))
                    {
                        parts.add("Treatment: " + formatStructuredValue(conclusion.get("treatment")));
                    }
                    if (!parts.isEmpty())
                    {
                        lines.add("Conclusion " + (i + 1) + ": " + String.join("; ", parts));
                    }
                }
            }

            String diffSummary = buildDiffSummary(diff);
            appendKeyValue(lines, "Findings / 四诊信息", diffSummary);
        }

        addLinesSection(doc, font, "Differentiation Conclusion / 辨证结论", lines);
    }

    private void addAcupunctureSection(Document doc, PdfFont font, JSONArray acupuncture)
    {
        if (acupuncture == null || acupuncture.isEmpty())
        {
            return;
        }

        Table table = new Table(UnitValue.createPercentArray(new float[] { 2, 1, 2 })).useAllAvailableWidth();
        boolean hasRows = false;
        addTableHeader(table, font, "Point / 穴位", "Side / 侧别", "Notes / 备注");
        for (int i = 0; i < acupuncture.size(); i++)
        {
            JSONObject item = acupuncture.getJSONObject(i);
            if (item == null || !hasAnyMeaningfulValue(item, "point", "side", "notes"))
            {
                continue;
            }
            addTableRow(table, font,
                    formatStructuredValue(item.get("point")),
                    formatStructuredValue(item.get("side")),
                    formatStructuredValue(item.get("notes")));
            hasRows = true;
        }
        if (hasRows)
        {
            addSectionTitle(doc, font, "Acupuncture Points / 针灸选穴");
            doc.add(table);
        }
    }

    private void addTreatmentInfoSection(Document doc, PdfFont font, JSONObject payload)
    {
        List<String> lines = new ArrayList<>();
        appendKeyValue(lines, "Treatment Plan / 治疗方案", firstMeaningful(
                payload.get("treatment"),
                payload.get("treatmentPlan"),
                payload.get("acupunctureTreatment")));
        appendKeyValue(lines, "Prognosis / 预后", payload.get("prognosis"));
        appendKeyValue(lines, "Follow Up / 复诊建议", firstMeaningful(
                payload.get("followUp"),
                payload.get("followUpAdvice"),
                payload.get("aftercare")));

        JSONArray services = payload.getJSONArray("services");
        if (services != null && !services.isEmpty())
        {
            List<String> serviceLines = new ArrayList<>();
            for (int i = 0; i < services.size(); i++)
            {
                JSONObject service = services.getJSONObject(i);
                if (service == null || !hasAnyMeaningfulValue(service, "name", "price", "quantity", "manualDiscount", "taxable"))
                {
                    continue;
                }
                List<String> parts = new ArrayList<>();
                appendValuePart(parts, service.get("name"));
                appendKeyValuePart(parts, "Price", service.get("price"));
                appendKeyValuePart(parts, "Qty", service.get("quantity"));
                appendKeyValuePart(parts, "Discount", service.get("manualDiscount"));
                appendKeyValuePart(parts, "Taxable", service.get("taxable"));
                serviceLines.add(String.join(", ", parts));
            }
            if (!serviceLines.isEmpty())
            {
                lines.add("Services / 治疗项目:\n" + String.join("\n", serviceLines));
            }
        }

        addLinesSection(doc, font, "Treatment / 治疗方案", lines);
    }

    private void addHerbalSection(Document doc, PdfFont font, JSONArray herbals, JSONObject payload)
    {
        if (herbals == null || herbals.isEmpty())
        {
            return;
        }
        addSectionTitle(doc, font, "Prescription");
        doc.add(new Paragraph(
                "Formula: " + safeStr(payload, "formulaName") + "    Type: " + safeStr(payload, "prescriptionType"))
                .setFont(font)
                .setFontSize(10));
        Table herbTable = new Table(UnitValue.createPercentArray(new float[] { 2, 1, 1 })).useAllAvailableWidth();
        addTableHeader(herbTable, font, "Herb", "Dosage", "Unit");
        for (int i = 0; i < herbals.size(); i++)
        {
            JSONObject herb = herbals.getJSONObject(i);
            addTableRow(
                    herbTable,
                    font,
                    herb.getString("name"),
                    String.valueOf(herb.get("dosage")),
                    herb.getString("unit"));
        }
        doc.add(herbTable);
    }

    private void addFormulaCompositionSection(Document doc, PdfFont font, JSONObject payload)
    {
        JSONArray prescriptions = payload.getJSONArray("prescriptions");
        boolean added = false;
        if (prescriptions != null && !prescriptions.isEmpty())
        {
            for (int i = 0; i < prescriptions.size(); i++)
            {
                JSONObject rx = prescriptions.getJSONObject(i);
                if (rx == null || "deleted".equals(rx.getString("rxStatus")) || rx.getString("deletedAt") != null)
                {
                    continue;
                }
                JSONArray items = rx.getJSONArray("items");
                boolean hasItems = items != null && !items.isEmpty();
                if (!hasItems && !hasAnyMeaningfulValue(rx, "formulaName", "prescriptionType", "direction", "whereToGet"))
                {
                    continue;
                }

                if (!added)
                {
                    addSectionTitle(doc, font, "Formula Composition / 方剂组成");
                    added = true;
                }

                List<String> headerParts = new ArrayList<>();
                appendKeyValuePart(headerParts, "Formula", firstMeaningful(rx.get("formulaName"), rx.get("name")));
                appendKeyValuePart(headerParts, "Type", rx.get("prescriptionType"));
                appendKeyValuePart(headerParts, "Direction", rx.get("direction"));
                appendKeyValuePart(headerParts, "Where To Get", rx.get("whereToGet"));
                appendKeyValuePart(headerParts, "Quantity", rx.get("quantity"));
                if (!headerParts.isEmpty())
                {
                    doc.add(new Paragraph(String.join("    ", headerParts)).setFont(font).setFontSize(10).setBold());
                }

                if (hasItems)
                {
                    Table itemTable = new Table(UnitValue.createPercentArray(new float[] { 2, 1, 1, 2 })).useAllAvailableWidth();
                    addTableHeader(itemTable, font, "Herb / 药材", "Dosage / 剂量", "Unit / 单位", "Notes / 备注");
                    boolean hasRows = false;
                    for (int j = 0; j < items.size(); j++)
                    {
                        JSONObject herb = items.getJSONObject(j);
                        if (herb == null || !hasAnyMeaningfulValue(herb, "name", "dosage", "unit", "notes"))
                        {
                            continue;
                        }
                        addTableRow(
                                itemTable,
                                font,
                                formatStructuredValue(herb.get("name")),
                                formatStructuredValue(herb.get("dosage")),
                                formatStructuredValue(herb.get("unit")),
                                firstNonBlank(
                                        formatStructuredValue(herb.get("notes")),
                                        formatStructuredValue(herb.get("supplierName")),
                                        formatStructuredValue(herb.get("category"))));
                        hasRows = true;
                    }
                    if (hasRows)
                    {
                        doc.add(itemTable);
                    }
                }
            }
        }

        if (!added)
        {
            addHerbalSection(doc, font, payload.getJSONArray("herbals"), payload);
        }
    }

    private void addPrescriptionSummary(Document doc, PdfFont font, JSONArray prescriptions)
    {
        if (prescriptions == null || prescriptions.isEmpty())
        {
            return;
        }
        addSectionTitle(doc, font, "Prescriptions");
        Table table = new Table(UnitValue.createPercentArray(new float[] { 2, 1, 1, 1 })).useAllAvailableWidth();
        addTableHeader(table, font, "Formula", "Type", "Qty", "Status");
        for (int i = 0; i < prescriptions.size(); i++)
        {
            JSONObject rx = prescriptions.getJSONObject(i);
            if (rx == null || "deleted".equals(rx.getString("rxStatus")) || rx.getString("deletedAt") != null)
            {
                continue;
            }
            addTableRow(
                    table,
                    font,
                    firstNonBlank(rx.getString("formulaName"), rx.getString("name"), "-"),
                    safeValue(rx.getString("prescriptionType")),
                    String.valueOf(rx.getIntValue("quantity", 1)),
                    safeValue(rx.getString("rxStatus")));
        }
        doc.add(table);
    }

    private void addInvoiceItems(Document doc, PdfFont font, JSONObject payload, String currency)
    {
        JSONArray services = payload.getJSONArray("services");
        addSectionTitle(doc, font, "收费项目 / Service Items");
        Table serviceTable = new Table(UnitValue.createPercentArray(new float[] { 3, 1, 1, 1 })).useAllAvailableWidth();
        addTableHeader(serviceTable, font, "Description / 收费项目", "Qty / 数量", "Unit Price / 单价", "Amount / 金额");
        boolean hasRows = false;

        BigDecimal consultationFee = payload.getBigDecimal("consultationFee");
        if (isPositive(consultationFee))
        {
            addTableRow(serviceTable, font,
                    "Consultation Fee / 诊疗费",
                    "1",
                    formatMoney(consultationFee, currency),
                    formatMoney(consultationFee, currency));
            hasRows = true;
        }

        if (services != null && !services.isEmpty())
        {
            for (int i = 0; i < services.size(); i++)
            {
                JSONObject service = services.getJSONObject(i);
                if (service == null || !hasAnyMeaningfulValue(service, "name", "price", "quantity", "manualDiscount"))
                {
                    continue;
                }
                BigDecimal price = defaultMoney(service.getBigDecimal("price"));
                int qty = Math.max(1, service.getIntValue("quantity", 1));
                BigDecimal discount = defaultMoney(service.getBigDecimal("manualDiscount"));
                BigDecimal subtotal = price.multiply(BigDecimal.valueOf(qty)).subtract(discount).max(BigDecimal.ZERO);
                addTableRow(
                        serviceTable,
                        font,
                        firstNonBlank(service.getString("name"), "Service / 服务"),
                        String.valueOf(qty),
                        formatMoney(price, currency),
                        formatMoney(subtotal, currency));
                hasRows = true;
            }
        }

        if (payload.getBooleanValue("includeRxAmount"))
        {
            JSONArray prescriptions = payload.getJSONArray("prescriptions");
            if (prescriptions != null && !prescriptions.isEmpty())
            {
                for (int p = 0; p < prescriptions.size(); p++)
                {
                    JSONObject rx = prescriptions.getJSONObject(p);
                    if (!isBillablePrescription(rx))
                    {
                        continue;
                    }
                    int rxQty = Math.max(1, rx.getIntValue("quantity", 1));
                    BigDecimal subtotal = defaultMoney(rx.getBigDecimal("subtotal"));
                    BigDecimal unitPrice = rx.getBigDecimal("perDoseSubtotal");
                    if (unitPrice == null && subtotal.compareTo(BigDecimal.ZERO) > 0)
                    {
                        unitPrice = subtotal.divide(BigDecimal.valueOf(rxQty), 2, RoundingMode.HALF_UP);
                    }
                    String rxName = firstNonBlank(
                            rx.getString("formulaName"),
                            rx.getString("name"),
                            rx.getString("prescriptionType"),
                            "Prescription / 中药");
                    addTableRow(
                            serviceTable,
                            font,
                            "Prescription / 中药: " + rxName,
                            String.valueOf(rxQty),
                            formatMoney(unitPrice, currency),
                            formatMoney(subtotal, currency));
                    hasRows = true;
                }
            }
        }

        if (!hasRows)
        {
            addTableRow(serviceTable, font, "No charge items / 暂无收费项目", "-", "-", formatMoney(BigDecimal.ZERO, currency));
        }
        doc.add(serviceTable);
    }

    private boolean isBillablePrescription(JSONObject rx)
    {
        if (rx == null || "deleted".equals(rx.getString("rxStatus")) || rx.getString("deletedAt") != null)
        {
            return false;
        }
        String rxStatus = rx.getString("rxStatus");
        return StringUtils.isBlank(rxStatus) || "pending".equals(rxStatus) || "dispensed".equals(rxStatus);
    }

    private void addInvoiceTotals(Document doc, PdfFont font, JSONObject payload, String currency)
    {
        doc.add(new Paragraph("\n").setFontSize(4));
        BigDecimal totalAmount = defaultMoney(payload.getBigDecimal("totalAmount"));
        BigDecimal taxAmount = defaultMoney(payload.getBigDecimal("taxAmount"));
        BigDecimal subtotal = payload.getBigDecimal("totalWithoutTax");
        if (subtotal == null)
        {
            subtotal = totalAmount.subtract(taxAmount).max(BigDecimal.ZERO);
        }
        BigDecimal paidAmount = resolvePaidAmount(payload);
        BigDecimal balanceAmount = totalAmount.subtract(paidAmount).max(BigDecimal.ZERO);

        Table totalTable = new Table(UnitValue.createPercentArray(new float[] { 3, 1 })).useAllAvailableWidth();
        totalTable.addCell(new Cell().add(new Paragraph("Subtotal").setFont(font).setFontSize(10))
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT));
        totalTable.addCell(new Cell().add(new Paragraph(formatMoney(subtotal, currency))
                .setFont(font)
                .setFontSize(10))
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT));
        totalTable.addCell(new Cell().add(new Paragraph(resolveHstLabel(payload)).setFont(font).setFontSize(10))
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT));
        totalTable.addCell(new Cell().add(new Paragraph(formatMoney(taxAmount, currency)).setFont(font).setFontSize(10))
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT));
        totalTable.addCell(new Cell().add(new Paragraph("Total").setFont(font).setFontSize(14).setBold()
                .setFontColor(PRIMARY_COLOR))
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT));
        totalTable.addCell(new Cell().add(new Paragraph(formatMoney(totalAmount, currency)).setFont(font)
                .setFontSize(14)
                .setBold()
                .setFontColor(PRIMARY_COLOR))
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT));
        totalTable.addCell(new Cell().add(new Paragraph("Paid Amount").setFont(font).setFontSize(10))
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT));
        totalTable.addCell(new Cell().add(new Paragraph(formatMoney(paidAmount, currency)).setFont(font).setFontSize(10))
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT));
        totalTable.addCell(new Cell().add(new Paragraph("Balance Amount").setFont(font).setFontSize(10).setBold())
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT));
        totalTable.addCell(new Cell().add(new Paragraph(formatMoney(balanceAmount, currency)).setFont(font).setFontSize(10).setBold())
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT));
        doc.add(totalTable);
    }

    private BigDecimal resolvePaidAmount(JSONObject payload)
    {
        BigDecimal paidAmount = payload.getBigDecimal("paidAmount");
        if (paidAmount != null)
        {
            return paidAmount;
        }
        JSONArray paymentRecords = payload.getJSONArray("paymentRecords");
        BigDecimal total = BigDecimal.ZERO;
        if (paymentRecords != null)
        {
            for (int i = 0; i < paymentRecords.size(); i++)
            {
                JSONObject record = paymentRecords.getJSONObject(i);
                if (record != null)
                {
                    total = total.add(defaultMoney(record.getBigDecimal("amount")));
                }
            }
        }
        return total;
    }

    private String resolveHstLabel(JSONObject payload)
    {
        BigDecimal rate = payload != null ? payload.getBigDecimal("overrideTaxRate") : null;
        if (rate == null)
        {
            rate = parseDecimal(getClinicSetting("taxRate"));
        }
        if (rate == null)
        {
            return "HST";
        }
        BigDecimal percent = rate.compareTo(BigDecimal.ONE) > 0
                ? rate
                : rate.multiply(BigDecimal.valueOf(100));
        return "HST (" + percent.stripTrailingZeros().toPlainString() + "%)";
    }

    private BigDecimal parseDecimal(String value)
    {
        if (!hasMeaningfulValue(value))
        {
            return null;
        }
        try
        {
            return new BigDecimal(value.trim());
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private boolean isPositive(BigDecimal amount)
    {
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal defaultMoney(BigDecimal amount)
    {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    private String resolveCurrency(JSONObject payload)
    {
        return "CAD";
    }

    private String formatMoney(BigDecimal amount, String currency)
    {
        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;
        return "CAD " + safeAmount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private void addParagraphSection(Document doc, PdfFont font, String title, String content)
    {
        addSectionTitle(doc, font, title);
        doc.add(new Paragraph(safeContent(content)).setFont(font).setFontSize(10));
    }

    private void addLinesSection(Document doc, PdfFont font, String title, List<String> lines)
    {
        if (lines == null || lines.isEmpty())
        {
            return;
        }
        addParagraphSection(doc, font, title, String.join("\n", lines));
    }

    private void addOptionalSection(Document doc, PdfFont font, String title, String content)
    {
        if (!hasMeaningfulValue(content))
        {
            return;
        }
        addParagraphSection(doc, font, title, content);
    }

    private void addOptionalParagraph(Document doc, PdfFont font, String content)
    {
        if (!hasMeaningfulValue(content))
        {
            return;
        }
        doc.add(new Paragraph(content).setFont(font).setFontSize(10));
    }

    private String buildDiffSummary(JSONObject diff)
    {
        if (diff == null || diff.isEmpty())
        {
            return "-";
        }
        Map<String, String> labels = diffLabels();
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, String> entry : labels.entrySet())
        {
            appendDiffLine(lines, entry.getValue(), diff.get(entry.getKey()));
        }
        for (String key : diff.keySet())
        {
            if (labels.containsKey(key)
                    || "conclusions".equals(key)
                    || "tongueImage".equals(key)
                    || "tongueImageResource".equals(key))
            {
                continue;
            }
            appendDiffLine(lines, humanizeKey(key), diff.get(key));
        }
        return lines.isEmpty() ? "-" : String.join("\n", lines);
    }

    private Map<String, String> diffLabels()
    {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("coldHeat", "Cold/Heat / 寒热");
        labels.put("sweat", "Sweat / 汗");
        labels.put("headDiscomfort", "Head Discomfort / 头部不适");
        labels.put("headPosition", "Head Position / 头痛部位");
        labels.put("eye", "Eye / 眼");
        labels.put("ear", "Ear / 耳");
        labels.put("nose", "Nose / 鼻");
        labels.put("mouth", "Mouth / 口");
        labels.put("taste", "Taste / 口味");
        labels.put("bodyDiscomforts", "Body Discomforts / 身体不适");
        labels.put("bodyDiscomfortsLocation", "Body Location / 身体部位");
        labels.put("skinIssues", "Skin / 皮肤");
        labels.put("otherExterior", "Other Exterior / 其他表证");
        labels.put("chest", "Chest / 胸");
        labels.put("hypochondriac", "Hypochondriac / 两胁");
        labels.put("sleep", "Sleep / 睡眠");
        labels.put("anxietyStress", "Anxiety/Stress / 焦虑压力");
        labels.put("otherChest", "Other Chest / 其他心胸");
        labels.put("appetite", "Appetite / 胃口");
        labels.put("thirst", "Thirst / 口渴");
        labels.put("abdomen", "Abdomen / 腹部");
        labels.put("otherAbdomen", "Other Abdomen / 其他腹部");
        labels.put("bowelMovement", "Bowel Movement / 大便");
        labels.put("urine", "Urine / 小便");
        labels.put("otherLowerAbdomen", "Other Lower Abdomen / 其他下腹");
        labels.put("periodCircle", "Period Cycle / 经期长");
        labels.put("periodDuration", "Period Duration / 每期持续");
        labels.put("bloodQuality", "Blood Quality / 经血");
        labels.put("pms", "PMS / 经前症状");
        labels.put("otherFemale", "Other Female / 其他妇科");
        labels.put("pulse", "Pulse / 脉");
        labels.put("pulseRightHand", "Right Pulse / 右手脉");
        labels.put("pulseLeftHand", "Left Pulse / 左手脉");
        labels.put("pulseBothCun", "Both Cun / 双寸脉");
        labels.put("pulseBothGuan", "Both Guan / 双关脉");
        labels.put("pulseBothChi", "Both Chi / 双尺脉");
        labels.put("detailedPulse", "Detailed Pulse / 脉象补充");
        labels.put("pathologicalChannel", "Pathological Channel / 病变经络");
        labels.put("pathologicalChanges", "Pathological Changes / 病变详情");
        labels.put("tongueColor", "Tongue Color / 舌色");
        labels.put("tongueBody", "Tongue Body / 舌体");
        labels.put("tongueCoating", "Tongue Coating / 舌苔");
        labels.put("otherTongue", "Other Tongue / 其他舌象");
        return labels;
    }

    private void appendDiffLine(List<String> lines, String label, Object value)
    {
        appendKeyValue(lines, label, value);
    }

    private void appendFirstAvailable(List<String> lines, String label, JSONObject primary, JSONObject fallback, String key)
    {
        Object value = primary != null ? primary.get(key) : null;
        if (!hasMeaningfulValue(value) && fallback != null)
        {
            value = fallback.get(key);
        }
        appendKeyValue(lines, label, value);
    }

    private void appendKeyValue(List<String> lines, String label, Object value)
    {
        if (!hasMeaningfulValue(value))
        {
            return;
        }
        lines.add(label + ": " + formatStructuredValue(value));
    }

    private void appendValuePart(List<String> parts, Object value)
    {
        if (hasMeaningfulValue(value))
        {
            parts.add(formatStructuredValue(value));
        }
    }

    private void appendKeyValuePart(List<String> parts, String label, Object value)
    {
        if (hasMeaningfulValue(value))
        {
            parts.add(label + ": " + formatStructuredValue(value));
        }
    }

    private Object firstMeaningful(Object... values)
    {
        if (values == null)
        {
            return null;
        }
        for (Object value : values)
        {
            if (hasMeaningfulValue(value))
            {
                return value;
            }
        }
        return null;
    }

    private boolean hasAnyMeaningfulValue(JSONObject object, String... keys)
    {
        if (object == null || keys == null)
        {
            return false;
        }
        for (String key : keys)
        {
            if (hasMeaningfulValue(object.get(key)))
            {
                return true;
            }
        }
        return false;
    }

    private boolean hasMeaningfulValue(Object value)
    {
        if (value == null)
        {
            return false;
        }
        if (value instanceof String)
        {
            String text = ((String) value).trim();
            return !text.isEmpty() && !"-".equals(text) && !"[]".equals(text) && !"{}".equals(text);
        }
        if (value instanceof JSONArray)
        {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.size(); i++)
            {
                if (hasMeaningfulValue(array.get(i)))
                {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Collection<?>)
        {
            for (Object item : (Collection<?>) value)
            {
                if (hasMeaningfulValue(item))
                {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof JSONObject)
        {
            JSONObject object = (JSONObject) value;
            for (String key : object.keySet())
            {
                if (hasMeaningfulValue(object.get(key)))
                {
                    return true;
                }
            }
            return false;
        }
        if (value instanceof Map<?, ?>)
        {
            for (Object item : ((Map<?, ?>) value).values())
            {
                if (hasMeaningfulValue(item))
                {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    private String formatStructuredValue(Object value)
    {
        if (!hasMeaningfulValue(value))
        {
            return "-";
        }
        if (value instanceof JSONArray)
        {
            JSONArray array = (JSONArray) value;
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < array.size(); i++)
            {
                String item = formatStructuredValue(array.get(i));
                if (hasMeaningfulValue(item))
                {
                    parts.add(item);
                }
            }
            return String.join(", ", parts);
        }
        if (value instanceof Collection<?>)
        {
            List<String> parts = new ArrayList<>();
            for (Object item : (Collection<?>) value)
            {
                String text = formatStructuredValue(item);
                if (hasMeaningfulValue(text))
                {
                    parts.add(text);
                }
            }
            return String.join(", ", parts);
        }
        if (value instanceof JSONObject)
        {
            JSONObject object = (JSONObject) value;
            List<String> lines = new ArrayList<>();
            for (String key : object.keySet())
            {
                Object item = object.get(key);
                if (hasMeaningfulValue(item))
                {
                    lines.add(humanizeKey(key) + ": " + formatStructuredValue(item));
                }
            }
            return String.join("\n", lines);
        }
        if (value instanceof Map<?, ?>)
        {
            List<String> lines = new ArrayList<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet())
            {
                Object item = entry.getValue();
                if (hasMeaningfulValue(item))
                {
                    lines.add(humanizeKey(String.valueOf(entry.getKey())) + ": " + formatStructuredValue(item));
                }
            }
            return String.join("\n", lines);
        }
        return String.valueOf(value).trim();
    }

    private JSONObject toJsonObject(Object value)
    {
        if (value instanceof JSONObject)
        {
            return JSON.parseObject(((JSONObject) value).toJSONString());
        }
        if (value instanceof Map<?, ?>)
        {
            return JSON.parseObject(JSON.toJSONString(value));
        }
        if (value instanceof String && StringUtils.isNotBlank((String) value))
        {
            try
            {
                return JSON.parseObject((String) value);
            }
            catch (Exception ignored)
            {
                return new JSONObject(new LinkedHashMap<>());
            }
        }
        return new JSONObject(new LinkedHashMap<>());
    }

    private String humanizeKey(String key)
    {
        if (key == null || key.trim().isEmpty())
        {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        char[] chars = key.trim().toCharArray();
        for (int i = 0; i < chars.length; i++)
        {
            char ch = chars[i];
            if (i > 0 && Character.isUpperCase(ch) && chars[i - 1] != ' ')
            {
                builder.append(' ');
            }
            builder.append(i == 0 ? Character.toUpperCase(ch) : ch);
        }
        return builder.toString();
    }

    private PdfFont createFont()
    {
        try
        {
            String[] fontPaths = {
                "C:/Windows/Fonts/msyh.ttc,0",
                "C:/Windows/Fonts/simsun.ttc,0",
                "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc,0",
            };
            for (String fontPath : fontPaths)
            {
                try
                {
                    String path = fontPath.contains(",") ? fontPath.substring(0, fontPath.indexOf(',')) : fontPath;
                    if (new File(path).exists())
                    {
                        return PdfFontFactory.createFont(fontPath, "Identity-H");
                    }
                }
                catch (Exception ignored)
                {
                    // try next font
                }
            }
            return PdfFontFactory.createFont();
        }
        catch (Exception e)
        {
            throw new ServiceException("failed to load PDF font");
        }
    }

    private PdfFont createSignatureFont(String displaySignature, PdfFont fallback)
    {
        boolean hasCjk = displaySignature != null && displaySignature.chars().anyMatch(ch -> ch > 127);
        String[] fontPaths = hasCjk
                ? new String[] {
                    "C:/Windows/Fonts/simkai.ttf",
                    "C:/Windows/Fonts/STKAITI.TTF",
                    "C:/Windows/Fonts/msyh.ttc,0",
                }
                : new String[] {
                    "C:/Windows/Fonts/segoesc.ttf",
                    "C:/Windows/Fonts/segoescb.ttf",
                    "C:/Windows/Fonts/brushsci.ttf",
                    "C:/Windows/Fonts/ITCEDSCR.TTF",
                    "C:/Windows/Fonts/msyh.ttc,0",
                };
        for (String fontPath : fontPaths)
        {
            try
            {
                String path = fontPath.contains(",") ? fontPath.substring(0, fontPath.indexOf(',')) : fontPath;
                if (new File(path).exists())
                {
                    return PdfFontFactory.createFont(fontPath, "Identity-H");
                }
            }
            catch (Exception ignored)
            {
                // try next font
            }
        }
        return fallback;
    }

    private void addHeader(Document doc, PdfFont font, String clinicName, String subtitle)
    {
        Table header = new Table(UnitValue.createPercentArray(new float[] { 1 })).useAllAvailableWidth();
        header.setMarginBottom(14);
        header.addCell(new Cell()
                .add(new Paragraph(safeValue(clinicName)).setFont(font).setFontSize(18).setBold()
                        .setFontColor(ColorConstants.WHITE))
                .add(new Paragraph(safeValue(subtitle)).setFont(font).setFontSize(11)
                        .setFontColor(new DeviceRgb(225, 240, 231)))
                .setBackgroundColor(PRIMARY_COLOR)
                .setBorder(Border.NO_BORDER)
                .setPadding(14));
        doc.add(header);
    }

    private void addSectionTitle(Document doc, PdfFont font, String title)
    {
        doc.add(new Paragraph(title).setFont(font).setFontSize(12).setBold().setFontColor(PRIMARY_COLOR)
                .setMarginTop(12).setMarginBottom(6)
                .setBackgroundColor(SOFT_GREEN)
                .setBorderLeft(new SolidBorder(PRIMARY_COLOR, 4))
                .setBorderBottom(new SolidBorder(BORDER_GREEN, 1))
                .setPaddingLeft(8)
                .setPaddingTop(6)
                .setPaddingBottom(6));
    }

    private void addInfoRow(Table table, PdfFont font, String label, String value)
    {
        table.addCell(new Cell()
                .add(new Paragraph(label).setFont(font).setFontSize(8).setBold().setFontColor(MUTED_TEXT))
                .add(new Paragraph(safeValue(value)).setFont(font).setFontSize(10))
                .setBorder(new SolidBorder(BORDER_GREEN, 0.6f))
                .setPadding(7));
    }

    private void addTableHeader(Table table, PdfFont font, String... headers)
    {
        for (String header : headers)
        {
            table.addHeaderCell(new Cell().add(new Paragraph(header).setFont(font).setFontSize(9).setBold()
                            .setFontColor(ColorConstants.WHITE))
                    .setBackgroundColor(PRIMARY_COLOR)
                    .setBorder(new SolidBorder(PRIMARY_COLOR, 0.6f))
                    .setPadding(6));
        }
    }

    private void addTableRow(Table table, PdfFont font, String... values)
    {
        for (String value : values)
        {
            table.addCell(new Cell().add(new Paragraph(safeValue(value)).setFont(font).setFontSize(9))
                    .setBorder(new SolidBorder(BORDER_GREEN, 0.6f))
                    .setPadding(6));
        }
    }

    private void addFooter(Document doc, PdfFont font)
    {
        doc.add(new Paragraph("Generated by TCM clinic system - " + new SimpleDateFormat("yyyy-MM-dd").format(new Date()))
                .setFont(font)
                .setFontSize(8)
                .setFontColor(MUTED_TEXT)
                .setBorderTop(new SolidBorder(BORDER_GREEN, 0.8f))
                .setPaddingTop(8)
                .setMarginTop(18)
                .setTextAlignment(TextAlignment.CENTER));
    }

    private void addConfiguredFooterImage(Document doc)
    {
        JSONObject thirdPartySignature = parsePayload(getClinicSetting("thirdPartySignature"));
        String imageRef = firstNonBlank(
                safeStr(thirdPartySignature, "path"),
                getClinicSetting("invoiceFooterImagePath"),
                getClinicSetting("invoiceFooterPngPath"),
                getClinicSetting("invoiceFooterImageUrl"));
        addImageResource(doc, imageRef, 90, "Invoice footer image ignored");
    }

    private void addClinicSeal(Document doc)
    {
        JSONObject clinicSeal = parsePayload(getClinicSetting("clinicSeal"));
        addImageResource(doc, safeStr(clinicSeal, "path"), 80, "Clinic seal ignored");
    }

    private void addPractitionerSignature(Document doc, PdfFont font, JSONObject practitionerProfile)
    {
        if (practitionerProfile == null)
        {
            return;
        }
        Object signature = practitionerProfile.get("signature");
        String imageRef = "";
        if (signature instanceof JSONObject)
        {
            imageRef = safeStr((JSONObject) signature, "path");
        }
        if (StringUtils.isBlank(imageRef))
        {
            imageRef = firstNonBlank(
                    practitionerProfile.getString("signaturePath"),
                    practitionerProfile.getString("signaturePng"),
                    practitionerProfile.getString("signatureUrl"));
        }
        if (StringUtils.isBlank(imageRef))
        {
            return;
        }
        addSectionTitle(doc, font, "Practitioner Signature / 医师签名");
        addImageResource(doc, imageRef, 70, "Practitioner signature ignored");
    }

    private void addImageResource(Document doc, String imageRef, float maxHeight, String warningPrefix)
    {
        if (StringUtils.isBlank(imageRef) || "-".equals(imageRef.trim()))
        {
            return;
        }
        try
        {
            String source = imageRef.trim();
            if (!source.startsWith("http://") && !source.startsWith("https://") && !new File(source).isAbsolute())
            {
                source = hospitalFileStorage.resolve(source).toString();
            }
            Image image = new Image(ImageDataFactory.create(source));
            image.setAutoScale(true);
            image.setMaxHeight(maxHeight);
            image.setMarginTop(12);
            image.setTextAlignment(TextAlignment.CENTER);
            doc.add(image);
        }
        catch (Exception e)
        {
            log.warn("{}: {}", warningPrefix, e.getMessage());
        }
    }

    private JSONObject resolvePractitionerProfile(TcmConsultation consultation)
    {
        JSONObject practitionerProfile = new JSONObject(new LinkedHashMap<>());
        if (consultation != null && consultation.getPractitionerId() != null)
        {
            try
            {
                SysUser practitioner = userService.selectUserById(Long.valueOf(consultation.getPractitionerId()));
                if (practitioner != null)
                {
                    practitionerProfile = parsePayload(practitioner.getRemark());
                    if (StringUtils.isBlank(practitionerProfile.getString("practitionerName"))
                            && StringUtils.isNotBlank(practitioner.getNickName()))
                    {
                        practitionerProfile.put("practitionerName", practitioner.getNickName());
                    }
                    if (StringUtils.isBlank(practitionerProfile.getString("practitionerEmail"))
                            && StringUtils.isNotBlank(practitioner.getEmail()))
                    {
                        practitionerProfile.put("practitionerEmail", practitioner.getEmail());
                    }
                    if (StringUtils.isBlank(practitionerProfile.getString("practitionerPhone"))
                            && StringUtils.isNotBlank(practitioner.getPhonenumber()))
                    {
                        practitionerProfile.put("practitionerPhone", practitioner.getPhonenumber());
                    }
                }
            }
            catch (Exception ignored) {}
        }

        JSONObject configuredPractitionerProfile = parsePayload(getClinicSetting("practitionerProfile"));
        mergeProfileFallback(practitionerProfile, configuredPractitionerProfile, "practitionerName");
        mergeProfileFallback(practitionerProfile, configuredPractitionerProfile, "title");
        mergeProfileFallback(practitionerProfile, configuredPractitionerProfile, "organization");
        mergeProfileFallback(practitionerProfile, configuredPractitionerProfile, "organizationNumber");
        mergeProfileFallback(practitionerProfile, configuredPractitionerProfile, "practitionerEmail");
        mergeProfileFallback(practitionerProfile, configuredPractitionerProfile, "practitionerPhone");
        mergeProfileFallback(practitionerProfile, "practitionerName", getClinicSetting("practitionerName"));
        mergeProfileFallback(practitionerProfile, "organization", getClinicSetting("practitionerOrganization"));
        mergeProfileFallback(practitionerProfile, "organizationNumber", getClinicSetting("practitionerOrganizationNumber"));

        if (StringUtils.isBlank(practitionerProfile.getString("organization"))
                && StringUtils.isNotBlank(practitionerProfile.getString("regulatoryBody")))
        {
            practitionerProfile.put("organization", practitionerProfile.getString("regulatoryBody"));
        }
        if (StringUtils.isBlank(practitionerProfile.getString("organizationNumber"))
                && StringUtils.isNotBlank(practitionerProfile.getString("registrationNumber")))
        {
            practitionerProfile.put("organizationNumber", practitionerProfile.getString("registrationNumber"));
        }
        return practitionerProfile;
    }

    private void mergeProfileFallback(JSONObject target, JSONObject fallback, String key)
    {
        if (target == null || fallback == null || StringUtils.isBlank(key))
        {
            return;
        }
        mergeProfileFallback(target, key, fallback.getString(key));
    }

    private void mergeProfileFallback(JSONObject target, String key, String value)
    {
        if (target == null || StringUtils.isBlank(key))
        {
            return;
        }
        if (StringUtils.isBlank(target.getString(key)) && hasMeaningfulValue(value))
        {
            target.put(key, value.trim());
        }
    }

    private JSONObject parsePayload(String payloadStr)
    {
        if (payloadStr != null && !payloadStr.isEmpty())
        {
            try
            {
                return JSON.parseObject(payloadStr);
            }
            catch (Exception ignored)
            {
                // fall through
            }
        }
        return new JSONObject();
    }

    private String safeStr(JSONObject obj, String key)
    {
        return safeValue(obj.getString(key));
    }

    private String safeValue(String value)
    {
        String text = cleanText(value);
        return text != null ? text : "-";
    }

    private String safeContent(String content)
    {
        String text = cleanText(content);
        return text != null ? text : "-";
    }

    private String cleanText(String value)
    {
        if (value == null)
        {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() || "-".equals(text) || "[]".equals(text) || "{}".equals(text) ? null : text;
    }

    private String firstNonBlank(String... values)
    {
        if (values == null)
        {
            return "-";
        }
        for (String value : values)
        {
            if (hasMeaningfulValue(value))
            {
                return value.trim();
            }
        }
        return "-";
    }

    private String getClinicName()
    {
        try
        {
            TcmClinicSetting setting = settingMapper.selectSettingByKey("clinicName");
            return setting != null ? setting.getSettingValue() : "TCM Clinic";
        }
        catch (Exception e)
        {
            return "TCM Clinic";
        }
    }

    private String getClinicSetting(String key)
    {
        try
        {
            TcmClinicSetting setting = settingMapper.selectSettingByKey(key);
            return setting != null ? setting.getSettingValue() : "";
        }
        catch (Exception e)
        {
            return "";
        }
    }

    private void updatePatientConsentMeta(TcmPatient patient, Map<String, String> result)
    {
        JSONObject payload = parsePayload(patient.getPayload());
        payload.put("consentPdfPath", result.get("filePath"));
        payload.put("consentPdfUrl", result.get("url"));
        payload.put("consentPdfGeneratedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        patient.setPayload(payload.toJSONString());
        patientMapper.updateTcmPatient(patient);
    }

    private void updateConsultationPdfMeta(TcmConsultation consultation, Map<String, String> result, String type)
    {
        JSONObject payload = parsePayload(consultation.getPayload());
        if ("report".equals(type))
        {
            payload.put("reportPdfPath", result.get("filePath"));
            payload.put("reportPdfUrl", result.get("url"));
            payload.put("consultationPdfPath", result.get("filePath"));
            payload.put("consultationPdfUrl", result.get("url"));
            payload.put("reportPdfGeneratedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        }
        consultation.setPayload(payload.toJSONString());
        consultationMapper.updateTcmConsultation(consultation);
    }

    private void insertConsentFileRecord(TcmPatient patient, String resourcePath)
    {
        TcmPatientFile file = new TcmPatientFile();
        file.setPatientId(patient.getId());
        file.setFileType("consent_pdf");
        file.setFileName("consent-" + safeValue(patient.getName()) + ".pdf");
        file.setFilePath(resourcePath);
        patientFileService.insertTcmPatientFile(file);
    }

    private void insertConsultationFileRecord(TcmConsultation consultation, String fileType, String prefix, String resourcePath)
    {
        if (patientFileService.selectTcmPatientFileByPath(resourcePath) != null)
        {
            return;
        }
        TcmPatientFile file = new TcmPatientFile();
        file.setPatientId(consultation.getPatientId());
        file.setConsultationId(consultation.getId());
        file.setFileType(fileType);
        String versionSuffix = consultation.getVersion() != null && consultation.getVersion() > 1
                ? "-v" + consultation.getVersion()
                : "";
        file.setFileName(prefix + "-" + safeValue(consultation.getConsultationId()) + versionSuffix + ".pdf");
        file.setFilePath(resourcePath);
        patientFileService.insertTcmPatientFile(file);
    }

    private void ensureDir(String filePath)
    {
        File file = new File(filePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists())
        {
            parent.mkdirs();
        }
    }
}
