--
-- 增加一个索引
SELECT IF(EXISTS(SELECT DISTINCT index_name
                 FROM information_schema.statistics
                 WHERE table_schema = DATABASE()
                   AND table_name = 'api_definition'
                   AND index_name LIKE 'api_definition_module_id_status_index'),
          'select 1',
          'ALTER TABLE api_definition ADD INDEX api_definition_module_id_status_index (module_id, status)')
INTO @a;
PREPARE stmt1 FROM @a;
EXECUTE stmt1;
DEALLOCATE PREPARE stmt1;

--
-- 增加一个索引
SELECT IF(EXISTS(SELECT DISTINCT index_name
                 FROM information_schema.statistics
                 WHERE table_schema = DATABASE()
                   AND table_name = 'load_test_report_detail'
                   AND index_name LIKE 'load_test_report_detail_report_id_part_index'),
          'select 1',
          'ALTER TABLE load_test_report_detail ADD INDEX load_test_report_detail_report_id_part_index (report_id, part)')
INTO @a;
PREPARE stmt2 FROM @a;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;