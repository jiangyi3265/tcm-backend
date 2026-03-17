package com.ruoyi.hospital.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.hospital.domain.TcmEmailLog;
import com.ruoyi.hospital.mapper.TcmEmailLogMapper;
import com.ruoyi.hospital.service.ITcmEmailLogService;

/**
 * 邮件日志 Service业务层处理
 *
 * @author ruoyi
 */
@Service
public class TcmEmailLogServiceImpl implements ITcmEmailLogService
{
    @Autowired
    private TcmEmailLogMapper tcmEmailLogMapper;

    /**
     * 查询邮件日志列表
     *
     * @param emailLog 邮件日志查询条件
     * @return 邮件日志集合
     */
    @Override
    public List<TcmEmailLog> selectTcmEmailLogList(TcmEmailLog emailLog)
    {
        return tcmEmailLogMapper.selectTcmEmailLogList(emailLog);
    }

    /**
     * 新增邮件日志
     *
     * @param emailLog 邮件日志信息
     * @return 影响行数
     */
    @Override
    public int insertTcmEmailLog(TcmEmailLog emailLog)
    {
        return tcmEmailLogMapper.insertTcmEmailLog(emailLog);
    }
}
