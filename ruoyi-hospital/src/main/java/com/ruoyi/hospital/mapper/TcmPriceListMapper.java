package com.ruoyi.hospital.mapper;

import java.util.List;
import com.ruoyi.hospital.domain.TcmPriceList;

/**
 * 价格列表Mapper接口
 *
 * @author ruoyi
 */
public interface TcmPriceListMapper
{
    /**
     * 查询价格列表
     *
     * @param priceList 价格列表
     * @return 价格列表集合
     */
    List<TcmPriceList> selectTcmPriceListList(TcmPriceList priceList);

    /**
     * 查询价格列表
     *
     * @param id 价格列表主键
     * @return 价格列表
     */
    TcmPriceList selectTcmPriceListById(String id);

    /**
     * 新增价格列表
     *
     * @param priceList 价格列表
     * @return 结果
     */
    int insertTcmPriceList(TcmPriceList priceList);

    /**
     * 修改价格列表
     *
     * @param priceList 价格列表
     * @return 结果
     */
    int updateTcmPriceList(TcmPriceList priceList);

    /**
     * 删除价格列表
     *
     * @param id 价格列表主键
     * @return 结果
     */
    int deleteTcmPriceListById(String id);
}
