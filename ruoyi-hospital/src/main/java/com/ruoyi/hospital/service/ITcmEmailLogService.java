package com.ruoyi.hospital.service;

import java.util.List;
import com.ruoyi.hospital.domain.TcmEmailLog;

/**
 * 邮件日志 Service接口
 *
 * @author ruoyi
 */
public interface ITcmEmailLogService
{
    /**
     * 查询邮件日志列表
     *
     * @param emailLog 邮件日志查询条件
     * @return 邮件日志集合
     */
    List<TcmEmailLog> selectTcmEmailLogList(TcmEmailLog emailLog);

    TcmEmailLog selectTcmEmailLogById(Long id);

    /**
     * 新增邮件日志
     *
     * @param emailLog 邮件日志信息
     * @return 影响行数
     */
    int insertTcmEmailLog(TcmEmailLog emailLog);
}
