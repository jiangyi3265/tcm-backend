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
     */
    List<TcmConsultation> selectTcmConsultationList(TcmConsultation consultation);

    /**
     * 查询问诊详情
     */
    TcmConsultation selectTcmConsultationById(String id);

    /**
     * 新增问诊
     */
    int insertTcmConsultation(TcmConsultation consultation);

    /**
     * 修改问诊
     */
    int updateTcmConsultation(TcmConsultation consultation, String actorId);

    /**
     * 完成问诊
     */
    TcmConsultation completeConsultation(String id, String actorId);

    /**
     * 标记已付款
     */
    TcmConsultation markPaid(String id, String actorId, Map<String, Object> paymentInfo);

    /**
     * 标记配药完成
     */
    TcmConsultation markDispensingComplete(String id, String actorId);

    /**
     * 标记配药完成（可在库存已预扣时跳过重复扣减）
     *
     * @param skipDeduct 是否尝试跳过库存扣减
     */
    TcmConsultation markDispensingComplete(String id, String actorId, boolean skipDeduct);

    /**
     * 软删除问诊
     */
    TcmConsultation softDeleteTcmConsultation(String id);

    /**
     * 恢复已软删除的问诊
     */
    TcmConsultation restoreTcmConsultation(String id);

    /**
     * 硬删除问诊
     */
    int hardDeleteTcmConsultation(String id);
}
