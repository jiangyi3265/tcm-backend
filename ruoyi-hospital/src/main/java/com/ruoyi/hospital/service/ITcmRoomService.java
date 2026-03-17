package com.ruoyi.hospital.service;

import java.util.List;
import com.ruoyi.hospital.domain.TcmRoom;

/**
 * 诊室管理 Service接口
 *
 * @author ruoyi
 */
public interface ITcmRoomService
{
    /**
     * 查询诊室列表
     *
     * @param room 诊室查询条件
     * @return 诊室集合
     */
    List<TcmRoom> selectTcmRoomList(TcmRoom room);

    /**
     * 查询诊室详情
     *
     * @param id 诊室ID
     * @return 诊室信息
     */
    TcmRoom selectTcmRoomById(String id);

    /**
     * 新增诊室
     *
     * @param room 诊室信息
     * @return 影响行数
     */
    int insertTcmRoom(TcmRoom room);

    /**
     * 修改诊室
     *
     * @param room 诊室信息
     * @return 影响行数
     */
    int updateTcmRoom(TcmRoom room);
}
