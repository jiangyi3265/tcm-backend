package com.ruoyi.hospital.service;

import java.util.Map;

/**
 * 诊所设置 Service接口
 *
 * @author ruoyi
 */
public interface ITcmSettingsService
{
    /**
     * 获取所有设置项的捆绑包
     *
     * @return 设置项集合
     */
    Map<String, Object> getBundle();

    /**
     * 更新基础设置
     *
     * @param data 设置数据
     * @return 更新后的设置数据
     */
    Map<String, Object> updateBaseSettings(Map<String, Object> data);
}
