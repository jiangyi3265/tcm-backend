package com.ruoyi.hospital.service.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
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

        TcmPatient patient = patientMapper.selectTcmPatientById(consultation.getPatientId());
        JSONObject payload = parsePayload(consultation.getPayload());
        String clinicName = getClinicName();
        String resourcePath = hospitalFileStorage.createResourceKey("report", ".pdf");
        String filePath = hospitalFileStorage.resolve(resourcePath).toString();
        ensureDir(filePath);

        try
        {
            PdfWriter writer = new PdfWriter(new FileOutputStream(filePath));
            PdfDocument pdf = new PdfDocument(writer);
            Document doc = new Document(pdf);
            PdfFont font = createFont();

            addHeader(doc, font, clinicName, "Consultation Report");
            addConsultationInfo(doc, font, consultation, patient);

            addOptionalSection(doc, font, "History and Medication", firstNonBlank(
                    safeStr(payload, "historyAndMedicationSnapshot"),
                    safeStr(payload, "historyAndMedication"),
                    safeStr(payload, "medicalHistory")));
            addParagraphSection(
                    doc,
                    font,
                    "Chief Complaint",
                    safeStr(payload, "chiefComplaint") + " (" + safeStr(payload, "chiefComplaintDuration") + ")");
            addOptionalParagraph(doc, font, safeStr(payload, "chiefComplaintDescription"));
            addOptionalSection(doc, font, "Clinical Notes", firstNonBlank(
                    safeStr(payload, "assessment"),
                    safeStr(payload, "diagnosis"),
                    safeStr(payload, "notes")));

            JSONObject diff = payload.getJSONObject("diff");
            if (diff != null)
            {
                addParagraphSection(doc, font, "Differentiation", buildDiffSummary(diff));
                addOptionalParagraph(doc, font, safeStr(payload, "differentiation"));
            }

            addHerbalSection(doc, font, payload.getJSONArray("herbals"), payload);
            addPrescriptionSummary(doc, font, payload.getJSONArray("prescriptions"));
            addOptionalSection(doc, font, "Treatment", firstNonBlank(
                    safeStr(payload, "treatment"),
                    safeStr(payload, "treatmentPlan"),
                    safeStr(payload, "acupunctureTreatment")));
            addOptionalSection(doc, font, "Prognosis", safeStr(payload, "prognosis"));
            addOptionalSection(doc, font, "Follow Up", firstNonBlank(
                    safeStr(payload, "followUp"),
                    safeStr(payload, "followUpAdvice"),
                    safeStr(payload, "aftercare")));
            addFooter(doc, font);
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

        JSONObject practitionerProfile = new JSONObject();
        JSONObject configuredPractitionerProfile = parsePayload(getClinicSetting("practitionerProfile"));
        if (consultation.getPractitionerId() != null)
        {
            try
            {
                SysUser practitioner = userService.selectUserById(Long.valueOf(consultation.getPractitionerId()));
                if (practitioner != null && practitioner.getRemark() != null)
                {
                    practitionerProfile = parsePayload(practitioner.getRemark());
                }
            }
            catch (Exception ignored) {}
        }
        mergeProfileFallback(practitionerProfile, configuredPractitionerProfile, "practitionerName");
        mergeProfileFallback(practitionerProfile, configuredPractitionerProfile, "title");
        mergeProfileFallback(practitionerProfile, configuredPractitionerProfile, "organization");
        mergeProfileFallback(practitionerProfile, configuredPractitionerProfile, "organizationNumber");

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
            addConsultationInfo(doc, font, consultation, patient);
            addInvoiceBillTo(doc, font, patient, patientPayload);
            addInvoiceItems(doc, font, payload.getJSONArray("services"), currency);
            addInvoicePrescriptionItems(doc, font, payload, currency);
            addInvoiceTotals(doc, font, payload, currency);
            addConfiguredFooterImage(doc);
            addFooter(doc, font);
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
        String consentTitle = StringUtils.defaultIfBlank(
                patientPayload.getString("consentDocumentTitle"),
                "OTCM Informed Consent");
        String displaySignature = StringUtils.defaultIfBlank(
                StringUtils.defaultIfBlank(signatureName, patientPayload.getString("consentSignatureName")),
                patient.getName());
        JSONObject acknowledgements = patientPayload.getJSONObject("consentSectionAcknowledgements");
        List<Map<String, Object>> sections = extractConsentSections(patientPayload.get("consentDocumentSections"));

        try
        {
            PdfWriter writer = new PdfWriter(new FileOutputStream(filePath));
            PdfDocument pdf = new PdfDocument(writer);
            Document doc = new Document(pdf);
            PdfFont font = createFont();

            addHeader(doc, font, clinicName, consentTitle);
            doc.add(new Paragraph("Patient / 患者：" + safeValue(patient.getName())).setFont(font).setFontSize(11));
            doc.add(new Paragraph("Signed at / 签署时间：" + safeValue(signedAt)).setFont(font).setFontSize(11));
            doc.add(new Paragraph("Version / 版本：" + safeValue(patientPayload.getString("consentVersion"))).setFont(font).setFontSize(10));
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
                String sectionKey = section.get("key") != null ? String.valueOf(section.get("key")) : "";
                String sectionTitle = section.get("title") != null ? String.valueOf(section.get("title")) : ("Section " + index);
                boolean agreed = acknowledgements != null && acknowledgements.getBooleanValue(sectionKey);
                doc.add(new Paragraph(index + ". " + sectionTitle)
                        .setFont(font)
                        .setFontSize(11)
                        .setBold()
                        .setMarginTop(8)
                        .setMarginBottom(4));
                if (agreed)
                {
                    doc.add(new Paragraph("[x] I have read carefully and agree.")
                            .setFont(font)
                            .setFontSize(10)
                            .setMarginBottom(4));
                }
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
                index++;
            }

            addSectionTitle(doc, font, "Signature Confirmation / 签署确认");
            doc.add(new Paragraph("Signer / 签署人：" + safeValue(displaySignature)).setFont(font).setFontSize(11));
            doc.add(new Paragraph("[x] I have read carefully and agree.")
                    .setFont(font).setFontSize(11));
            addFooter(doc, font);
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

    private List<Map<String, Object>> extractConsentSections(Object sectionsObj)
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
        return ConsentDocumentTemplate.toResponseSections();
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
        StringBuilder sb = new StringBuilder();
        sb.append(clinicName);
        if (clinicAddress != null && !clinicAddress.isEmpty()) sb.append("\n").append(clinicAddress);
        if (clinicPhone != null && !clinicPhone.isEmpty()) sb.append("\n").append(clinicPhone);
        appendLine(sb, getClinicSetting("clinicEmail"));
        appendLine(sb, getClinicSetting("clinicOrganization"));
        appendLine(sb, getClinicSetting("clinicOrganizationNumber"));
        appendLine(sb, getClinicSetting("clinicTaxNumber"));
        appendLine(sb, getClinicSetting("invoiceBusinessNumber"));
        appendLine(sb, practitionerProfile.getString("practitionerName"));
        appendLine(sb, practitionerProfile.getString("title"));
        appendLine(sb, practitionerProfile.getString("organization"));
        appendLine(sb, practitionerProfile.getString("organizationNumber"));
        String regBody = practitionerProfile.getString("regulatoryBody");
        String regNumber = practitionerProfile.getString("registrationNumber");
        if ((regBody != null && !regBody.isEmpty()) || (regNumber != null && !regNumber.isEmpty()))
        {
            sb.append("\n");
            if (regBody != null && !regBody.isEmpty()) sb.append(regBody);
            if (regBody != null && !regBody.isEmpty() && regNumber != null && !regNumber.isEmpty()) sb.append(" # ");
            if (regNumber != null && !regNumber.isEmpty()) sb.append(regNumber);
        }
        doc.add(new Paragraph(sb.toString()).setFont(font).setFontSize(10).setMarginBottom(8));
    }

    private void appendLine(StringBuilder sb, String value)
    {
        if (StringUtils.isNotBlank(value))
        {
            sb.append("\n").append(value.trim());
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

    private void addConsultationInfo(Document doc, PdfFont font, TcmConsultation consultation, TcmPatient patient)
    {
        Table infoTable = new Table(UnitValue.createPercentArray(new float[] { 1, 1 })).useAllAvailableWidth();
        addInfoRow(infoTable, font, "Consultation ID", consultation.getConsultationId());
        addInfoRow(infoTable, font, "Date", consultation.getConsultDate());
        addInfoRow(infoTable, font, "Patient", patient != null ? patient.getName() : "-");
        addInfoRow(infoTable, font, "Status", consultation.getStatus());
        doc.add(infoTable);
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

    private void addInvoiceItems(Document doc, PdfFont font, JSONArray services, String currency)
    {
        if (services == null || services.isEmpty())
        {
            return;
        }
        addSectionTitle(doc, font, "Service Items");
        Table serviceTable = new Table(UnitValue.createPercentArray(new float[] { 3, 1, 1, 1 })).useAllAvailableWidth();
        addTableHeader(serviceTable, font, "Service", "Unit Price", "Qty", "Subtotal");
        for (int i = 0; i < services.size(); i++)
        {
            JSONObject service = services.getJSONObject(i);
            BigDecimal price = service.getBigDecimal("price") != null ? service.getBigDecimal("price") : BigDecimal.ZERO;
            int qty = service.getIntValue("quantity", 1);
            BigDecimal subtotal = price.multiply(BigDecimal.valueOf(qty));
            addTableRow(
                    serviceTable,
                    font,
                    service.getString("name"),
                    formatMoney(price, currency),
                    String.valueOf(qty),
                    formatMoney(subtotal, currency));
        }
        doc.add(serviceTable);
    }

    private void addInvoicePrescriptionItems(Document doc, PdfFont font, JSONObject payload, String currency)
    {
        JSONArray prescriptions = payload.getJSONArray("prescriptions");
        if (prescriptions == null || prescriptions.isEmpty()) return;
        Boolean includeRx = payload.getBoolean("includeRxAmount");
        if (includeRx == null || !includeRx) return;

        Table rxTable = new Table(UnitValue.createPercentArray(new float[] { 3, 1, 1, 1 })).useAllAvailableWidth();
        addTableHeader(rxTable, font, "Prescription", "Unit Price", "Qty", "Subtotal");
        for (int p = 0; p < prescriptions.size(); p++)
        {
            JSONObject rx = prescriptions.getJSONObject(p);
            String rxStatus = rx.getString("rxStatus");
            if ("deleted".equals(rxStatus) || rx.getString("deletedAt") != null) continue;
            if (!"pending".equals(rxStatus) && !"dispensed".equals(rxStatus)) continue;

            String formulaName = rx.getString("formulaName");
            String prescType = rx.getString("prescriptionType");
            String name = (formulaName != null && !formulaName.isEmpty()) ? formulaName : safeValue(prescType);
            int rxQty = rx.getIntValue("quantity", 1);
            BigDecimal perDose = rx.getBigDecimal("perDoseSubtotal") != null ? rx.getBigDecimal("perDoseSubtotal") : BigDecimal.ZERO;
            BigDecimal subtotal = rx.getBigDecimal("subtotal") != null ? rx.getBigDecimal("subtotal") : BigDecimal.ZERO;
            addTableRow(rxTable, font, name, formatMoney(perDose, currency), String.valueOf(rxQty), formatMoney(subtotal, currency));
        }
        doc.add(rxTable);
    }

    private void addInvoiceTotals(Document doc, PdfFont font, JSONObject payload, String currency)
    {
        doc.add(new Paragraph("\n").setFontSize(4));
        BigDecimal totalAmount = payload.getBigDecimal("totalAmount");
        BigDecimal taxAmount = payload.getBigDecimal("taxAmount");
        if (totalAmount == null)
        {
            totalAmount = BigDecimal.ZERO;
        }
        if (taxAmount == null)
        {
            taxAmount = BigDecimal.ZERO;
        }

        Table totalTable = new Table(UnitValue.createPercentArray(new float[] { 3, 1 })).useAllAvailableWidth();
        totalTable.addCell(new Cell().add(new Paragraph("Subtotal").setFont(font).setFontSize(10))
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT));
        totalTable.addCell(new Cell().add(new Paragraph(formatMoney(totalAmount.subtract(taxAmount), currency))
                .setFont(font)
                .setFontSize(10))
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT));
        totalTable.addCell(new Cell().add(new Paragraph("Tax").setFont(font).setFontSize(10))
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
        doc.add(totalTable);
    }

    private String resolveCurrency(JSONObject payload)
    {
        String currency = payload != null ? payload.getString("currency") : null;
        return StringUtils.defaultIfBlank(currency, "CAD");
    }

    private String formatMoney(BigDecimal amount, String currency)
    {
        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;
        return StringUtils.defaultIfBlank(currency, "CAD") + " " + safeAmount.toPlainString();
    }

    private void addParagraphSection(Document doc, PdfFont font, String title, String content)
    {
        addSectionTitle(doc, font, title);
        doc.add(new Paragraph(safeContent(content)).setFont(font).setFontSize(10));
    }

    private void addOptionalSection(Document doc, PdfFont font, String title, String content)
    {
        if ("-".equals(content))
        {
            return;
        }
        addParagraphSection(doc, font, title, content);
    }

    private void addOptionalParagraph(Document doc, PdfFont font, String content)
    {
        if ("-".equals(content))
        {
            return;
        }
        doc.add(new Paragraph(content).setFont(font).setFontSize(10));
    }

    private String buildDiffSummary(JSONObject diff)
    {
        String[] keys = { "coldHeat", "sweat", "sleep", "appetite", "thirst", "bowelMovement", "urine", "pulse",
                "tongueColor", "tongueBody", "tongueCoating" };
        String[] labels = { "Cold/Heat", "Sweat", "Sleep", "Appetite", "Thirst", "Bowel", "Urine", "Pulse",
                "Tongue Color", "Tongue Body", "Tongue Coat" };
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < keys.length; i++)
        {
            String value = diff.getString(keys[i]);
            if (value != null && !value.isEmpty())
            {
                if (builder.length() > 0)
                {
                    builder.append("  ");
                }
                builder.append(labels[i]).append(": ").append(value);
            }
        }
        return builder.length() > 0 ? builder.toString() : "-";
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

    private void addHeader(Document doc, PdfFont font, String clinicName, String subtitle)
    {
        doc.add(new Paragraph(clinicName).setFont(font).setFontSize(18).setBold().setFontColor(PRIMARY_COLOR)
                .setTextAlignment(TextAlignment.CENTER));
        doc.add(new Paragraph(subtitle).setFont(font).setFontSize(11).setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER).setMarginBottom(15));
    }

    private void addSectionTitle(Document doc, PdfFont font, String title)
    {
        doc.add(new Paragraph(title).setFont(font).setFontSize(12).setBold().setFontColor(PRIMARY_COLOR)
                .setMarginTop(12).setMarginBottom(4).setBorderLeft(new SolidBorder(PRIMARY_COLOR, 3))
                .setPaddingLeft(8));
    }

    private void addInfoRow(Table table, PdfFont font, String label, String value)
    {
        table.addCell(new Cell().add(new Paragraph(label + ": " + safeValue(value)).setFont(font).setFontSize(10))
                .setBorder(Border.NO_BORDER));
    }

    private void addTableHeader(Table table, PdfFont font, String... headers)
    {
        for (String header : headers)
        {
            table.addHeaderCell(new Cell().add(new Paragraph(header).setFont(font).setFontSize(9).setBold())
                    .setBackgroundColor(new DeviceRgb(245, 245, 245)));
        }
    }

    private void addTableRow(Table table, PdfFont font, String... values)
    {
        for (String value : values)
        {
            table.addCell(new Cell().add(new Paragraph(safeValue(value)).setFont(font).setFontSize(9)));
        }
    }

    private void addFooter(Document doc, PdfFont font)
    {
        doc.add(new Paragraph("\nGenerated by TCM clinic system - " + new SimpleDateFormat("yyyy-MM-dd").format(new Date()))
                .setFont(font)
                .setFontSize(8)
                .setFontColor(ColorConstants.GRAY)
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
            image.setMaxHeight(90);
            image.setMarginTop(12);
            image.setTextAlignment(TextAlignment.CENTER);
            doc.add(image);
        }
        catch (Exception e)
        {
            log.warn("Invoice footer image ignored: {}", e.getMessage());
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

    private void mergeProfileFallback(JSONObject target, JSONObject fallback, String key)
    {
        if (target == null || fallback == null || StringUtils.isBlank(key))
        {
            return;
        }
        if (StringUtils.isBlank(target.getString(key)) && StringUtils.isNotBlank(fallback.getString(key)))
        {
            target.put(key, fallback.getString(key));
        }
    }

    private String safeStr(JSONObject obj, String key)
    {
        return safeValue(obj.getString(key));
    }

    private String safeValue(String value)
    {
        return value != null && !value.isEmpty() ? value : "-";
    }

    private String safeContent(String content)
    {
        return content != null && !content.isEmpty() ? content : "-";
    }

    private String firstNonBlank(String... values)
    {
        if (values == null)
        {
            return "-";
        }
        for (String value : values)
        {
            if (StringUtils.isNotBlank(value) && !"-".equals(value.trim()))
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
        file.setFileName(prefix + "-" + safeValue(consultation.getConsultationId()) + ".pdf");
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
