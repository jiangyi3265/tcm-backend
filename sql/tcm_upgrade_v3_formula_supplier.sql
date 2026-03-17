-- =============================================
-- TCM v3 升级脚本 - 方剂管理 & 供应商管理
-- 在执行 tcm_upgrade_v2.sql 之后执行此脚本
-- =============================================

-- ----------------------------
-- 1. 供应商表
-- ----------------------------
DROP TABLE IF EXISTS tcm_supplier;
CREATE TABLE tcm_supplier (
  id              varchar(64)   NOT NULL                  COMMENT '供应商ID',
  name            varchar(100)  NOT NULL                  COMMENT '供应商名称',
  contact_person  varchar(100)  DEFAULT NULL              COMMENT '联系人',
  phone           varchar(30)   DEFAULT NULL              COMMENT '电话',
  email           varchar(100)  DEFAULT NULL              COMMENT '邮箱',
  address         varchar(300)  DEFAULT NULL              COMMENT '地址',
  notes           varchar(500)  DEFAULT NULL              COMMENT '备注',
  is_active       tinyint(1)    DEFAULT 1                 COMMENT '是否启用',
  deleted_at      datetime      DEFAULT NULL              COMMENT '软删除时间',
  create_time     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_supplier_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商表';

-- ----------------------------
-- 2. 方剂主表
-- ----------------------------
DROP TABLE IF EXISTS tcm_formula_item;
DROP TABLE IF EXISTS tcm_formula;
CREATE TABLE tcm_formula (
  id              varchar(64)   NOT NULL                  COMMENT '方剂ID',
  name            varchar(100)  NOT NULL                  COMMENT '方剂名称',
  category        varchar(100)  DEFAULT NULL              COMMENT '方剂分类',
  description     varchar(500)  DEFAULT NULL              COMMENT '方剂说明/功效',
  source          varchar(200)  DEFAULT NULL              COMMENT '出处/来源',
  is_active       tinyint(1)    DEFAULT 1                 COMMENT '是否启用',
  deleted_at      datetime      DEFAULT NULL              COMMENT '软删除时间',
  create_time     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_formula_name (name),
  KEY idx_formula_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方剂表';

-- ----------------------------
-- 3. 方剂药材明细表
-- ----------------------------
CREATE TABLE tcm_formula_item (
  id              bigint(20)    NOT NULL AUTO_INCREMENT   COMMENT '主键',
  formula_id      varchar(64)   NOT NULL                  COMMENT '方剂ID',
  herb_name       varchar(100)  NOT NULL                  COMMENT '药材名称',
  dosage          decimal(10,2) DEFAULT 0                 COMMENT '默认剂量',
  unit            varchar(20)   DEFAULT 'g'               COMMENT '单位',
  sort_order      int(11)       DEFAULT 0                 COMMENT '排序',
  notes           varchar(200)  DEFAULT NULL              COMMENT '备注（如炮制方法）',
  PRIMARY KEY (id),
  KEY idx_formula_item_fid (formula_id),
  CONSTRAINT fk_formula_item_formula FOREIGN KEY (formula_id) REFERENCES tcm_formula (id) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='方剂药材明细表';

-- ----------------------------
-- 4. 库存表增加 supplier_id 字段
-- ----------------------------
ALTER TABLE tcm_inventory_item ADD COLUMN supplier_id varchar(64) DEFAULT NULL COMMENT '供应商ID' AFTER supplier;
ALTER TABLE tcm_inventory_item ADD KEY idx_inv_supplier (supplier_id);

-- ----------------------------
-- 5. 种子数据 - 供应商
-- ----------------------------
INSERT INTO tcm_supplier (id, name, contact_person, phone, email, address, notes) VALUES
('supplier-1', '同仁堂',   '张经理', '010-65135566', 'tongren@example.com',  '北京市东城区东兴隆街52号', '百年老字号'),
('supplier-2', '康仁堂',   '李经理', '010-82863366', 'kangren@example.com',  '北京市昌平区科技园区',     '中药配方颗粒供应商'),
('supplier-3', '本草药材', '王经理', '010-67891234', 'bencao@example.com',   '北京市丰台区南苑路',       '散装草药供应商'),
('supplier-4', '华佗',     '赵经理', '020-88881234', 'huatuo@example.com',   '广东省广州市天河区',       '成药供应商');

-- 回填已有库存数据的 supplier_id
UPDATE tcm_inventory_item SET supplier_id = 'supplier-1' WHERE supplier = '同仁堂';
UPDATE tcm_inventory_item SET supplier_id = 'supplier-2' WHERE supplier = '康仁堂';
UPDATE tcm_inventory_item SET supplier_id = 'supplier-3' WHERE supplier = '本草药材';
UPDATE tcm_inventory_item SET supplier_id = 'supplier-4' WHERE supplier = '华佗';

-- ----------------------------
-- 6. 种子数据 - 方剂
-- ----------------------------
INSERT INTO tcm_formula (id, name, category, description, source) VALUES
('formula-1', '四君子汤',   '补益剂', '益气健脾',               '《太平惠民和剂局方》'),
('formula-2', '六味地黄丸', '补益剂', '滋补肝肾',               '《小儿药证直诀》'),
('formula-3', '逍遥散',     '和解剂', '疏肝解郁，养血健脾',     '《太平惠民和剂局方》'),
('formula-4', '补中益气汤', '补益剂', '补中益气，升阳举陷',     '《内外伤辨惑论》'),
('formula-5', '八珍汤',     '补益剂', '气血双补',               '《正体类要》'),
('formula-6', '小柴胡汤',   '和解剂', '和解少阳',               '《伤寒论》'),
('formula-7', '桂枝汤',     '解表剂', '解肌发表，调和营卫',     '《伤寒论》'),
('formula-8', '金匮肾气丸', '补益剂', '温补肾阳',               '《金匮要略》');

-- 方剂明细
INSERT INTO tcm_formula_item (formula_id, herb_name, dosage, unit, sort_order) VALUES
-- 四君子汤
('formula-1', '党参', 15, 'g', 1),
('formula-1', '白术', 10, 'g', 2),
('formula-1', '茯苓', 15, 'g', 3),
('formula-1', '甘草',  6, 'g', 4),
-- 六味地黄丸
('formula-2', '熟地黄', 24, 'g', 1),
('formula-2', '山萸肉', 12, 'g', 2),
('formula-2', '山药',   12, 'g', 3),
('formula-2', '泽泻',    9, 'g', 4),
('formula-2', '茯苓',    9, 'g', 5),
('formula-2', '牡丹皮',  9, 'g', 6),
-- 逍遥散
('formula-3', '柴胡', 10, 'g', 1),
('formula-3', '白芍', 15, 'g', 2),
('formula-3', '当归', 10, 'g', 3),
('formula-3', '茯苓', 15, 'g', 4),
('formula-3', '白术', 10, 'g', 5),
('formula-3', '甘草',  6, 'g', 6),
('formula-3', '薄荷',  3, 'g', 7),
('formula-3', '生姜',  3, 'g', 8),
-- 补中益气汤
('formula-4', '黄芪', 30, 'g', 1),
('formula-4', '党参', 15, 'g', 2),
('formula-4', '白术', 10, 'g', 3),
('formula-4', '甘草',  6, 'g', 4),
('formula-4', '当归', 10, 'g', 5),
('formula-4', '陈皮',  6, 'g', 6),
('formula-4', '升麻',  6, 'g', 7),
('formula-4', '柴胡',  6, 'g', 8),
-- 八珍汤
('formula-5', '党参', 15, 'g', 1),
('formula-5', '白术', 10, 'g', 2),
('formula-5', '茯苓', 15, 'g', 3),
('formula-5', '甘草',  6, 'g', 4),
('formula-5', '当归', 10, 'g', 5),
('formula-5', '白芍', 10, 'g', 6),
('formula-5', '川芎',  8, 'g', 7),
('formula-5', '熟地黄', 15, 'g', 8),
-- 小柴胡汤
('formula-6', '柴胡',   24, 'g', 1),
('formula-6', '黄芩',    9, 'g', 2),
('formula-6', '党参',    9, 'g', 3),
('formula-6', '法半夏',  9, 'g', 4),
('formula-6', '甘草',    6, 'g', 5),
('formula-6', '生姜',    9, 'g', 6),
('formula-6', '大枣',   12, 'g', 7),
-- 桂枝汤
('formula-7', '桂枝',  9, 'g', 1),
('formula-7', '白芍',  9, 'g', 2),
('formula-7', '生姜',  9, 'g', 3),
('formula-7', '大枣', 12, 'g', 4),
('formula-7', '甘草',  6, 'g', 5),
-- 金匮肾气丸
('formula-8', '熟地黄', 24, 'g', 1),
('formula-8', '山萸肉', 12, 'g', 2),
('formula-8', '山药',   12, 'g', 3),
('formula-8', '泽泻',    9, 'g', 4),
('formula-8', '茯苓',    9, 'g', 5),
('formula-8', '牡丹皮',  9, 'g', 6),
('formula-8', '附子',    3, 'g', 7),
('formula-8', '桂枝',    3, 'g', 8);

-- ----------------------------
-- 7. 菜单权限
-- ----------------------------
INSERT INTO sys_menu VALUES (2008, '方剂管理', 2000, 8,  'formula',  'tcm/formula/index',  '', '', 1, 0, 'C', '0', '0', 'tcm:formula:list',  'documentation', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2009, '供应商管理', 2000, 9, 'supplier', 'tcm/supplier/index', '', '', 1, 0, 'C', '0', '0', 'tcm:supplier:list', 'international', 'admin', sysdate(), '', NULL, '');

-- 按钮权限 - 方剂
INSERT INTO sys_menu VALUES (2810, '方剂查询', 2008, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:formula:query',  '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2811, '方剂新增', 2008, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:formula:add',    '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2812, '方剂修改', 2008, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:formula:edit',   '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2813, '方剂删除', 2008, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:formula:remove', '#', 'admin', sysdate(), '', NULL, '');

-- 按钮权限 - 供应商
INSERT INTO sys_menu VALUES (2820, '供应商查询', 2009, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:supplier:query',  '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2821, '供应商新增', 2009, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:supplier:add',    '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2822, '供应商修改', 2009, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:supplier:edit',   '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2823, '供应商删除', 2009, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:supplier:remove', '#', 'admin', sysdate(), '', NULL, '');

-- 角色-菜单关联 (admin 拥有全部)
INSERT INTO sys_role_menu VALUES (1,2008),(1,2009);
INSERT INTO sys_role_menu VALUES (1,2810),(1,2811),(1,2812),(1,2813);
INSERT INTO sys_role_menu VALUES (1,2820),(1,2821),(1,2822),(1,2823);

-- practitioner 可查看方剂和供应商
INSERT INTO sys_role_menu VALUES (100,2008),(100,2009);
INSERT INTO sys_role_menu VALUES (100,2810),(100,2820);
