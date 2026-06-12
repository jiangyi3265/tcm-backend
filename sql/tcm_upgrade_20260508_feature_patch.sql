-- ============================================================
-- OTCM 2026-05-08 功能补丁升级脚本
-- 适用场景：已有数据库升级到本轮功能修改后的版本。
-- 特点：只做幂等补齐，不删除数据，不重建业务表。
-- ============================================================

-- 1. 用户 Profile 目前存放在 sys_user.remark。
--    RuoYi 默认 remark 通常是 varchar(500)，组织信息、工作时间、服务范围等 JSON 容易超过长度。
ALTER TABLE sys_user MODIFY COLUMN remark longtext DEFAULT NULL COMMENT '备注/用户扩展资料(JSON)';

-- 2. 病人主治医师字段：首次预约自动绑定、主治医师权限判断依赖此字段。
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tcm_patient' AND COLUMN_NAME = 'practitioner_id'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE tcm_patient ADD COLUMN practitioner_id varchar(64) DEFAULT NULL COMMENT ''主治医师ID'' AFTER phone',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Herb 字典 Latin Name 字段（幂等）
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tcm_herb_dict' AND COLUMN_NAME = 'latin_name'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE tcm_herb_dict ADD COLUMN latin_name varchar(255) DEFAULT NULL COMMENT ''Latin Name'' AFTER pinyin',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tcm_patient' AND INDEX_NAME = 'idx_patient_practitioner'
);
SET @sql = IF(@idx_exists = 0,
  'ALTER TABLE tcm_patient ADD KEY idx_patient_practitioner (practitioner_id)',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 患者公开链接：同意书 token。
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tcm_patient' AND COLUMN_NAME = 'consent_token'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE tcm_patient ADD COLUMN consent_token varchar(64) DEFAULT NULL COMMENT ''同意书签署令牌''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tcm_patient' AND COLUMN_NAME = 'consent_token_expires'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE tcm_patient ADD COLUMN consent_token_expires datetime DEFAULT NULL COMMENT ''令牌过期时间''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tcm_patient' AND INDEX_NAME = 'idx_patient_consent_token'
);
SET @sql = IF(@idx_exists = 0,
  'ALTER TABLE tcm_patient ADD KEY idx_patient_consent_token (consent_token)',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. Initial Intake 公开问诊链接字段。
SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tcm_appointment' AND COLUMN_NAME = 'intake_token'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE tcm_appointment ADD COLUMN intake_token varchar(64) DEFAULT NULL COMMENT ''问诊表单令牌''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tcm_appointment' AND COLUMN_NAME = 'intake_submitted'
);
SET @sql = IF(@col_exists = 0,
  'ALTER TABLE tcm_appointment ADD COLUMN intake_submitted tinyint(1) DEFAULT 0 COMMENT ''表单是否已提交''',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tcm_appointment' AND INDEX_NAME = 'idx_appt_intake_token'
);
SET @sql = IF(@idx_exists = 0,
  'ALTER TABLE tcm_appointment ADD KEY idx_appt_intake_token (intake_token)',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5. Documents / 自动生成 PDF / 上传文件需要附件表。
CREATE TABLE IF NOT EXISTS tcm_patient_file (
  id              bigint(20)    NOT NULL AUTO_INCREMENT   COMMENT '附件ID',
  patient_id      varchar(64)   DEFAULT NULL              COMMENT '病人ID',
  consultation_id varchar(64)   DEFAULT NULL              COMMENT '诊疗记录ID',
  file_type       varchar(30)   DEFAULT NULL              COMMENT '文件类型',
  file_name       varchar(200)  DEFAULT NULL              COMMENT '文件名',
  file_path       varchar(500)  DEFAULT NULL              COMMENT '文件路径',
  upload_time     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (id),
  KEY idx_file_patient (patient_id),
  KEY idx_file_consultation (consultation_id)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='病人附件表';

-- 6. 诊所设置表保存 Settings、成药列表、邮件模板等 JSON 配置。
CREATE TABLE IF NOT EXISTS tcm_clinic_setting (
  setting_key   varchar(64)  NOT NULL              COMMENT '配置键',
  setting_value longtext     DEFAULT NULL           COMMENT '配置值(JSON)',
  update_time   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='诊所配置表';

-- 7. 邮件模板和成药列表用配置表存储。
--    后端会在读取时补齐默认模板，这里只保证 key 存在，避免老库没有对应配置。
INSERT IGNORE INTO tcm_clinic_setting (setting_key, setting_value)
VALUES ('emailTemplates', '{}');

INSERT IGNORE INTO tcm_clinic_setting (setting_key, setting_value)
VALUES ('patentMedicines', '[]');

-- 8. Stripe 付款记录不新增表，写入 tcm_consultation.payload.paymentRecords。
--    Stripe secret-key / webhook-secret 仍使用 application.yml 或环境变量配置。
