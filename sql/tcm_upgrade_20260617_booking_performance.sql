-- ============================================================
-- OTCM 2026-06-17 public booking performance patch
-- Adds composite indexes used by public booking availability lookups.
-- Safe to run multiple times.
-- ============================================================

SET @idx_exists = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'tcm_appointment'
    AND INDEX_NAME = 'idx_appt_practitioner_time'
);
SET @sql = IF(@idx_exists = 0,
  'ALTER TABLE tcm_appointment ADD KEY idx_appt_practitioner_time (practitioner_id, start_time, end_time, status)',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'tcm_appointment'
    AND INDEX_NAME = 'idx_appt_room_time'
);
SET @sql = IF(@idx_exists = 0,
  'ALTER TABLE tcm_appointment ADD KEY idx_appt_room_time (room_id, start_time, end_time, status)',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
