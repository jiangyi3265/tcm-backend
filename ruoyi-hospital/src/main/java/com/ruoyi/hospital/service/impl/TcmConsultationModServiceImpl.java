package com.ruoyi.hospital.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.hospital.domain.TcmConsultationMod;
import com.ruoyi.hospital.mapper.TcmConsultationModMapper;
import com.ruoyi.hospital.service.ITcmConsultationModService;

/**
 * 问诊修改记录 Service业务层处理
 *
 * @author ruoyi
 */
@Service
public class TcmConsultationModServiceImpl implements ITcmConsultationModService
{
    @Autowired
    private TcmConsultationModMapper tcmConsultationModMapper;

    /**
     * 查询全部修改记录
     *
     * @param mod 查询条件
     * @return 修改记录集合
     */
    @Override
    public List<TcmConsultationMod> selectTcmConsultationModList(TcmConsultationMod mod)
    {
        return tcmConsultationModMapper.selectTcmConsultationModList(mod);
    }

    /**
     * 根据问诊ID查询修改记录列表
     *
     * @param consultationId 问诊ID
     * @return 修改记录集合
     */
    @Override
    public List<TcmConsultationMod> selectModsByConsultationId(String consultationId)
    {
        return tcmConsultationModMapper.selectModsByConsultationId(consultationId);
    }

    /**
     * 新增修改记录
     *
     * @param mod 修改记录信息
     * @return 影响行数
     */
    @Override
    public int insertMod(TcmConsultationMod mod)
    {
        return tcmConsultationModMapper.insertTcmConsultationMod(mod);
    }
}
