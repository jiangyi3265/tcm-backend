package com.ruoyi.hospital.mapper;

import java.util.List;
import com.ruoyi.hospital.domain.TcmEmailLog;

/**
 * 邮件日志Mapper接口
 *
 * @author ruoyi
 */
public interface TcmEmailLogMapper
{
    /**
     * 查询邮件日志列表
     *
     * @param emailLog 邮件日志
     * @return 邮件日志集合
     */
    List<TcmEmailLog> selectTcmEmailLogList(TcmEmailLog emailLog);

    /**
     * 新增邮件日志
     *
     * @param emailLog 邮件日志
     * @return 结果
     */
    int insertTcmEmailLog(TcmEmailLog emailLog);
}
