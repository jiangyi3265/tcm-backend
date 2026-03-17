package com.ruoyi.hospital.service;

import java.util.List;
import com.ruoyi.hospital.domain.TcmConsultationMod;

/**
 * 问诊修改记录 Service接口
 *
 * @author ruoyi
 */
public interface ITcmConsultationModService
{
    /**
     * 查询全部修改记录
     *
     * @param mod 查询条件
     * @return 修改记录集合
     */
    List<TcmConsultationMod> selectTcmConsultationModList(TcmConsultationMod mod);

    /**
     * 根据问诊ID查询修改记录列表
     *
     * @param consultationId 问诊ID
     * @return 修改记录集合
     */
    List<TcmConsultationMod> selectModsByConsultationId(String consultationId);

    /**
     * 新增修改记录
     *
     * @param mod 修改记录信息
     * @return 影响行数
     */
    int insertMod(TcmConsultationMod mod);
}
