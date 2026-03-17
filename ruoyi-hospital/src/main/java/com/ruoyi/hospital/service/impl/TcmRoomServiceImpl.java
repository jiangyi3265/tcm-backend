package com.ruoyi.hospital.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.hospital.domain.TcmRoom;
import com.ruoyi.hospital.mapper.TcmRoomMapper;
import com.ruoyi.hospital.service.ITcmRoomService;

/**
 * 诊室管理 Service业务层处理
 *
 * @author ruoyi
 */
@Service
public class TcmRoomServiceImpl implements ITcmRoomService
{
    @Autowired
    private TcmRoomMapper tcmRoomMapper;

    /**
     * 查询诊室列表
     *
     * @param room 诊室查询条件
     * @return 诊室集合
     */
    @Override
    public List<TcmRoom> selectTcmRoomList(TcmRoom room)
    {
        return tcmRoomMapper.selectTcmRoomList(room);
    }

    /**
     * 查询诊室详情
     *
     * @param id 诊室ID
     * @return 诊室信息
     */
    @Override
    public TcmRoom selectTcmRoomById(String id)
    {
        return tcmRoomMapper.selectTcmRoomById(id);
    }

    /**
     * 新增诊室
     *
     * @param room 诊室信息
     * @return 影响行数
     */
    @Override
    public int insertTcmRoom(TcmRoom room)
    {
        if (room.getId() == null || room.getId().isEmpty())
        {
            room.setId(java.util.UUID.randomUUID().toString());
        }
        room.setCreateTime(DateUtils.getNowDate());
        return tcmRoomMapper.insertTcmRoom(room);
    }

    /**
     * 修改诊室
     *
     * @param room 诊室信息
     * @return 影响行数
     */
    @Override
    public int updateTcmRoom(TcmRoom room)
    {
        return tcmRoomMapper.updateTcmRoom(room);
    }
}
