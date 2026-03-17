package com.ruoyi.hospital.service;

import java.util.List;
import com.ruoyi.hospital.domain.TcmBranch;

/**
 * 分院管理 Service接口
 *
 * @author ruoyi
 */
public interface ITcmBranchService
{
    /**
     * 查询分院列表
     *
     * @param branch 分院查询条件
     * @return 分院集合
     */
    List<TcmBranch> selectTcmBranchList(TcmBranch branch);

    /**
     * 查询分院详情
     *
     * @param id 分院ID
     * @return 分院信息
     */
    TcmBranch selectTcmBranchById(String id);

    /**
     * 新增分院
     *
     * @param branch 分院信息
     * @return 影响行数
     */
    int insertTcmBranch(TcmBranch branch);

    /**
     * 修改分院
     *
     * @param branch 分院信息
     * @return 影响行数
     */
    int updateTcmBranch(TcmBranch branch);

    /**
     * 切换分院启用/停用状态
     *
     * @param id 分院ID
     * @return 切换后的分院对象
     */
    TcmBranch toggleBranch(String id);

    /**
     * 软删除分院
     *
     * @param id 分院ID
     * @return 软删除后的分院对象
     */
    TcmBranch softDeleteBranch(String id);
}
