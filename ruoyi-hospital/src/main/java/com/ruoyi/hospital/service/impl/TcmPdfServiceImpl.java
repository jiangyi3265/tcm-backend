package com.ruoyi.hospital.service.impl;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
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
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.hospital.domain.TcmConsultation;
import com.ruoyi.hospital.domain.TcmPatient;
import com.ruoyi.hospital.domain.TcmClinicSetting;
import com.ruoyi.hospital.mapper.TcmClinicSettingMapper;
import com.ruoyi.hospital.mapper.TcmConsultationMapper;
import com.ruoyi.hospital.mapper.TcmPatientMapper;
import com.ruoyi.hospital.service.ITcmPdfService;
import com.ruoyi.hospital.util.SignedFileUrlService;

/**
 * PDF生成 Service实现
 * 使用 iText 7 生成诊疗报告和发票 PDF
 *
 * @author ruoyi
 */
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

    @Override
    public Map<String, String> generateConsultationReport(String consultationId)
    {
        TcmConsultation c = consultationMapper.selectTcmConsultationById(consultationId);
        if (c == null) throw new ServiceException("诊疗记录不存在");

        TcmPatient patient = patientMapper.selectTcmPatientById(c.getPatientId());
        JSONObject payload = parsePayload(c.getPayload());
        String clinicName = getClinicName();

        String fileName = generateFileName("report", consultationId);
        String filePath = RuoYiConfig.getUploadPath() + "/" + fileName;
        ensureDir(filePath);

        try
        {
            PdfWriter writer = new PdfWriter(new FileOutputStream(filePath));
            PdfDocument pdf = new PdfDocument(writer);
            Document doc = new Document(pdf);
            PdfFont font = createFont();

            // Header
            addHeader(doc, font, clinicName, "诊疗报告 Consultation Report");

            // 基本信息
            addSectionTitle(doc, font, "基本信息");
            Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();
            addInfoRow(infoTable, font, "诊疗编号", c.getConsultationId() != null ? c.getConsultationId() : "-");
            addInfoRow(infoTable, font, "日期", c.getConsultDate() != null ? c.getConsultDate() : "-");
            addInfoRow(infoTable, font, "病人", patient != null ? patient.getName() : "-");
            addInfoRow(infoTable, font, "状态", c.getStatus() != null ? c.getStatus() : "-");
            doc.add(infoTable);

            // 主诉
            addSectionTitle(doc, font, "主诉");
            doc.add(new Paragraph(safeStr(payload, "chiefComplaint") + " (" + safeStr(payload, "chiefComplaintDuration") + ")")
                    .setFont(font).setFontSize(11).setBold());
            String desc = safeStr(payload, "chiefComplaintDescription");
            if (!desc.equals("-")) doc.add(new Paragraph(desc).setFont(font).setFontSize(10));

            // 辨证
            JSONObject diff = payload.getJSONObject("diff");
            if (diff != null)
            {
                addSectionTitle(doc, font, "辨证");
                StringBuilder sb = new StringBuilder();
                String[] keys = {"coldHeat", "sweat", "sleep", "appetite", "thirst", "bowelMovement", "urine", "pulse", "tongueColor", "tongueBody", "tongueCoating"};
                String[] labels = {"寒热", "汗", "睡眠", "胃口", "口渴", "大便", "小便", "脉", "舌色", "舌体", "舌苔"};
                for (int i = 0; i < keys.length; i++)
                {
                    String v = diff.getString(keys[i]);
                    if (v != null && !v.isEmpty()) sb.append(labels[i]).append(": ").append(v).append("  ");
                }
                if (sb.length() > 0) doc.add(new Paragraph(sb.toString()).setFont(font).setFontSize(10));

                String differentiation = safeStr(payload, "differentiation");
                if (!differentiation.equals("-"))
                {
                    doc.add(new Paragraph("辨证结论: " + differentiation).setFont(font).setFontSize(10).setBold());
                }
            }

            // 针灸
            JSONArray acupuncture = payload.getJSONArray("acupuncture");
            if (acupuncture != null && !acupuncture.isEmpty())
            {
                addSectionTitle(doc, font, "针灸穴位");
                Table acuTable = new Table(UnitValue.createPercentArray(new float[]{2, 1, 2})).useAllAvailableWidth();
                addTableHeader(acuTable, font, "穴位", "侧", "备注");
                for (int i = 0; i < acupuncture.size(); i++)
                {
                    JSONObject pt = acupuncture.getJSONObject(i);
                    addTableRow(acuTable, font,
                            pt.getString("point"),
                            pt.getString("side"),
                            pt.getString("notes"));
                }
                doc.add(acuTable);
            }

            // 处方
            JSONArray herbals = payload.getJSONArray("herbals");
            if (herbals != null && !herbals.isEmpty())
            {
                addSectionTitle(doc, font, "中药处方");
                doc.add(new Paragraph("方名: " + safeStr(payload, "formulaName") + "  类型: " + safeStr(payload, "prescriptionType"))
                        .setFont(font).setFontSize(10));
                Table herbTable = new Table(UnitValue.createPercentArray(new float[]{2, 1, 1})).useAllAvailableWidth();
                addTableHeader(herbTable, font, "药材", "剂量", "单位");
                for (int i = 0; i < herbals.size(); i++)
                {
                    JSONObject h = herbals.getJSONObject(i);
                    addTableRow(herbTable, font,
                            h.getString("name"),
                            String.valueOf(h.get("dosage")),
                            h.getString("unit"));
                }
                doc.add(herbTable);
            }

            // 预后
            String prognosis = safeStr(payload, "prognosis");
            if (!prognosis.equals("-"))
            {
                addSectionTitle(doc, font, "预后");
                doc.add(new Paragraph(prognosis).setFont(font).setFontSize(10));
            }

            // Footer
            doc.add(new Paragraph("\n此文档由中医诊所综合管理系统自动生成 · " +
                    new SimpleDateFormat("yyyy-MM-dd").format(new Date()))
                    .setFont(font).setFontSize(8).setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER));

            doc.close();
            log.info("诊疗报告PDF生成成功: {}", filePath);
        }
        catch (ServiceException e) { throw e; }
        catch (Exception e)
        {
            log.error("PDF生成失败", e);
            throw new ServiceException("PDF生成失败: " + e.getMessage());
        }

        String resourcePath = "/profile/upload/" + fileName;
        Map<String, String> result = new HashMap<>();
        result.put("filePath", resourcePath);
        result.put("resource", resourcePath);
        result.put("url", signedFileUrlService.buildAccessUrl(resourcePath));
        return result;
    }

    @Override
    public Map<String, String> generateInvoice(String consultationId)
    {
        TcmConsultation c = consultationMapper.selectTcmConsultationById(consultationId);
        if (c == null) throw new ServiceException("诊疗记录不存在");

        TcmPatient patient = patientMapper.selectTcmPatientById(c.getPatientId());
        JSONObject payload = parsePayload(c.getPayload());
        String clinicName = getClinicName();

        String fileName = generateFileName("invoice", consultationId);
        String filePath = RuoYiConfig.getUploadPath() + "/" + fileName;
        ensureDir(filePath);

        try
        {
            PdfWriter writer = new PdfWriter(new FileOutputStream(filePath));
            PdfDocument pdf = new PdfDocument(writer);
            Document doc = new Document(pdf);
            PdfFont font = createFont();

            addHeader(doc, font, clinicName, "发票 Invoice");

            // 基本信息
            Table infoTable = new Table(UnitValue.createPercentArray(new float[]{1, 1})).useAllAvailableWidth();
            addInfoRow(infoTable, font, "发票编号", c.getConsultationId() != null ? c.getConsultationId() : consultationId.substring(Math.max(0, consultationId.length() - 8)));
            addInfoRow(infoTable, font, "日期", c.getConsultDate() != null ? c.getConsultDate() : "-");
            addInfoRow(infoTable, font, "病人", patient != null ? patient.getName() : "-");
            addInfoRow(infoTable, font, "状态", c.getStatus() != null ? c.getStatus() : "-");
            doc.add(infoTable);

            // 服务项目
            JSONArray services = payload.getJSONArray("services");
            if (services != null && !services.isEmpty())
            {
                addSectionTitle(doc, font, "服务项目");
                Table svcTable = new Table(UnitValue.createPercentArray(new float[]{3, 1, 1, 1})).useAllAvailableWidth();
                addTableHeader(svcTable, font, "服务", "单价", "数量", "小计");
                for (int i = 0; i < services.size(); i++)
                {
                    JSONObject s = services.getJSONObject(i);
                    BigDecimal price = s.getBigDecimal("price") != null ? s.getBigDecimal("price") : BigDecimal.ZERO;
                    int qty = s.getIntValue("quantity", 1);
                    BigDecimal subtotal = price.multiply(BigDecimal.valueOf(qty));
                    addTableRow(svcTable, font,
                            s.getString("name"),
                            "¥" + price.toPlainString(),
                            String.valueOf(qty),
                            "¥" + subtotal.toPlainString());
                }
                doc.add(svcTable);
            }

            // 总计
            doc.add(new Paragraph("\n").setFontSize(4));
            BigDecimal totalAmount = payload.getBigDecimal("totalAmount");
            BigDecimal taxAmount = payload.getBigDecimal("taxAmount");
            if (totalAmount == null) totalAmount = BigDecimal.ZERO;
            if (taxAmount == null) taxAmount = BigDecimal.ZERO;

            Table totalTable = new Table(UnitValue.createPercentArray(new float[]{3, 1})).useAllAvailableWidth();
            totalTable.addCell(new Cell().add(new Paragraph("小计").setFont(font).setFontSize(10))
                    .setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT));
            totalTable.addCell(new Cell().add(new Paragraph("¥" + totalAmount.subtract(taxAmount).toPlainString())
                    .setFont(font).setFontSize(10)).setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT));
            totalTable.addCell(new Cell().add(new Paragraph("税额").setFont(font).setFontSize(10))
                    .setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT));
            totalTable.addCell(new Cell().add(new Paragraph("¥" + taxAmount.toPlainString())
                    .setFont(font).setFontSize(10)).setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT));
            totalTable.addCell(new Cell().add(new Paragraph("总计").setFont(font).setFontSize(14).setBold()
                    .setFontColor(PRIMARY_COLOR)).setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT));
            totalTable.addCell(new Cell().add(new Paragraph("¥" + totalAmount.toPlainString())
                    .setFont(font).setFontSize(14).setBold().setFontColor(PRIMARY_COLOR))
                    .setBorder(Border.NO_BORDER).setTextAlignment(TextAlignment.RIGHT));
            doc.add(totalTable);

            // Footer
            doc.add(new Paragraph("\n此文档由中医诊所综合管理系统自动生成 · " +
                    new SimpleDateFormat("yyyy-MM-dd").format(new Date()))
                    .setFont(font).setFontSize(8).setFontColor(ColorConstants.GRAY)
                    .setTextAlignment(TextAlignment.CENTER));

            doc.close();
            log.info("发票PDF生成成功: {}", filePath);
        }
        catch (ServiceException e) { throw e; }
        catch (Exception e)
        {
            log.error("PDF生成失败", e);
            throw new ServiceException("PDF生成失败: " + e.getMessage());
        }

        String resourcePath = "/profile/upload/" + fileName;
        Map<String, String> result = new HashMap<>();
        result.put("filePath", resourcePath);
        result.put("resource", resourcePath);
        result.put("url", signedFileUrlService.buildAccessUrl(resourcePath));
        return result;
    }

    // ========== Helper Methods ==========

    private PdfFont createFont()
    {
        try
        {
            // 尝试使用系统中文字体
            String[] fontPaths = {
                "C:/Windows/Fonts/msyh.ttc,0",    // 微软雅黑
                "C:/Windows/Fonts/simsun.ttc,0",   // 宋体
                "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc,0", // Linux 文泉驿
            };
            for (String fp : fontPaths)
            {
                try
                {
                    String path = fp.contains(",") ? fp.split(",")[0] : fp;
                    if (new File(path).exists())
                    {
                        return PdfFontFactory.createFont(fp, "Identity-H");
                    }
                }
                catch (Exception ignored) {}
            }
            // Fallback: 使用内置字体（不支持中文）
            return PdfFontFactory.createFont();
        }
        catch (Exception e)
        {
            throw new ServiceException("字体加载失败");
        }
    }

    private void addHeader(Document doc, PdfFont font, String clinicName, String subtitle)
    {
        doc.add(new Paragraph(clinicName != null ? clinicName : "中医诊所综合管理系统")
                .setFont(font).setFontSize(18).setBold().setFontColor(PRIMARY_COLOR)
                .setTextAlignment(TextAlignment.CENTER));
        doc.add(new Paragraph(subtitle)
                .setFont(font).setFontSize(11).setFontColor(ColorConstants.GRAY)
                .setTextAlignment(TextAlignment.CENTER).setMarginBottom(15));
    }

    private void addSectionTitle(Document doc, PdfFont font, String title)
    {
        doc.add(new Paragraph(title)
                .setFont(font).setFontSize(12).setBold().setFontColor(PRIMARY_COLOR)
                .setMarginTop(12).setMarginBottom(4)
                .setBorderLeft(new SolidBorder(PRIMARY_COLOR, 3)).setPaddingLeft(8));
    }

    private void addInfoRow(Table table, PdfFont font, String label, String value)
    {
        table.addCell(new Cell().add(new Paragraph(label + ": " + (value != null ? value : "-"))
                .setFont(font).setFontSize(10)).setBorder(Border.NO_BORDER));
    }

    private void addTableHeader(Table table, PdfFont font, String... headers)
    {
        for (String h : headers)
        {
            table.addHeaderCell(new Cell().add(new Paragraph(h).setFont(font).setFontSize(9).setBold())
                    .setBackgroundColor(new DeviceRgb(245, 245, 245)));
        }
    }

    private void addTableRow(Table table, PdfFont font, String... values)
    {
        for (String v : values)
        {
            table.addCell(new Cell().add(new Paragraph(v != null ? v : "-").setFont(font).setFontSize(9)));
        }
    }

    private JSONObject parsePayload(String payloadStr)
    {
        if (payloadStr != null && !payloadStr.isEmpty())
        {
            try { return JSON.parseObject(payloadStr); }
            catch (Exception e) { /* ignore */ }
        }
        return new JSONObject();
    }

    private String safeStr(JSONObject obj, String key)
    {
        String v = obj.getString(key);
        return v != null && !v.isEmpty() ? v : "-";
    }

    private String getClinicName()
    {
        try
        {
            TcmClinicSetting setting = settingMapper.selectSettingByKey("clinicName");
            return setting != null ? setting.getSettingValue() : "中医诊所";
        }
        catch (Exception e) { return "中医诊所"; }
    }

    private String generateFileName(String type, String id)
    {
        String dateDir = new SimpleDateFormat("yyyy/MM/dd").format(new Date());
        String shortId = id.length() > 8 ? id.substring(id.length() - 8) : id;
        return dateDir + "/" + type + "_" + shortId + "_" + System.currentTimeMillis() + ".pdf";
    }

    private void ensureDir(String filePath)
    {
        File file = new File(filePath);
        if (!file.getParentFile().exists())
        {
            file.getParentFile().mkdirs();
        }
    }
}
