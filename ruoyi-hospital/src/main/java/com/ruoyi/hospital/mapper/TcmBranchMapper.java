package com.ruoyi.hospital.mapper;

import java.util.List;
import com.ruoyi.hospital.domain.TcmBranch;

/**
 * 分院Mapper接口
 *
 * @author ruoyi
 */
public interface TcmBranchMapper
{
    /**
     * 查询分院列表
     *
     * @param branch 分院
     * @return 分院集合
     */
    List<TcmBranch> selectTcmBranchList(TcmBranch branch);

    /**
     * 查询分院
     *
     * @param id 分院主键
     * @return 分院
     */
    TcmBranch selectTcmBranchById(String id);

    /**
     * 新增分院
     *
     * @param branch 分院
     * @return 结果
     */
    int insertTcmBranch(TcmBranch branch);

    /**
     * 修改分院
     *
     * @param branch 分院
     * @return 结果
     */
    int updateTcmBranch(TcmBranch branch);

    /**
     * 删除分院
     *
     * @param id 分院主键
     * @return 结果
     */
    int deleteTcmBranchById(String id);
}
