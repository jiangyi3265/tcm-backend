package com.ruoyi.hospital.mapper;

import java.util.List;
import com.ruoyi.hospital.domain.TcmRoom;

/**
 * 诊室Mapper接口
 *
 * @author ruoyi
 */
public interface TcmRoomMapper
{
    /**
     * 查询诊室列表
     *
     * @param room 诊室
     * @return 诊室集合
     */
    List<TcmRoom> selectTcmRoomList(TcmRoom room);

    /**
     * 查询诊室
     *
     * @param id 诊室主键
     * @return 诊室
     */
    TcmRoom selectTcmRoomById(String id);

    /**
     * 新增诊室
     *
     * @param room 诊室
     * @return 结果
     */
    int insertTcmRoom(TcmRoom room);

    /**
     * 修改诊室
     *
     * @param room 诊室
     * @return 结果
     */
    int updateTcmRoom(TcmRoom room);

    /**
     * 删除诊室
     *
     * @param id 诊室主键
     * @return 结果
     */
    int deleteTcmRoomById(String id);
}
