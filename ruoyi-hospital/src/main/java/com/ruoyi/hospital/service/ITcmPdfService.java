package com.ruoyi.hospital.service;

import java.util.Map;

/**
 * PDF生成 Service接口
 *
 * @author ruoyi
 */
public interface ITcmPdfService
{
    /**
     * 生成诊疗报告PDF
     *
     * @param consultationId 诊疗ID
     * @return 包含 filePath 和 url 的结果
     */
    Map<String, String> generateConsultationReport(String consultationId);

    /**
     * 生成发票PDF
     *
     * @param consultationId 诊疗ID
     * @return 包含 filePath 和 url 的结果
     */
    Map<String, String> generateInvoice(String consultationId);

    /**
     * 生成知情同意书PDF
     *
     * @param patientId 患者ID
     * @param signatureName 签署姓名
     * @return 包含 filePath 和 url 的结果
     */
    Map<String, String> generateConsentForm(String patientId, String signatureName);
}
