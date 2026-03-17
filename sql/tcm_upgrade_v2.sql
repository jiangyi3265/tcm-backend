-- =============================================
-- TCM v2 升级脚本 - 补全3项增强功能
-- 1. 同意书邮件链接签署
-- 2. 病人在线问诊表单
-- 3. 文件夹层级展示（前端实现，无需数据库变更）
-- =============================================

-- 1. 同意书令牌字段
ALTER TABLE tcm_patient ADD COLUMN consent_token varchar(64) DEFAULT NULL COMMENT '同意书签署令牌';
ALTER TABLE tcm_patient ADD COLUMN consent_token_expires datetime DEFAULT NULL COMMENT '令牌过期时间';
ALTER TABLE tcm_patient ADD KEY idx_patient_consent_token (consent_token);

-- 2. 问诊表单令牌字段
ALTER TABLE tcm_appointment ADD COLUMN intake_token varchar(64) DEFAULT NULL COMMENT '问诊表单令牌';
ALTER TABLE tcm_appointment ADD COLUMN intake_submitted tinyint(1) DEFAULT 0 COMMENT '表单是否已提交';
ALTER TABLE tcm_appointment ADD KEY idx_appt_intake_token (intake_token);
