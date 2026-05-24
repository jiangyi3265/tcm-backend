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

    /**
     * 获取 Stripe POS 配置（敏感字段只返回遮罩状态）
     *
     * @return Stripe 配置
     */
    Map<String, Object> getStripeSettings();

    /**
     * 更新 Stripe POS 配置
     *
     * @param data Stripe 配置数据
     * @return 更新后的 Stripe 配置（敏感字段只返回遮罩状态）
     */
    Map<String, Object> updateStripeSettings(Map<String, Object> data);

    String getStripePublishableKey();

    String getStripeSecretKey();

    String getStripeWebhookSecret();

    String getStripeTerminalReaderId();
}
