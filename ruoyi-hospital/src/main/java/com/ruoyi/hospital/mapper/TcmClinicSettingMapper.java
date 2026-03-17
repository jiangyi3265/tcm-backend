package com.ruoyi.hospital.mapper;

import java.util.List;
import com.ruoyi.hospital.domain.TcmClinicSetting;

/**
 * 诊所设置Mapper接口
 *
 * @author ruoyi
 */
public interface TcmClinicSettingMapper
{
    /**
     * 查询所有设置
     *
     * @return 设置集合
     */
    List<TcmClinicSetting> selectAllSettings();

    /**
     * 根据设置键查询设置
     *
     * @param settingKey 设置键
     * @return 设置
     */
    TcmClinicSetting selectSettingByKey(String settingKey);

    /**
     * 新增设置
     *
     * @param setting 设置
     * @return 结果
     */
    int insertSetting(TcmClinicSetting setting);

    /**
     * 修改设置
     *
     * @param setting 设置
     * @return 结果
     */
    int updateSetting(TcmClinicSetting setting);
}
