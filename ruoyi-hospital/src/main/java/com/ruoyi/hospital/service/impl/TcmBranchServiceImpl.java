package com.ruoyi.hospital.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.hospital.domain.TcmBranch;
import com.ruoyi.hospital.mapper.TcmBranchMapper;
import com.ruoyi.hospital.service.ITcmBranchService;

/**
 * 分院管理 Service业务层处理
 *
 * @author ruoyi
 */
@Service
public class TcmBranchServiceImpl implements ITcmBranchService
{
    @Autowired
    private TcmBranchMapper tcmBranchMapper;

    /**
     * 查询分院列表
     *
     * @param branch 分院查询条件
     * @return 分院集合
     */
    @Override
    public List<TcmBranch> selectTcmBranchList(TcmBranch branch)
    {
        return tcmBranchMapper.selectTcmBranchList(branch);
    }

    /**
     * 查询分院详情
     *
     * @param id 分院ID
     * @return 分院信息
     */
    @Override
    public TcmBranch selectTcmBranchById(String id)
    {
        return tcmBranchMapper.selectTcmBranchById(id);
    }

    /**
     * 新增分院
     *
     * @param branch 分院信息
     * @return 影响行数
     */
    @Override
    public int insertTcmBranch(TcmBranch branch)
    {
        if (branch.getId() == null || branch.getId().isEmpty())
        {
            branch.setId(java.util.UUID.randomUUID().toString());
        }
        branch.setCreateTime(DateUtils.getNowDate());
        return tcmBranchMapper.insertTcmBranch(branch);
    }

    /**
     * 修改分院
     *
     * @param branch 分院信息
     * @return 影响行数
     */
    @Override
    public int updateTcmBranch(TcmBranch branch)
    {
        return tcmBranchMapper.updateTcmBranch(branch);
    }

    /**
     * 切换分院启用/停用状态
     *
     * @param id 分院ID
     * @return 切换后的分院对象
     */
    @Override
    public TcmBranch toggleBranch(String id)
    {
        TcmBranch branch = tcmBranchMapper.selectTcmBranchById(id);
        if (branch == null)
        {
            throw new ServiceException("分院记录不存在");
        }
        Integer currentActive = branch.getIsActive();
        branch.setIsActive(currentActive != null && currentActive == 1 ? 0 : 1);
        tcmBranchMapper.updateTcmBranch(branch);
        return branch;
    }

    /**
     * 软删除分院（设置isActive为0）
     *
     * @param id 分院ID
     * @return 软删除后的分院对象
     */
    @Override
    public TcmBranch softDeleteBranch(String id)
    {
        TcmBranch branch = tcmBranchMapper.selectTcmBranchById(id);
        if (branch == null)
        {
            throw new ServiceException("分院记录不存在");
        }
        branch.setIsActive(0);
        tcmBranchMapper.updateTcmBranch(branch);
        return branch;
    }
}
