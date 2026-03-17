-- =============================================
-- TCM 中医诊所管理系统 - 数据库初始化脚本
-- 在执行 ry_20250522.sql 之后执行此脚本
-- =============================================

-- ----------------------------
-- 1. 扩展 sys_user 表（幂等，重复执行不报错）
-- ----------------------------
ALTER TABLE sys_user MODIFY COLUMN user_name varchar(50) NOT NULL COMMENT '用户账号';

-- branch_ids
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'branch_ids');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE sys_user ADD COLUMN branch_ids varchar(500) DEFAULT NULL COMMENT ''关联分店ID(JSON数组)''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- assigned_to
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'assigned_to');
SET @sql = IF(@col_exists = 0, 'ALTER TABLE sys_user ADD COLUMN assigned_to bigint(20) DEFAULT NULL COMMENT ''学徒分配给的医师ID''', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------
-- 2. 创建业务表
-- ----------------------------

DROP TABLE IF EXISTS tcm_patient;
CREATE TABLE tcm_patient (
  id              varchar(64)   NOT NULL                  COMMENT '病人ID',
  name            varchar(100)  DEFAULT ''                COMMENT '姓名',
  first_name      varchar(50)   DEFAULT ''                COMMENT '名',
  last_name       varchar(50)   DEFAULT ''                COMMENT '姓',
  email           varchar(100)  DEFAULT ''                COMMENT '邮箱',
  phone           varchar(30)   DEFAULT ''                COMMENT '电话',
  practitioner_id varchar(64)   DEFAULT NULL              COMMENT '主治医师ID',
  is_active       tinyint(1)    DEFAULT 1                 COMMENT '是否有效',
  consent_signed  tinyint(1)    DEFAULT 0                 COMMENT '是否签署同意书',
  consent_signed_at datetime    DEFAULT NULL              COMMENT '同意书签署时间',
  merged_into     varchar(64)   DEFAULT NULL              COMMENT '合并到的病人ID',
  deleted_at      datetime      DEFAULT NULL              COMMENT '软删除时间',
  payload         longtext      DEFAULT NULL              COMMENT '完整病人信息(JSON)',
  create_time     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_patient_practitioner (practitioner_id),
  KEY idx_patient_deleted (deleted_at),
  KEY idx_patient_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='病人档案表';

DROP TABLE IF EXISTS tcm_appointment;
CREATE TABLE tcm_appointment (
  id              varchar(64)   NOT NULL                  COMMENT '预约ID',
  patient_id      varchar(64)   DEFAULT NULL              COMMENT '病人ID',
  practitioner_id varchar(64)   DEFAULT NULL              COMMENT '医师ID',
  room_id         varchar(64)   DEFAULT NULL              COMMENT '诊室ID',
  service_type    varchar(64)   DEFAULT NULL              COMMENT '服务类型',
  start_time      datetime      DEFAULT NULL              COMMENT '开始时间',
  end_time        datetime      DEFAULT NULL              COMMENT '结束时间',
  status          varchar(20)   DEFAULT 'booked'          COMMENT '状态',
  branch_id       varchar(64)   DEFAULT NULL              COMMENT '分店ID',
  payload         longtext      DEFAULT NULL              COMMENT '附加数据(JSON)',
  create_time     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_appt_start (start_time),
  KEY idx_appt_practitioner (practitioner_id),
  KEY idx_appt_patient (patient_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约表';

DROP TABLE IF EXISTS tcm_consultation;
CREATE TABLE tcm_consultation (
  id              varchar(64)   NOT NULL                  COMMENT '记录ID',
  consultation_id varchar(64)   DEFAULT NULL              COMMENT '诊疗编号',
  patient_id      varchar(64)   DEFAULT NULL              COMMENT '病人ID',
  practitioner_id varchar(64)   DEFAULT NULL              COMMENT '医师ID',
  consult_date    date          DEFAULT NULL              COMMENT '就诊日期',
  status          varchar(20)   DEFAULT 'draft'           COMMENT '状态',
  branch_id       varchar(64)   DEFAULT NULL              COMMENT '分店ID',
  locked_at       datetime      DEFAULT NULL              COMMENT '锁定时间',
  version         int(11)       DEFAULT 1                 COMMENT '版本号',
  deleted_at      datetime      DEFAULT NULL              COMMENT '软删除时间',
  payload         longtext      DEFAULT NULL              COMMENT '完整诊疗数据(JSON)',
  create_time     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_consultation_id (consultation_id),
  KEY idx_consult_patient (patient_id),
  KEY idx_consult_deleted (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='诊疗记录表';

DROP TABLE IF EXISTS tcm_consultation_mod;
CREATE TABLE tcm_consultation_mod (
  id              bigint(20)    NOT NULL AUTO_INCREMENT   COMMENT '主键',
  consultation_id varchar(64)   DEFAULT NULL              COMMENT '诊疗记录ID',
  mod_date        datetime      DEFAULT NULL              COMMENT '修改时间',
  mod_type        varchar(30)   DEFAULT NULL              COMMENT '修改类型',
  action          varchar(200)  DEFAULT NULL              COMMENT '操作描述',
  user_id         varchar(64)   DEFAULT NULL              COMMENT '操作用户ID',
  version         int(11)       DEFAULT NULL              COMMENT '版本号',
  changes         longtext      DEFAULT NULL              COMMENT '变更内容(JSON)',
  create_time     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_mod_consultation (consultation_id)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='诊疗修改审计表';

DROP TABLE IF EXISTS tcm_inventory_item;
CREATE TABLE tcm_inventory_item (
  id              varchar(64)    NOT NULL                  COMMENT '库存ID',
  name            varchar(100)   NOT NULL                  COMMENT '药品名称',
  category        varchar(30)    DEFAULT NULL              COMMENT '分类',
  unit            varchar(20)    DEFAULT NULL              COMMENT '单位',
  quantity        decimal(12,2)  DEFAULT 0                 COMMENT '库存数量',
  price_per_unit  decimal(10,4)  DEFAULT 0                 COMMENT '单价',
  min_stock_level decimal(12,2)  DEFAULT 0                 COMMENT '最低库存',
  supplier        varchar(100)   DEFAULT NULL              COMMENT '供应商',
  grams_per_packet decimal(8,2)  DEFAULT NULL              COMMENT '每包克数',
  branch_id       varchar(64)    DEFAULT NULL              COMMENT '分店ID',
  is_active       tinyint(1)     DEFAULT 1                 COMMENT '是否有效',
  deleted_at      datetime       DEFAULT NULL              COMMENT '软删除时间',
  create_time     datetime       DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     datetime       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_inv_category (category),
  KEY idx_inv_deleted (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='药品库存表';

DROP TABLE IF EXISTS tcm_branch;
CREATE TABLE tcm_branch (
  id          varchar(64)   NOT NULL                  COMMENT '分店ID',
  name        varchar(100)  NOT NULL                  COMMENT '分店名称',
  code        varchar(30)   DEFAULT NULL              COMMENT '分店编码',
  address     varchar(300)  DEFAULT NULL              COMMENT '地址',
  phone       varchar(30)   DEFAULT NULL              COMMENT '电话',
  email       varchar(100)  DEFAULT NULL              COMMENT '邮箱',
  manager_id  varchar(64)   DEFAULT NULL              COMMENT '店长ID',
  is_active   tinyint(1)    DEFAULT 1                 COMMENT '是否启用',
  is_main     tinyint(1)    DEFAULT 0                 COMMENT '是否总店',
  room_ids    varchar(500)  DEFAULT NULL              COMMENT '诊室ID列表(JSON)',
  create_time datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分店表';

DROP TABLE IF EXISTS tcm_room;
CREATE TABLE tcm_room (
  id          varchar(64)   NOT NULL                  COMMENT '诊室ID',
  name        varchar(100)  NOT NULL                  COMMENT '诊室名称',
  branch_id   varchar(64)   DEFAULT NULL              COMMENT '所属分店ID',
  is_active   tinyint(1)    DEFAULT 1                 COMMENT '是否启用',
  create_time datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='诊室表';

DROP TABLE IF EXISTS tcm_service_type;
CREATE TABLE tcm_service_type (
  service_key       varchar(64)   NOT NULL              COMMENT '服务键名',
  label             varchar(100)  DEFAULT NULL           COMMENT '显示名称',
  duration          int(11)       DEFAULT NULL           COMMENT '总时长(分钟)',
  practitioner_time int(11)       DEFAULT NULL           COMMENT '医师用时(分钟)',
  room_required     tinyint(1)    DEFAULT 1              COMMENT '是否需要诊室',
  default_price     decimal(10,2) DEFAULT NULL           COMMENT '默认价格',
  update_time       datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (service_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='服务类型表';

DROP TABLE IF EXISTS tcm_clinic_setting;
CREATE TABLE tcm_clinic_setting (
  setting_key   varchar(64)  NOT NULL              COMMENT '配置键',
  setting_value longtext     DEFAULT NULL           COMMENT '配置值(JSON)',
  update_time   datetime     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='诊所配置表';

DROP TABLE IF EXISTS tcm_price_list;
CREATE TABLE tcm_price_list (
  id            varchar(64)   NOT NULL                  COMMENT '价目表ID',
  name          varchar(100)  DEFAULT NULL              COMMENT '名称',
  effective_date date         DEFAULT NULL              COMMENT '生效日期',
  is_active     tinyint(1)    DEFAULT 1                 COMMENT '是否有效',
  items_json    longtext      DEFAULT NULL              COMMENT '项目明细(JSON)',
  create_time   datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='价目表';

DROP TABLE IF EXISTS tcm_email_log;
CREATE TABLE tcm_email_log (
  id          bigint(20)    NOT NULL AUTO_INCREMENT   COMMENT '日志ID',
  to_email    varchar(200)  DEFAULT NULL              COMMENT '收件人',
  subject     varchar(300)  DEFAULT NULL              COMMENT '主题',
  email_type  varchar(50)   DEFAULT NULL              COMMENT '邮件类型',
  body        longtext      DEFAULT NULL              COMMENT '邮件内容',
  sent_at     datetime      DEFAULT NULL              COMMENT '发送时间',
  payload     longtext      DEFAULT NULL              COMMENT '附加数据(JSON)',
  create_time datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='邮件日志表';

DROP TABLE IF EXISTS tcm_patient_file;
CREATE TABLE tcm_patient_file (
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

-- ----------------------------
-- 3. 插入角色（IGNORE 避免重复执行报错）
-- ----------------------------
INSERT IGNORE INTO sys_role VALUES (100, '中医师', 'practitioner', 5, '1', 1, 1, '0', '0', 'admin', sysdate(), '', NULL, '中医师角色');
INSERT IGNORE INTO sys_role VALUES (101, '学徒',   'apprentice',   6, '1', 1, 1, '0', '0', 'admin', sysdate(), '', NULL, '学徒角色');
INSERT IGNORE INTO sys_role VALUES (102, '药师',   'pharmacist',   7, '1', 1, 1, '0', '0', 'admin', sysdate(), '', NULL, '药师角色');
INSERT IGNORE INTO sys_role VALUES (103, '收银',   'cashier',      8, '1', 1, 1, '0', '0', 'admin', sysdate(), '', NULL, '收银角色');

-- ----------------------------
-- 4. 插入菜单（IGNORE 避免重复执行报错）
-- ----------------------------
INSERT IGNORE INTO sys_menu VALUES (2000, '中医诊所', 0, 6, 'tcm', NULL, '', '', 1, 0, 'M', '0', '0', '', 'example', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2001, '病人管理', 2000, 1, 'patient',      'tcm/patient/index',      '', '', 1, 0, 'C', '0', '0', 'tcm:patient:list',      'peoples',  'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2002, '预约管理', 2000, 2, 'appointment',  'tcm/appointment/index',  '', '', 1, 0, 'C', '0', '0', 'tcm:appointment:list',  'date',     'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2003, '诊疗管理', 2000, 3, 'consultation', 'tcm/consultation/index', '', '', 1, 0, 'C', '0', '0', 'tcm:consultation:list', 'edit',     'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2004, '库存管理', 2000, 4, 'inventory',    'tcm/inventory/index',    '', '', 1, 0, 'C', '0', '0', 'tcm:inventory:list',    'shopping', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2005, '分店管理', 2000, 5, 'branch',       'tcm/branch/index',       '', '', 1, 0, 'C', '0', '0', 'tcm:branch:list',       'tree',     'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2006, '诊所设置', 2000, 6, 'settings',     'tcm/settings/index',     '', '', 1, 0, 'C', '0', '0', 'tcm:settings:query',    'system',   'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2007, '邮件日志', 2000, 7, 'email',        'tcm/email/index',        '', '', 1, 0, 'C', '0', '0', 'tcm:email:list',        'email',    'admin', sysdate(), '', NULL, '');

-- 按钮权限 - 病人
INSERT IGNORE INTO sys_menu VALUES (2101, '病人查询',  2001, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:patient:query',   '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2102, '病人新增',  2001, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:patient:add',     '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2103, '病人修改',  2001, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:patient:edit',    '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2104, '病人删除',  2001, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:patient:remove',  '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2105, '病人合并',  2001, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:patient:merge',   '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2106, '同意书签署',2001, 6, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:patient:consent', '#', 'admin', sysdate(), '', NULL, '');

-- 按钮权限 - 预约
INSERT IGNORE INTO sys_menu VALUES (2201, '预约查询', 2002, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:appointment:query',     '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2202, '预约新增', 2002, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:appointment:add',       '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2203, '预约修改', 2002, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:appointment:edit',      '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2204, '预约状态', 2002, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:appointment:status',    '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2205, '时段检查', 2002, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:appointment:checkslot', '#', 'admin', sysdate(), '', NULL, '');

-- 按钮权限 - 诊疗
INSERT IGNORE INTO sys_menu VALUES (2301, '诊疗查询', 2003, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:consultation:query',    '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2302, '诊疗新增', 2003, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:consultation:add',      '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2303, '诊疗修改', 2003, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:consultation:edit',     '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2304, '诊疗删除', 2003, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:consultation:remove',   '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2305, '标记完成', 2003, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:consultation:complete', '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2306, '标记付费', 2003, 6, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:consultation:paid',     '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2307, '标记配药', 2003, 7, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:consultation:dispense', '#', 'admin', sysdate(), '', NULL, '');

-- 按钮权限 - 库存
INSERT IGNORE INTO sys_menu VALUES (2401, '库存查询', 2004, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:inventory:query',   '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2402, '库存新增', 2004, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:inventory:add',     '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2403, '库存修改', 2004, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:inventory:edit',    '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2404, '库存删除', 2004, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:inventory:remove',  '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2405, '库存调整', 2004, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:inventory:adjust',  '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2406, '处方扣减', 2004, 6, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:inventory:deduct',  '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2407, '处方恢复', 2004, 7, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:inventory:restore', '#', 'admin', sysdate(), '', NULL, '');

-- 按钮权限 - 分店
INSERT IGNORE INTO sys_menu VALUES (2501, '分店查询', 2005, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:branch:query',  '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2502, '分店新增', 2005, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:branch:add',    '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2503, '分店修改', 2005, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:branch:edit',   '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2504, '分店切换', 2005, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:branch:toggle', '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2505, '分店删除', 2005, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:branch:remove', '#', 'admin', sysdate(), '', NULL, '');

INSERT IGNORE INTO sys_menu VALUES (2601, '设置修改', 2006, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:settings:edit', '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2701, '邮件新增', 2007, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:email:add',     '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2801, '文件上传', 2000, 8, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:file:upload',   '#', 'admin', sysdate(), '', NULL, '');
INSERT IGNORE INTO sys_menu VALUES (2802, '文件下载', 2000, 9, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:file:download', '#', 'admin', sysdate(), '', NULL, '');

-- ----------------------------
-- 5. 角色-菜单关联
-- ----------------------------
-- admin (1)
INSERT IGNORE INTO sys_role_menu VALUES (1,2000),(1,2001),(1,2002),(1,2003),(1,2004),(1,2005),(1,2006),(1,2007);
INSERT IGNORE INTO sys_role_menu VALUES (1,2101),(1,2102),(1,2103),(1,2104),(1,2105),(1,2106);
INSERT IGNORE INTO sys_role_menu VALUES (1,2201),(1,2202),(1,2203),(1,2204),(1,2205);
INSERT IGNORE INTO sys_role_menu VALUES (1,2301),(1,2302),(1,2303),(1,2304),(1,2305),(1,2306),(1,2307);
INSERT IGNORE INTO sys_role_menu VALUES (1,2401),(1,2402),(1,2403),(1,2404),(1,2405),(1,2406),(1,2407);
INSERT IGNORE INTO sys_role_menu VALUES (1,2501),(1,2502),(1,2503),(1,2504),(1,2505);
INSERT IGNORE INTO sys_role_menu VALUES (1,2601),(1,2701),(1,2801),(1,2802);

-- practitioner (100)
INSERT IGNORE INTO sys_role_menu VALUES (100,2000),(100,2001),(100,2002),(100,2003),(100,2004),(100,2006);
INSERT IGNORE INTO sys_role_menu VALUES (100,2101),(100,2102),(100,2103),(100,2104),(100,2105),(100,2106);
INSERT IGNORE INTO sys_role_menu VALUES (100,2201),(100,2202),(100,2203),(100,2204),(100,2205);
INSERT IGNORE INTO sys_role_menu VALUES (100,2301),(100,2302),(100,2303),(100,2304),(100,2305),(100,2306),(100,2307);
INSERT IGNORE INTO sys_role_menu VALUES (100,2401),(100,2801),(100,2802);

-- apprentice (101)
INSERT IGNORE INTO sys_role_menu VALUES (101,2000),(101,2001),(101,2002),(101,2003);
INSERT IGNORE INTO sys_role_menu VALUES (101,2101),(101,2102),(101,2103),(101,2106);
INSERT IGNORE INTO sys_role_menu VALUES (101,2201),(101,2202),(101,2203),(101,2205);
INSERT IGNORE INTO sys_role_menu VALUES (101,2301);

-- pharmacist (102)
INSERT IGNORE INTO sys_role_menu VALUES (102,2000),(102,2003),(102,2004);
INSERT IGNORE INTO sys_role_menu VALUES (102,2301),(102,2307);
INSERT IGNORE INTO sys_role_menu VALUES (102,2401),(102,2402),(102,2403),(102,2404),(102,2405),(102,2406),(102,2407);

-- cashier (103)
INSERT IGNORE INTO sys_role_menu VALUES (103,2000),(103,2003);
INSERT IGNORE INTO sys_role_menu VALUES (103,2301),(103,2306);

-- ----------------------------
-- 6. 演示用户 (user_name = email)
-- ----------------------------
INSERT IGNORE INTO sys_user (user_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar, password, status, del_flag, login_ip, login_date, pwd_update_date, create_by, create_time, update_by, update_time, remark, branch_ids, assigned_to)
VALUES (100, 100, 'admin@clinic.com',      '张管理', '00', 'admin@clinic.com',      '13800000001', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', NULL, 'TCM管理员', '["branch-main"]', NULL);
INSERT IGNORE INTO sys_user (user_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar, password, status, del_flag, login_ip, login_date, pwd_update_date, create_by, create_time, update_by, update_time, remark, branch_ids, assigned_to)
VALUES (101, 100, 'doctor@clinic.com',     '李医师', '00', 'doctor@clinic.com',     '13800000002', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', NULL, 'TCM中医师', '["branch-main"]', NULL);
INSERT IGNORE INTO sys_user (user_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar, password, status, del_flag, login_ip, login_date, pwd_update_date, create_by, create_time, update_by, update_time, remark, branch_ids, assigned_to)
VALUES (102, 100, 'doctor2@clinic.com',    '王医师', '00', 'doctor2@clinic.com',    '13800000005', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', NULL, 'TCM中医师', '["branch-main"]', NULL);
INSERT IGNORE INTO sys_user (user_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar, password, status, del_flag, login_ip, login_date, pwd_update_date, create_by, create_time, update_by, update_time, remark, branch_ids, assigned_to)
VALUES (103, 100, 'apprentice@clinic.com', '陈学徒', '00', 'apprentice@clinic.com', '13800000003', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', NULL, 'TCM学徒',   '["branch-main"]', 101);
INSERT IGNORE INTO sys_user (user_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar, password, status, del_flag, login_ip, login_date, pwd_update_date, create_by, create_time, update_by, update_time, remark, branch_ids, assigned_to)
VALUES (104, 100, 'pharmacist@clinic.com', '刘药师', '00', 'pharmacist@clinic.com', '13800000004', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', NULL, 'TCM药师',   '["branch-main"]', NULL);
INSERT IGNORE INTO sys_user (user_id, dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar, password, status, del_flag, login_ip, login_date, pwd_update_date, create_by, create_time, update_by, update_time, remark, branch_ids, assigned_to)
VALUES (105, 100, 'cashier@clinic.com',    '赵收银', '00', 'cashier@clinic.com',    '13800000006', '0', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', NULL, 'TCM收银',   '["branch-main"]', NULL);

INSERT IGNORE INTO sys_user_role VALUES (100, 1);
INSERT IGNORE INTO sys_user_role VALUES (101, 100);
INSERT IGNORE INTO sys_user_role VALUES (102, 100);
INSERT IGNORE INTO sys_user_role VALUES (103, 101);
INSERT IGNORE INTO sys_user_role VALUES (104, 102);
INSERT IGNORE INTO sys_user_role VALUES (105, 103);

-- 确保所有演示用户密码统一为 admin123（防止 INSERT IGNORE 跳过后密码不一致）
UPDATE sys_user SET password = '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2'
WHERE user_id IN (100, 101, 102, 103, 104, 105);

-- ----------------------------
-- 7. 种子业务数据
-- ----------------------------

-- 分店
INSERT IGNORE INTO tcm_branch VALUES ('branch-main',  '总店',     'MAIN',  '北京市朝阳区建国路88号',     '010-88886666', 'main@clinic.com',  '100', 1, 1, '["room-1","room-2","room-3"]', sysdate(), sysdate());
INSERT IGNORE INTO tcm_branch VALUES ('branch-east',  '东城分店', 'EAST',  '北京市东城区东直门大街12号',   '010-88886667', 'east@clinic.com',  NULL,  1, 0, '[]', sysdate(), sysdate());
INSERT IGNORE INTO tcm_branch VALUES ('branch-south', '南城分店', 'SOUTH', '北京市丰台区南三环路56号',     '010-88886668', 'south@clinic.com', NULL,  1, 0, '[]', sysdate(), sysdate());

-- 诊室
INSERT IGNORE INTO tcm_room VALUES ('room-1', '诊疗室一号', 'branch-main', 1, sysdate(), sysdate());
INSERT IGNORE INTO tcm_room VALUES ('room-2', '诊疗室二号', 'branch-main', 1, sysdate(), sysdate());
INSERT IGNORE INTO tcm_room VALUES ('room-3', '诊疗室三号', 'branch-main', 1, sysdate(), sysdate());

-- 服务类型
INSERT IGNORE INTO tcm_service_type VALUES ('acupuncture_new',      '针灸首诊', 60, 20, 1, 120.00, sysdate());
INSERT IGNORE INTO tcm_service_type VALUES ('acupuncture_followup', '针灸复诊', 50, 10, 1,  80.00, sysdate());
INSERT IGNORE INTO tcm_service_type VALUES ('herbs_only',           '仅中药',   20, 20, 0,  60.00, sysdate());

-- 诊所配置
INSERT IGNORE INTO tcm_clinic_setting VALUES ('taxRate',              '0.13',                         sysdate());
INSERT IGNORE INTO tcm_clinic_setting VALUES ('practitionerInterval', '20',                           sysdate());
INSERT IGNORE INTO tcm_clinic_setting VALUES ('profitRatio',          '1.0',                          sysdate());
INSERT IGNORE INTO tcm_clinic_setting VALUES ('clinicName',           '中医养生堂',                   sysdate());
INSERT IGNORE INTO tcm_clinic_setting VALUES ('clinicAddress',        '北京市朝阳区建国路88号',       sysdate());
INSERT IGNORE INTO tcm_clinic_setting VALUES ('clinicPhone',          '010-88886666',                  sysdate());

-- 病人
INSERT IGNORE INTO tcm_patient (id, name, first_name, last_name, email, phone, practitioner_id, is_active, consent_signed, consent_signed_at, payload, create_time)
VALUES ('patient-1', '张三', '三', '张', 'zhangsan@example.com', '13900001111', '101', 1, 1, '2024-02-01 08:30:00',
'{"firstName":"三","lastName":"张","middleName":"","jobTitle":"","accountName":"","emails":["zhangsan@example.com"],"email2":"","email3":"","phone":"13900001111","mobilePhone":"13900001111","businessPhone":"","fax":"","preferredContact":"Any","dateOfBirth":"1985-06-15","gender":"男","address":"北京市朝阳区","addressStreet":"朝阳路802号","addressCity":"北京","addressState":"北京市","addressPostal":"100020","diseaseName":"慢性疲劳综合征","historyAndMedication":"对花粉过敏，既往无重大手术史","notes":"对花粉过敏"}',
'2024-02-01 08:00:00');

INSERT IGNORE INTO tcm_patient (id, name, first_name, last_name, email, phone, practitioner_id, is_active, consent_signed, consent_signed_at, payload, create_time)
VALUES ('patient-2', '李四', '四', '李', 'lisi@example.com', '13900002222', '101', 1, 1, '2024-02-10 09:15:00',
'{"firstName":"四","lastName":"李","middleName":"","jobTitle":"","accountName":"","emails":["lisi@example.com","lisi_work@example.com"],"email2":"lisi_work@example.com","email3":"","phone":"13900002222","mobilePhone":"13900002222","businessPhone":"","fax":"","preferredContact":"Email","dateOfBirth":"1990-11-20","gender":"女","address":"北京市海淀区","addressStreet":"海淀路15号","addressCity":"北京","addressState":"北京市","addressPostal":"100080","diseaseName":"失眠症，月经不调","historyAndMedication":"无药物过敏史","notes":""}',
'2024-02-10 09:00:00');

INSERT IGNORE INTO tcm_patient (id, name, first_name, last_name, email, phone, practitioner_id, is_active, consent_signed, payload, create_time)
VALUES ('patient-3', '王五', '五', '王', 'wangwu@example.com', '13900003333', '102', 1, 0,
'{"firstName":"五","lastName":"王","middleName":"","jobTitle":"","accountName":"","emails":["wangwu@example.com"],"email2":"","email3":"","phone":"13900003333","mobilePhone":"13900003333","businessPhone":"01088881234","fax":"","preferredContact":"Phone","dateOfBirth":"1978-03-08","gender":"男","address":"北京市西城区","addressStreet":"西长安街1号","addressCity":"北京","addressState":"北京市","addressPostal":"100032","diseaseName":"高血压，颈椎病","historyAndMedication":"高血压病史10年，服用降压药","notes":"高血压病史"}',
'2024-03-01 10:00:00');

-- 诊疗记录
INSERT IGNORE INTO tcm_consultation (id, consultation_id, patient_id, practitioner_id, consult_date, status, branch_id, locked_at, version, payload, create_time)
VALUES ('consult-1', 'ORD-00001-A1B2C3', 'patient-1', '101', '2024-02-01', 'paid', 'branch-main', '2024-02-01 16:00:00', 1,
'{"chiefComplaint":"Fatigue 疲劳","chiefComplaintDuration":"1-4 weeks 一个月内","chiefComplaintDescription":"患者主诉头痛、失眠，持续两周，伴有疲劳乏力，工作压力较大。","progressOfDisease":"症状逐渐加重，夜间尤为明显。","summary":"患者主诉头痛、失眠，持续两周，伴有疲劳乏力。","differentiation":"肝郁气滞，心神不宁","treatment":"疏肝解郁，养心安神。针灸配合中药汤剂。","diff":{"coldHeat":"Neither 无","sweat":"Normal 正常","headDiscomfort":"头痛，两侧为主","eye":"","ear":"","nose":"","mouth":"","taste":"Bitter 口苦","bodyDiscomforts":"肩颈僵硬","skinIssues":"","otherExterior":"","chest":"Tightness 胸闷","heart":"Palpitation 心悸","hypochondriac":"Distension 胀","sleep":"Insomnia 失眠","anxietyStress":"工作压力大，容易烦躁","otherChest":"","appetite":"Poor appetit 食欲不振","thirst":"Dry mouth 口干","abdomen":"Bloating 腹胀","otherAbdomen":"","bowelMovement":"Normal 正常","urine":"Normal 正常","otherLowerAbdomen":"","periodCircle":"","periodDuration":"","bloodQuality":"","pms":"","otherFemale":"","pathologicalChannel":"肝经，心经","pathologicalChanges":"肝区压痛，太冲穴有明显酸胀感","pulse":"Wiry 弦","detailedPulse":"弦细，左关尤甚","tongueColor":"Red 红","tongueBody":"Normal 正常","tongueCoating":"Thin yellow 薄黄","otherTongue":"舌尖红","tongueImage":null,"conclusions":[{"name":"肝郁气滞","treatment":"疏肝理气"},{"name":"心神不宁","treatment":"养心安神"}]},"acupuncture":[{"point":"百会","side":"bilateral","notes":"留针20分钟"},{"point":"神门","side":"bilateral","notes":""},{"point":"太冲","side":"bilateral","notes":""},{"point":"合谷","side":"left","notes":""}],"prescriptions":[{"id":"rx-1","direction":"内服 Oral intake","whereToGet":"In-store 店内取药","quantity":7,"preferredUnit":"g","formulaName":"逍遥散加减","items":[{"name":"柴胡","dosage":10,"unit":"g","category":"1. 辛温解表药","guijing":"肝, 胆","nature":"3. 微寒","taste":"Acrid辛, Bitter苦","pricePerUnit":0.15},{"name":"白芍","dosage":15,"unit":"g","category":"40. 补血药","guijing":"肝, 脾","nature":"3. 微寒","taste":"Bitter苦, Sour酸","pricePerUnit":0.12},{"name":"当归","dosage":10,"unit":"g","category":"40. 补血药","guijing":"肝, 心, 脾","nature":"6. 温","taste":"Sweet甜, Acrid辛","pricePerUnit":0.20},{"name":"茯苓","dosage":15,"unit":"g","category":"15. 利水渗湿药","guijing":"脾, 心, 肺","nature":"4. 平","taste":"Sweet甜, Bland淡","pricePerUnit":0.09},{"name":"白术","dosage":10,"unit":"g","category":"38. 补气药","guijing":"脾, 胃","nature":"6. 温","taste":"Bitter苦, Sweet甜","pricePerUnit":0.10},{"name":"甘草","dosage":6,"unit":"g","category":"38. 补气药","guijing":"脾, 肺","nature":"4. 平","taste":"Sweet甜","pricePerUnit":0.06}],"subtotal":14.94,"dispensingCompleted":true}],"herbals":[{"name":"柴胡","dosage":10,"unit":"g"},{"name":"白芍","dosage":15,"unit":"g"},{"name":"当归","dosage":10,"unit":"g"},{"name":"茯苓","dosage":15,"unit":"g"},{"name":"白术","dosage":10,"unit":"g"},{"name":"甘草","dosage":6,"unit":"g"}],"formulaName":"逍遥散加减","prescriptionType":"raw_herbs","prognosis":"预计1-2周后头痛改善，睡眠逐步好转，建议复诊。","feedback":"","previousPrognosisReview":null,"servicePriceList":"2024-02","services":[{"name":"针灸首诊 Basic Acupuncture 60min","price":120,"quantity":1,"manualDiscount":0,"taxable":true},{"name":"中药处方（7剂）","price":280,"quantity":1,"manualDiscount":0,"taxable":true}],"consultationFee":0,"discountType":"none","discountValue":0,"taxable":true,"includeRxAmount":false,"add3rdParty":false,"currency":"CAD","comments":"","totalAmount":400,"taxAmount":52,"totalWithoutTax":400,"documents":[],"invoicePdfUrl":null,"consultationPdfUrl":null,"modifications":[],"parentConsultationId":null}',
'2024-02-01 14:00:00');

INSERT IGNORE INTO tcm_consultation (id, consultation_id, patient_id, practitioner_id, consult_date, status, branch_id, locked_at, version, payload, create_time)
VALUES ('consult-2', 'ORD-00002-D4E5F6', 'patient-1', '101', '2024-02-15', 'paid', 'branch-main', '2024-02-15 16:00:00', 1,
'{"chiefComplaint":"Fatigue 疲劳","chiefComplaintDuration":"1-4 weeks 一个月内","chiefComplaintDescription":"复诊。头痛明显改善，睡眠好转约60%，仍感乏力。","progressOfDisease":"整体好转，乏力症状为主要问题。","summary":"复诊。头痛明显改善，睡眠好转约60%，仍感乏力。","differentiation":"气血两虚为主，肝郁已解","treatment":"补气养血为主，继续针灸治疗。","diff":{"coldHeat":"Neither 无","sweat":"Spontaneous sweating 自汗","headDiscomfort":"偶有轻微头痛","eye":"","ear":"","nose":"","mouth":"","taste":"Normal 正常","bodyDiscomforts":"全身乏力","skinIssues":"","otherExterior":"","chest":"Normal 正常","heart":"Normal 正常","hypochondriac":"None 无","sleep":"Dream-disturbed 多梦","anxietyStress":"","otherChest":"","appetite":"Poor appetit 食欲不振","thirst":"Normal 正常","abdomen":"Normal 正常","otherAbdomen":"","bowelMovement":"Loose 便溏","urine":"Normal 正常","otherLowerAbdomen":"","periodCircle":"","periodDuration":"","bloodQuality":"","pms":"","otherFemale":"","pathologicalChannel":"脾经，胃经","pathologicalChanges":"腹部压痛减轻","pulse":"Thready 细","detailedPulse":"细弱","tongueColor":"Pale 淡白","tongueBody":"Swollen 胖大","tongueCoating":"Thin white 薄白","otherTongue":"舌边有齿痕","tongueImage":null,"conclusions":[{"name":"气血两虚","treatment":"补气养血"},{"name":"脾虚湿困","treatment":"健脾祛湿"}]},"acupuncture":[{"point":"气海","side":"bilateral","notes":""},{"point":"足三里","side":"bilateral","notes":"补法"},{"point":"三阴交","side":"bilateral","notes":""}],"prescriptions":[{"id":"rx-2","direction":"内服 Oral intake","whereToGet":"In-store 店内取药","quantity":7,"preferredUnit":"g","formulaName":"八珍汤加减","items":[{"name":"黄芪","dosage":30,"unit":"g","category":"38. 补气药","guijing":"肺, 脾","nature":"6. 温","taste":"Sweet甜","pricePerUnit":0.08},{"name":"党参","dosage":15,"unit":"g","category":"38. 补气药","guijing":"脾, 肺","nature":"4. 平","taste":"Sweet甜","pricePerUnit":0.12},{"name":"白术","dosage":10,"unit":"g","category":"38. 补气药","guijing":"脾, 胃","nature":"6. 温","taste":"Bitter苦, Sweet甜","pricePerUnit":0.10},{"name":"当归","dosage":10,"unit":"g","category":"40. 补血药","guijing":"肝, 心, 脾","nature":"6. 温","taste":"Sweet甜, Acrid辛","pricePerUnit":0.20},{"name":"熟地黄","dosage":15,"unit":"g","category":"40. 补血药","guijing":"肝, 肾","nature":"3. 微寒","taste":"Sweet甜","pricePerUnit":0.14},{"name":"甘草","dosage":6,"unit":"g","category":"38. 补气药","guijing":"脾, 肺","nature":"4. 平","taste":"Sweet甜","pricePerUnit":0.06}],"subtotal":18.62,"dispensingCompleted":true}],"herbals":[{"name":"黄芪","dosage":30,"unit":"g"},{"name":"党参","dosage":15,"unit":"g"},{"name":"白术","dosage":10,"unit":"g"},{"name":"当归","dosage":10,"unit":"g"},{"name":"熟地黄","dosage":15,"unit":"g"},{"name":"甘草","dosage":6,"unit":"g"}],"formulaName":"八珍汤加减","prescriptionType":"raw_herbs","prognosis":"再坚持两周，乏力症状可显著改善。","feedback":"","previousPrognosisReview":"上次预测头痛会改善——准确，睡眠改善比预期稍慢。","servicePriceList":"2024-02","services":[{"name":"针灸复诊 Supercare Acupuncture 30min","price":80,"quantity":1,"manualDiscount":0,"taxable":true},{"name":"中药处方（7剂）","price":280,"quantity":1,"manualDiscount":0,"taxable":true}],"consultationFee":0,"discountType":"none","discountValue":0,"taxable":true,"includeRxAmount":false,"add3rdParty":false,"currency":"CAD","comments":"","totalAmount":360,"taxAmount":46.8,"totalWithoutTax":360,"documents":[],"invoicePdfUrl":null,"consultationPdfUrl":null,"modifications":[],"parentConsultationId":"consult-1"}',
'2024-02-15 14:30:00');

-- 预约
INSERT IGNORE INTO tcm_appointment (id, patient_id, practitioner_id, room_id, service_type, start_time, end_time, status, branch_id, payload, create_time)
VALUES ('appt-1', 'patient-1', '101', 'room-1', 'acupuncture_followup', CONCAT(CURDATE(),' 09:00:00'), CONCAT(CURDATE(),' 09:50:00'), 'confirmed', 'branch-main', '{"intakeFormData":{"chiefComplaint":"乏力改善，但仍有轻微头痛"},"notes":""}', NOW());
INSERT IGNORE INTO tcm_appointment (id, patient_id, practitioner_id, room_id, service_type, start_time, end_time, status, branch_id, payload, create_time)
VALUES ('appt-2', 'patient-2', '101', 'room-2', 'acupuncture_new', CONCAT(CURDATE(),' 10:30:00'), CONCAT(CURDATE(),' 11:30:00'), 'booked', 'branch-main', '{"intakeFormData":{},"notes":"初次就诊"}', NOW());

-- 库存
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-1',  '逍遥散',             'powder',    '包', 50.00,   35.0000, 10.00, '同仁堂',  6.00, 'branch-main', 1, NULL, sysdate(), sysdate());
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-2',  '六味地黄丸（浓缩粉）','powder',   '包', 8.00,    40.0000, 10.00, '同仁堂',  6.00, 'branch-main', 1, NULL, sysdate(), sysdate());
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-3',  '补中益气汤',         'powder',    '包', 30.00,   38.0000, 10.00, '康仁堂',  5.00, 'branch-main', 1, NULL, sysdate(), sysdate());
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-4',  '黄芪',              'raw_herbs', 'g',  2000.00,  0.0800, 500.00,'本草药材', NULL, 'branch-main', 1, NULL, sysdate(), sysdate());
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-5',  '党参',              'raw_herbs', 'g',  1500.00,  0.1200, 300.00,'本草药材', NULL, 'branch-main', 1, NULL, sysdate(), sysdate());
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-6',  '白术',              'raw_herbs', 'g',  800.00,   0.1000, 200.00,'本草药材', NULL, 'branch-main', 1, NULL, sysdate(), sysdate());
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-7',  '茯苓',              'raw_herbs', 'g',  1200.00,  0.0900, 300.00,'本草药材', NULL, 'branch-main', 1, NULL, sysdate(), sysdate());
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-8',  '柴胡',              'raw_herbs', 'g',  150.00,   0.1500, 200.00,'本草药材', NULL, 'branch-main', 1, NULL, sysdate(), sysdate());
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-9',  '当归',              'raw_herbs', 'g',  900.00,   0.2000, 200.00,'本草药材', NULL, 'branch-main', 1, NULL, sysdate(), sysdate());
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-10', '甘草',              'raw_herbs', 'g',  1800.00,  0.0600, 300.00,'本草药材', NULL, 'branch-main', 1, NULL, sysdate(), sysdate());
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-11', '六味地黄丸',         'pills',     '盒', 25.00,   28.0000, 5.00, '同仁堂',  NULL, 'branch-main', 1, NULL, sysdate(), sysdate());
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-12', '逍遥丸',             'pills',     '盒', 3.00,    22.0000, 5.00, '同仁堂',  NULL, 'branch-main', 1, NULL, sysdate(), sysdate());
INSERT IGNORE INTO tcm_inventory_item VALUES ('inv-13', '金匮肾气丸',         'pills',     '瓶', 15.00,   35.0000, 5.00, '华佗',    NULL, 'branch-main', 1, NULL, sysdate(), sysdate());
