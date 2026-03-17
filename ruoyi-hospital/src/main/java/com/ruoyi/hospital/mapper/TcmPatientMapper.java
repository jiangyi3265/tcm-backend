package com.ruoyi.hospital.mapper;

import java.util.List;
import com.ruoyi.hospital.domain.TcmPatient;

/**
 * 患者Mapper接口
 *
 * @author ruoyi
 */
public interface TcmPatientMapper
{
    /**
     * 查询患者列表
     *
     * @param tcmPatient 患者
     * @return 患者集合
     */
    List<TcmPatient> selectTcmPatientList(TcmPatient tcmPatient);

    /**
     * 查询患者
     *
     * @param id 患者主键
     * @return 患者
     */
    TcmPatient selectTcmPatientById(String id);

    /**
     * 根据邮箱查询患者
     *
     * @param email 邮箱
     * @return 患者
     */
    TcmPatient selectTcmPatientByEmail(String email);

    /**
     * 根据同意书令牌查询患者
     *
     * @param consentToken 同意书令牌
     * @return 患者
     */
    TcmPatient selectTcmPatientByConsentToken(String consentToken);

    /**
     * 新增患者
     *
     * @param tcmPatient 患者
     * @return 结果
     */
    int insertTcmPatient(TcmPatient tcmPatient);

    /**
     * 修改患者
     *
     * @param tcmPatient 患者
     * @return 结果
     */
    int updateTcmPatient(TcmPatient tcmPatient);

    /**
     * 删除患者
     *
     * @param id 患者主键
     * @return 结果
     */
    int deleteTcmPatientById(String id);
}
