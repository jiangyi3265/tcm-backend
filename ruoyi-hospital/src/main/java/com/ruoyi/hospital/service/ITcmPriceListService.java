package com.ruoyi.hospital.service;

import java.util.List;
import com.ruoyi.hospital.domain.TcmPriceList;

/**
 * 价格表 Service接口
 *
 * @author ruoyi
 */
public interface ITcmPriceListService
{
    /**
     * 查询价格表列表
     *
     * @param priceList 价格表查询条件
     * @return 价格表集合
     */
    List<TcmPriceList> selectTcmPriceListList(TcmPriceList priceList);

    /**
     * 查询价格表详情
     *
     * @param id 价格表ID
     * @return 价格表信息
     */
    TcmPriceList selectTcmPriceListById(String id);

    /**
     * 新增价格表
     *
     * @param priceList 价格表信息
     * @return 影响行数
     */
    int insertTcmPriceList(TcmPriceList priceList);

    /**
     * 修改价格表
     *
     * @param priceList 价格表信息
     * @return 影响行数
     */
    int updateTcmPriceList(TcmPriceList priceList);

    /**
     * 删除价格表
     *
     * @param id 价格表ID
     * @return 影响行数
     */
    int deleteTcmPriceListById(String id);
}
