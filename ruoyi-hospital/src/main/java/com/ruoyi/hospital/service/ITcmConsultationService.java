package com.ruoyi.hospital.service;

import java.util.List;
import java.util.Map;
import com.ruoyi.hospital.domain.TcmConsultation;

/**
 * 中医问诊 Service接口
 *
 * @author ruoyi
 */
public interface ITcmConsultationService
{
    /**
     * 查询问诊列表
     *
     * @param consultation 问诊查询条件
     * @return 问诊集合
     */
    List<TcmConsultation> selectTcmConsultationList(TcmConsultation consultation);

    /**
     * 查询问诊详情
     *
     * @param id 问诊ID
     * @return 问诊信息
     */
    TcmConsultation selectTcmConsultationById(String id);

    /**
     * 新增问诊
     *
     * @param consultation 问诊信息
     * @return 影响行数
     */
    int insertTcmConsultation(TcmConsultation consultation);

    /**
     * 修改问诊
     *
     * @param consultation 问诊信息
     * @param actorId      操作者ID
     * @return 影响行数
     */
    int updateTcmConsultation(TcmConsultation consultation, String actorId);

    /**
     * 完成问诊
     *
     * @param id      问诊ID
     * @param actorId 操作者ID
     * @return 完成后的问诊对象
     */
    TcmConsultation completeConsultation(String id, String actorId);

    /**
     * 标记已付款
     *
     * @param id      问诊ID
     * @param actorId 操作者ID
     * @return 标记后的问诊对象
     */
    TcmConsultation markPaid(String id, String actorId, Map<String, Object> paymentInfo);

    /**
     * 标记配药完成
     *
     * @param id      问诊ID
     * @param actorId 操作者ID
     * @return 标记后的问诊对象
     */
    TcmConsultation markDispensingComplete(String id, String actorId);

    /**
     * 软删除问诊
     *
     * @param id 问诊ID
     * @return 软删除后的问诊对象
     */
    TcmConsultation softDeleteTcmConsultation(String id);

    /**
     * 恢复已软删除的问诊
     *
     * @param id 问诊ID
     * @return 恢复后的问诊对象
     */
    TcmConsultation restoreTcmConsultation(String id);

    /**
     * 硬删除问诊
     *
     * @param id 问诊ID
     * @return 影响行数
     */
    int hardDeleteTcmConsultation(String id);
}
