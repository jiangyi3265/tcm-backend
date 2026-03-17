package com.ruoyi.hospital.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.hospital.domain.TcmPriceList;
import com.ruoyi.hospital.mapper.TcmPriceListMapper;
import com.ruoyi.hospital.service.ITcmPriceListService;

/**
 * 价格表 Service业务层处理
 *
 * @author ruoyi
 */
@Service
public class TcmPriceListServiceImpl implements ITcmPriceListService
{
    @Autowired
    private TcmPriceListMapper tcmPriceListMapper;

    /**
     * 查询价格表列表
     *
     * @param priceList 价格表查询条件
     * @return 价格表集合
     */
    @Override
    public List<TcmPriceList> selectTcmPriceListList(TcmPriceList priceList)
    {
        return tcmPriceListMapper.selectTcmPriceListList(priceList);
    }

    /**
     * 查询价格表详情
     *
     * @param id 价格表ID
     * @return 价格表信息
     */
    @Override
    public TcmPriceList selectTcmPriceListById(String id)
    {
        return tcmPriceListMapper.selectTcmPriceListById(id);
    }

    /**
     * 新增价格表
     *
     * @param priceList 价格表信息
     * @return 影响行数
     */
    @Override
    public int insertTcmPriceList(TcmPriceList priceList)
    {
        if (priceList.getId() == null || priceList.getId().isEmpty())
        {
            priceList.setId(java.util.UUID.randomUUID().toString());
        }
        priceList.setCreateTime(DateUtils.getNowDate());
        return tcmPriceListMapper.insertTcmPriceList(priceList);
    }

    /**
     * 修改价格表
     *
     * @param priceList 价格表信息
     * @return 影响行数
     */
    @Override
    public int updateTcmPriceList(TcmPriceList priceList)
    {
        return tcmPriceListMapper.updateTcmPriceList(priceList);
    }

    /**
     * 删除价格表
     *
     * @param id 价格表ID
     * @return 影响行数
     */
    @Override
    public int deleteTcmPriceListById(String id)
    {
        return tcmPriceListMapper.deleteTcmPriceListById(id);
    }
}
