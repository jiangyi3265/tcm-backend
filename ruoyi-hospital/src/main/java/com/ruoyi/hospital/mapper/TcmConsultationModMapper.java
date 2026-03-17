package com.ruoyi.hospital.mapper;

import java.util.List;
import com.ruoyi.hospital.domain.TcmConsultationMod;

/**
 * 问诊模块Mapper接口
 *
 * @author ruoyi
 */
public interface TcmConsultationModMapper
{
    /**
     * 查询修改记录列表
     *
     * @param mod 查询条件
     * @return 修改记录集合
     */
    List<TcmConsultationMod> selectTcmConsultationModList(TcmConsultationMod mod);

    /**
     * 根据问诊ID查询模块列表
     *
     * @param consultationId 问诊ID
     * @return 模块集合
     */
    List<TcmConsultationMod> selectModsByConsultationId(String consultationId);

    /**
     * 新增问诊模块
     *
     * @param mod 问诊模块
     * @return 结果
     */
    int insertTcmConsultationMod(TcmConsultationMod mod);
}
