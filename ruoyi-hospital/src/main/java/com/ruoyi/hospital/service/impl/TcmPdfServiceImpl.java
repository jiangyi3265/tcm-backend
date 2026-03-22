package com.ruoyi.hospital.service.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
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
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmClinicSetting;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
import com.ruoyi.hospital.mapper.TcmConsultationMapper;
import com.ruoyi.hospital.mapper.TcmPatientMapper;
import com.ruoyi.hospital.service.ITcmPdfService;
import com.ruoyi.hospital.util.HospitalFileStorage;
import com.ruoyi.hospital.util.SignedFileUrlService;

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

            addParagraphSection(
                    doc,
                    font,
                    "Chief Complaint",
                    safeStr(payload, "chiefComplaint") + " (" + safeStr(payload, "chiefComplaintDuration") + ")");
            addOptionalParagraph(doc, font, safeStr(payload, "chiefComplaintDescription"));

            JSONObject diff = payload.getJSONObject("diff");
            if (diff != null)
            {
                addParagraphSection(doc, font, "Differentiation", buildDiffSummary(diff));
                addOptionalParagraph(doc, font, safeStr(payload, "differentiation"));
            }

            addHerbalSection(doc, font, payload.getJSONArray("herbals"), payload);
            addOptionalSection(doc, font, "Prognosis", safeStr(payload, "prognosis"));
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

        return buildResult(resourcePath);
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
        String clinicName = getClinicName();
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
            addConsultationInfo(doc, font, consultation, patient);
            addInvoiceItems(doc, font, payload.getJSONArray("services"));
            addInvoiceTotals(doc, font, payload);
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

        return buildResult(resourcePath);
    }

    private Map<String, String> buildResult(String resourcePath)
    {
        Map<String, String> result = new HashMap<>();
        result.put("filePath", resourcePath);
        result.put("resource", resourcePath);
        result.put("url", signedFileUrlService.buildAccessUrl(resourcePath));
        return result;
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

    private void addInvoiceItems(Document doc, PdfFont font, JSONArray services)
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
                    "¥" + price.toPlainString(),
                    String.valueOf(qty),
                    "¥" + subtotal.toPlainString());
        }
        doc.add(serviceTable);
    }

    private void addInvoiceTotals(Document doc, PdfFont font, JSONObject payload)
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
        totalTable.addCell(new Cell().add(new Paragraph("¥" + totalAmount.subtract(taxAmount).toPlainString())
                .setFont(font)
                .setFontSize(10))
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT));
        totalTable.addCell(new Cell().add(new Paragraph("Tax").setFont(font).setFontSize(10))
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT));
        totalTable.addCell(new Cell().add(new Paragraph("¥" + taxAmount.toPlainString()).setFont(font).setFontSize(10))
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT));
        totalTable.addCell(new Cell().add(new Paragraph("Total").setFont(font).setFontSize(14).setBold()
                .setFontColor(PRIMARY_COLOR))
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT));
        totalTable.addCell(new Cell().add(new Paragraph("¥" + totalAmount.toPlainString()).setFont(font)
                .setFontSize(14)
                .setBold()
                .setFontColor(PRIMARY_COLOR))
                .setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.RIGHT));
        doc.add(totalTable);
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
        return value != null && !value.isEmpty() ? value : "-";
    }

    private String safeContent(String content)
    {
        return content != null && !content.isEmpty() ? content : "-";
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
