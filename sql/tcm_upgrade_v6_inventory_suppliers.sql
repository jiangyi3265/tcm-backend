-- =============================================
-- TCM v6 升级脚本 - 库存扩展 + 供应商种子数据
-- 在执行 tcm_upgrade_v5 之后执行此脚本
-- supplierId 约定: supplier-1=同仁堂, supplier-2=康仁堂, supplier-3=本草药材, supplier-4=华佗
-- =============================================

-- ----------------------------
-- 1. 确保 supplier_id 列存在（幂等）
-- ----------------------------
SET @col_exists = (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tcm_inventory_item' AND COLUMN_NAME = 'supplier_id');
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE tcm_inventory_item ADD COLUMN supplier_id varchar(64) DEFAULT NULL COMMENT ''供应商ID'' AFTER supplier',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tcm_inventory_item' AND INDEX_NAME = 'idx_inv_supplier');
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE tcm_inventory_item ADD KEY idx_inv_supplier (supplier_id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ----------------------------
-- 2. 确保供应商存在
-- ----------------------------
INSERT IGNORE INTO tcm_supplier (id, name, contact_person, phone, email, address, notes, is_active, create_time, update_time)
VALUES
('supplier-1', '同仁堂',   '张经理', '010-65135566', 'tongren@example.com',  '北京市东城区东兴隆街52号', '百年老字号',           1, sysdate(), sysdate()),
('supplier-2', '康仁堂',   '李经理', '010-82863366', 'kangren@example.com',  '北京市昌平区科技园区',     '中药配方颗粒供应商',   1, sysdate(), sysdate()),
('supplier-3', '本草药材', '王经理', '010-67891234', 'bencao@example.com',   '北京市丰台区南苑路',       '散装草药供应商',       1, sysdate(), sysdate()),
('supplier-4', '华佗',     '赵经理', '020-88881234', 'huatuo@example.com',   '广东省广州市天河区',       '成药供应商',           1, sysdate(), sysdate());

-- ----------------------------
-- 3. 更新已有库存的 supplier_id（幂等）
-- ----------------------------
UPDATE tcm_inventory_item SET supplier_id = 'supplier-1' WHERE id IN ('inv-1','inv-2')  AND supplier_id IS NULL;
UPDATE tcm_inventory_item SET supplier_id = 'supplier-2' WHERE id = 'inv-3'  AND supplier_id IS NULL;
UPDATE tcm_inventory_item SET supplier_id = 'supplier-3' WHERE id IN ('inv-4','inv-5','inv-6','inv-7','inv-8','inv-9','inv-10') AND supplier_id IS NULL;
UPDATE tcm_inventory_item SET supplier_id = 'supplier-1' WHERE id IN ('inv-11','inv-12') AND supplier_id IS NULL;
UPDATE tcm_inventory_item SET supplier_id = 'supplier-4' WHERE id = 'inv-13' AND supplier_id IS NULL;

-- ----------------------------
-- 4. 新增库存 - 草药（多供应商）
-- ----------------------------
INSERT IGNORE INTO tcm_inventory_item (id, name, category, unit, quantity, price_per_unit, min_stock_level, supplier, supplier_id, grams_per_packet, branch_id, is_active, create_time, update_time)
VALUES
-- 人参
('inv-14', '人参',   'raw_herbs', 'g', 500.00,  0.8000, 100.00, '本草药材', 'supplier-3', NULL, 'branch-main', 1, sysdate(), sysdate()),
('inv-15', '人参',   'raw_herbs', 'g', 300.00,  1.2000, 100.00, '同仁堂',   'supplier-1', NULL, 'branch-main', 1, sysdate(), sysdate()),
-- 麦冬
('inv-16', '麦冬',   'raw_herbs', 'g', 1200.00, 0.1500, 200.00, '本草药材', 'supplier-3', NULL, 'branch-main', 1, sysdate(), sysdate()),
('inv-17', '麦冬',   'raw_herbs', 'g', 600.00,  0.1800, 200.00, '康仁堂',   'supplier-2', NULL, 'branch-main', 1, sysdate(), sysdate()),
-- 五味子
('inv-18', '五味子', 'raw_herbs', 'g', 800.00,  0.2500, 150.00, '本草药材', 'supplier-3', NULL, 'branch-main', 1, sysdate(), sysdate()),
('inv-19', '五味子', 'raw_herbs', 'g', 400.00,  0.3000, 100.00, '华佗',     'supplier-4', NULL, 'branch-main', 1, sysdate(), sysdate()),
-- 其他草药
('inv-20', '白芍',   'raw_herbs', 'g', 1000.00, 0.1200, 200.00, '本草药材', 'supplier-3', NULL, 'branch-main', 1, sysdate(), sysdate()),
('inv-21', '熟地黄', 'raw_herbs', 'g', 600.00,  0.1400, 200.00, '本草药材', 'supplier-3', NULL, 'branch-main', 1, sysdate(), sysdate()),
('inv-22', '黄芩',   'raw_herbs', 'g', 700.00,  0.1000, 150.00, '本草药材', 'supplier-3', NULL, 'branch-main', 1, sysdate(), sysdate()),
('inv-23', '山药',   'raw_herbs', 'g', 900.00,  0.0800, 200.00, '本草药材', 'supplier-3', NULL, 'branch-main', 1, sysdate(), sysdate()),
('inv-24', '陈皮',   'raw_herbs', 'g', 500.00,  0.0500, 100.00, '本草药材', 'supplier-3', NULL, 'branch-main', 1, sysdate(), sysdate()),
('inv-25', '川芎',   'raw_herbs', 'g', 400.00,  0.1800, 100.00, '本草药材', 'supplier-3', NULL, 'branch-main', 1, sysdate(), sysdate()),
('inv-26', '桂枝',   'raw_herbs', 'g', 600.00,  0.1000, 100.00, '本草药材', 'supplier-3', NULL, 'branch-main', 1, sysdate(), sysdate()),
('inv-27', '生姜',   'raw_herbs', 'g', 2000.00, 0.0300, 300.00, '本草药材', 'supplier-3', NULL, 'branch-main', 1, sysdate(), sysdate()),
('inv-28', '大枣',   'raw_herbs', 'g', 1500.00, 0.0400, 200.00, '本草药材', 'supplier-3', NULL, 'branch-main', 1, sysdate(), sysdate()),
('inv-29', '薄荷',   'raw_herbs', 'g', 300.00,  0.0600, 80.00,  '本草药材', 'supplier-3', NULL, 'branch-main', 1, sysdate(), sysdate()),
('inv-30', '法半夏', 'raw_herbs', 'g', 400.00,  0.2200, 100.00, '本草药材', 'supplier-3', NULL, 'branch-main', 1, sysdate(), sysdate());

-- ----------------------------
-- 5. 新增库存 - 粉剂（多供应商、不同 grams_per_packet）
-- ----------------------------
INSERT IGNORE INTO tcm_inventory_item (id, name, category, unit, quantity, price_per_unit, min_stock_level, supplier, supplier_id, grams_per_packet, branch_id, is_active, create_time, update_time)
VALUES
-- 人参粉剂（同仁堂 2g/包，康仁堂 5g/包）
('inv-31', '人参',   'powder', '包', 200.00, 5.0000, 30.00, '同仁堂', 'supplier-1', 2.00, 'branch-main', 1, sysdate(), sysdate()),
('inv-32', '人参',   'powder', '包', 150.00, 3.5000, 30.00, '康仁堂', 'supplier-2', 5.00, 'branch-main', 1, sysdate(), sysdate()),
-- 麦冬粉剂
('inv-33', '麦冬',   'powder', '包', 300.00, 2.0000, 40.00, '同仁堂', 'supplier-1', 3.00, 'branch-main', 1, sysdate(), sysdate()),
('inv-34', '麦冬',   'powder', '包', 180.00, 1.8000, 30.00, '康仁堂', 'supplier-2', 5.00, 'branch-main', 1, sysdate(), sysdate()),
-- 五味子粉剂
('inv-35', '五味子', 'powder', '包', 250.00, 2.5000, 30.00, '同仁堂', 'supplier-1', 2.00, 'branch-main', 1, sysdate(), sysdate()),
('inv-36', '五味子', 'powder', '包', 100.00, 2.2000, 20.00, '华佗',   'supplier-4', 3.00, 'branch-main', 1, sysdate(), sysdate()),
-- 生脉散成方粉剂
('inv-37', '生脉散', 'powder', '包', 60.00,  8.0000, 10.00, '同仁堂', 'supplier-1', 6.00, 'branch-main', 1, sysdate(), sysdate());

-- ----------------------------
-- 6. 新增方剂 - 生脉散、归脾汤、四物汤
-- ----------------------------
INSERT IGNORE INTO tcm_formula (id, name, category, description, source, is_active, create_time, update_time)
VALUES
('formula-shengmai', '生脉散', '补益剂',
 '益气复脉，养阴生津。用于气阴两亏，心悸气短，脉微欲绝。',
 '《医学启源》', 1, sysdate(), sysdate()),

('formula-guipi', '归脾汤', '补益剂',
 '益气补血，健脾养心。用于心脾两虚，气血不足。',
 '《济生方》', 1, sysdate(), sysdate()),

('formula-siwu', '四物汤', '补益剂',
 '补血调经。用于血虚萎黄，头晕心悸，月经不调。',
 '《太平惠民和剂局方》', 1, sysdate(), sysdate());

-- 方剂明细（如果使用 tcm_formula_item 表）
INSERT IGNORE INTO tcm_formula_item (formula_id, herb_name, dosage, unit, sort_order) VALUES
-- 生脉散
('formula-shengmai', '人参',   10, 'g', 1),
('formula-shengmai', '麦冬',   30, 'g', 2),
('formula-shengmai', '五味子',  6, 'g', 3),
-- 归脾汤
('formula-guipi', '黄芪', 15, 'g', 1),
('formula-guipi', '党参', 15, 'g', 2),
('formula-guipi', '白术', 10, 'g', 3),
('formula-guipi', '茯苓', 15, 'g', 4),
('formula-guipi', '当归', 10, 'g', 5),
('formula-guipi', '甘草',  6, 'g', 6),
-- 四物汤
('formula-siwu', '当归',   10, 'g', 1),
('formula-siwu', '白芍',   10, 'g', 2),
('formula-siwu', '川芎',    8, 'g', 3),
('formula-siwu', '熟地黄', 15, 'g', 4);
