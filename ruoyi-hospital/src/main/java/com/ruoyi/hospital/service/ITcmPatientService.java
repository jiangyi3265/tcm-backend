package com.ruoyi.hospital.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.hospital.domain.TcmPatient;

/**
 * 中医患者 Service接口
 *
 * @author ruoyi
 */
public interface ITcmPatientService
{
    /**
     * 查询中医患者列表
     *
     * @param tcmPatient 中医患者查询条件
     * @return 中医患者集合
     */
    List<TcmPatient> selectTcmPatientList(TcmPatient tcmPatient);

    /**
     * 查询中医患者详情
     *
     * @param id 患者ID
     * @return 中医患者
     */
    TcmPatient selectTcmPatientById(String id);

    /**
     * 新增中医患者
     *
     * @param tcmPatient 中医患者信息
     * @return 影响行数
     */
    int insertTcmPatient(TcmPatient tcmPatient);

    /**
     * 修改中医患者
     *
     * @param tcmPatient 中医患者信息
     * @return 影响行数
     */
    int updateTcmPatient(TcmPatient tcmPatient);

    /**
     * 软删除中医患者
     *
     * @param id 患者ID
     * @return 软删除后的患者对象
     */
    TcmPatient softDeleteTcmPatient(String id);

    /**
     * 恢复已软删除的中医患者
     *
     * @param id 患者ID
     * @return 恢复后的患者对象
     */
    TcmPatient restoreTcmPatient(String id);

    /**
     * 硬删除中医患者
     *
     * @param id 患者ID
     * @return 影响行数
     */
    int hardDeleteTcmPatient(String id);

    /**
     * 合并两个患者记录
     *
     * @param keepId  保留的患者ID
     * @param mergeId 被合并的患者ID
     */
    void mergeTcmPatients(String keepId, String mergeId);

    /**
     * 签署知情同意书
     *
     * @param id 患者ID
     * @return 签署后的患者对象
     */
    TcmPatient signConsent(String id);

    TcmPatient signConsent(String id, String signatureName, Map<String, Object> sectionAcknowledgements);

    /**
     * 生成同意书签署令牌（用于邮件链接签署）
     *
     * @param id 患者ID
     * @return 令牌字符串
     */
    String generateConsentToken(String id);

    /**
     * 根据令牌查找患者（公开接口）
     *
     * @param token 同意书令牌
     * @return 患者信息（脱敏）
     */
    TcmPatient selectByConsentToken(String token);

    /**
     * 通过令牌签署同意书（公开接口）
     *
     * @param token         同意书令牌
     * @param signatureName 签署人姓名
     * @param sectionAcknowledgements 各分段已读并同意状态
     * @return 签署后的患者对象
     */
    TcmPatient signConsentByToken(String token, String signatureName, Map<String, Object> sectionAcknowledgements);

    /**
     * 生成公开问诊表令牌
     *
     * @param id 患者ID
     * @return 问诊表令牌
     */
    String generateIntakeToken(String id);

    /**
     * 通过公开问诊表令牌查找患者
     *
     * @param token 问诊表令牌
     * @return 患者信息
     */
    TcmPatient selectByIntakeToken(String token);

    /**
     * 通过公开问诊表令牌保存问诊信息
     *
     * @param token    问诊表令牌
     * @param formData 问诊表数据
     * @return 保存后的患者
     */
    TcmPatient saveIntakeFormByToken(String token, Map<String, Object> formData);

    /**
     * 将最近一次问诊表内容保存到患者档案中，供首诊快速回填
     *
     * @param patientId 患者ID
     * @param formData  问诊表数据
     */
    void saveLatestIntakeForm(String patientId, Map<String, Object> formData);

    /**
     * 将公开预约页收集到的轻量初诊信息合并到患者档案中，
     * 供首诊问诊时快速预填，同时保留既有完整问诊单字段。
     *
     * @param patientId 患者ID
     * @param appointmentId 预约ID
     * @param formData 公开预约页收集到的轻量初诊信息
     */
    void savePublicBookingIntakeSummary(String patientId, String appointmentId, Map<String, Object> formData);

    /**
     * 为员工自动创建或同步对应病人档案
     *
     * @param userId 员工用户ID
     * @param name 员工姓名
     * @param email 员工邮箱
     * @param phone 员工电话
     */
    void ensureStaffPatientProfile(Long userId, String name, String email, String phone);
}
