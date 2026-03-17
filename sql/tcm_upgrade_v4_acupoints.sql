-- =============================================
-- TCM v4 升级脚本 - 针灸穴位管理 & 单位换算
-- 在执行 tcm_upgrade_v3 之后执行此脚本
-- =============================================

-- ----------------------------
-- 1. 针灸穴位表
-- ----------------------------
DROP TABLE IF EXISTS tcm_acupoint;
CREATE TABLE tcm_acupoint (
  id              varchar(64)   NOT NULL                  COMMENT '穴位ID',
  name            varchar(100)  NOT NULL                  COMMENT '穴位名称',
  pinyin          varchar(100)  DEFAULT NULL              COMMENT '拼音',
  english_name    varchar(200)  DEFAULT NULL              COMMENT '英文名',
  meridian        varchar(100)  DEFAULT NULL              COMMENT '所属经络',
  location        varchar(500)  DEFAULT NULL              COMMENT '定位',
  indication      varchar(500)  DEFAULT NULL              COMMENT '主治',
  method          varchar(300)  DEFAULT NULL              COMMENT '刺法',
  notes           varchar(500)  DEFAULT NULL              COMMENT '备注',
  is_active       tinyint(1)    DEFAULT 1                 COMMENT '是否启用',
  deleted_at      datetime      DEFAULT NULL              COMMENT '软删除时间',
  create_time     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     datetime      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_acupoint_name (name),
  KEY idx_acupoint_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='针灸穴位表';

-- ----------------------------
-- 2. 单位换算表
-- ----------------------------
DROP TABLE IF EXISTS tcm_unit_conversion;
CREATE TABLE tcm_unit_conversion (
  id              bigint(20)    NOT NULL AUTO_INCREMENT   COMMENT '主键',
  from_unit       varchar(20)   NOT NULL                  COMMENT '源单位',
  to_unit         varchar(20)   NOT NULL                  COMMENT '目标单位',
  factor          decimal(12,6) NOT NULL                  COMMENT '换算因子（1源=factor目标）',
  notes           varchar(200)  DEFAULT NULL              COMMENT '备注',
  create_time     datetime      DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_unit_pair (from_unit, to_unit)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='单位换算表';

-- ----------------------------
-- 3. 种子数据 - 穴位
-- ----------------------------
INSERT INTO tcm_acupoint (id, name, pinyin, english_name, meridian, location, indication) VALUES
('acu-001', '百会', 'bǎi huì',   'GV20 Baihui',   '督脉',   '头顶正中线与两耳尖连线的交点', '头痛、眩晕、中风、失眠'),
('acu-002', '神庭', 'shén tíng',  'GV24 Shenting',  '督脉',   '前发际正中直上0.5寸', '头痛、眩晕、失眠、鼻渊'),
('acu-003', '印堂', 'yìn táng',   'EX-HN3 Yintang', '经外奇穴','两眉头连线的中点', '头痛、眩晕、鼻渊、失眠'),
('acu-004', '太阳', 'tài yáng',   'EX-HN5 Taiyang', '经外奇穴','眉梢与目外眦之间向后约1寸凹陷处', '头痛、目疾'),
('acu-005', '风池', 'fēng chí',   'GB20 Fengchi',    '足少阳胆经','枕骨之下，胸锁乳突肌与斜方肌上端之间的凹陷', '头痛、眩晕、颈项强痛、目赤肿痛'),
('acu-006', '风府', 'fēng fǔ',    'GV16 Fengfu',    '督脉',   '后发际正中直上1寸', '头痛、项强、眩晕、中风'),
('acu-007', '大椎', 'dà zhuī',    'GV14 Dazhui',    '督脉',   '第7颈椎棘突下凹陷中', '热病、疟疾、咳嗽、项强'),
('acu-008', '合谷', 'hé gǔ',      'LI4 Hegu',       '手阳明大肠经','第1、2掌骨间，第2掌骨桡侧中点', '头痛、目赤肿痛、牙痛、咽喉肿痛'),
('acu-009', '曲池', 'qū chí',     'LI11 Quchi',     '手阳明大肠经','肘横纹外侧端，屈肘时肱骨外上髁与肘横纹连线中点', '热病、咽喉肿痛、上肢不遂'),
('acu-010', '足三里', 'zú sān lǐ', 'ST36 Zusanli',   '足阳明胃经','犊鼻穴下3寸，胫骨前嵴外1横指', '胃痛、呕吐、腹胀、泄泻、虚劳'),
('acu-011', '三阴交', 'sān yīn jiāo','SP6 Sanyinjiao','足太阴脾经','内踝尖上3寸，胫骨内侧面后缘', '脾胃虚弱、月经不调、带下、遗精'),
('acu-012', '太冲', 'tài chōng',   'LR3 Taichong',   '足厥阴肝经','足背第1、2跖骨结合部之前凹陷中', '头痛、眩晕、目赤肿痛、胁痛'),
('acu-013', '内关', 'nèi guān',    'PC6 Neiguan',    '手厥阴心包经','腕横纹上2寸，掌长肌腱与桡侧腕屈肌腱之间', '心痛、心悸、胸闷、胃痛、呕吐'),
('acu-014', '涌泉', 'yǒng quán',   'KI1 Yongquan',   '足少阴肾经','足底前1/3与后2/3交界处凹陷中', '头痛、头顶痛、失眠、便秘'),
('acu-015', '气海', 'qì hǎi',     'CV6 Qihai',      '任脉',   '脐中下1.5寸', '虚脱、腹痛、泄泻、月经不调'),
('acu-016', '关元', 'guān yuán',   'CV4 Guanyuan',   '任脉',   '脐中下3寸', '中风脱证、虚劳、泄泻、痢疾'),
('acu-017', '中脘', 'zhōng wǎn',   'CV12 Zhongwan',  '任脉',   '脐中上4寸', '胃痛、呕吐、呃逆、腹胀'),
('acu-018', '天枢', 'tiān shū',    'ST25 Tianshu',   '足阳明胃经','脐中旁开2寸', '腹胀、泄泻、痢疾、便秘'),
('acu-019', '肩井', 'jiān jǐng',   'GB21 Jianjing',  '足少阳胆经','大椎与肩峰连线的中点', '肩背痹痛、上肢不遂、难产'),
('acu-020', '委中', 'wěi zhōng',   'BL40 Weizhong',  '足太阳膀胱经','腘横纹中点', '腰痛、下肢痿痹、腹痛、吐泻');

-- ----------------------------
-- 4. 种子数据 - 单位换算
-- ----------------------------
INSERT INTO tcm_unit_conversion (from_unit, to_unit, factor, notes) VALUES
('kg',  'g',    1000.000000, '千克 → 克'),
('g',   'kg',      0.001000, '克 → 千克'),
('g',   'mg',   1000.000000, '克 → 毫克'),
('mg',  'g',       0.001000, '毫克 → 克'),
('liang','g',     50.000000, '两 → 克（现代）'),
('g',   'liang',   0.020000, '克 → 两'),
('qian','g',       5.000000, '钱 → 克'),
('g',   'qian',    0.200000, '克 → 钱'),
('jin', 'g',     500.000000, '斤 → 克'),
('g',   'jin',     0.002000, '克 → 斤'),
('oz',  'g',      28.349523, '盎司 → 克'),
('g',   'oz',      0.035274, '克 → 盎司'),
('lb',  'g',     453.592370, '磅 → 克'),
('g',   'lb',      0.002205, '克 → 磅'),
('ml',  'g',       1.000000, '毫升 → 克（水近似）'),
('g',   'ml',      1.000000, '克 → 毫升（水近似）');

-- ----------------------------
-- 5. 菜单权限 - 穴位管理
-- ----------------------------
INSERT INTO sys_menu VALUES (2010, '穴位管理', 2000, 10, 'acupoint', 'tcm/acupoint/index', '', '', 1, 0, 'C', '0', '0', 'tcm:acupoint:list', 'guide', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2830, '穴位查询', 2010, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:acupoint:query',  '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2831, '穴位新增', 2010, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:acupoint:add',    '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2832, '穴位修改', 2010, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:acupoint:edit',   '#', 'admin', sysdate(), '', NULL, '');
INSERT INTO sys_menu VALUES (2833, '穴位删除', 2010, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'tcm:acupoint:remove', '#', 'admin', sysdate(), '', NULL, '');

INSERT INTO sys_role_menu VALUES (1,2010),(1,2830),(1,2831),(1,2832),(1,2833);
INSERT INTO sys_role_menu VALUES (100,2010),(100,2830);
