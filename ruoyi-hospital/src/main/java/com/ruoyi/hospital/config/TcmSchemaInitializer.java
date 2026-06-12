package com.ruoyi.hospital.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TcmSchemaInitializer implements ApplicationRunner
{
    private static final Logger log = LoggerFactory.getLogger(TcmSchemaInitializer.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args)
    {
        ensureColumn("tcm_herb_dict", "latin_name", "varchar(255) null comment 'Latin Name'");
    }

    private void ensureColumn(String tableName, String columnName, String definition)
    {
        try
        {
            Integer count = jdbcTemplate.queryForObject(
                    "select count(*) from information_schema.columns "
                            + "where table_schema = database() and table_name = ? and column_name = ?",
                    Integer.class,
                    tableName,
                    columnName);
            if (count != null && count > 0)
            {
                return;
            }
            jdbcTemplate.execute("alter table " + tableName + " add column " + columnName + " " + definition);
            log.info("Added missing hospital schema column {}.{}", tableName, columnName);
        }
        catch (Exception e)
        {
            log.warn("Hospital schema check skipped for {}.{}: {}", tableName, columnName, e.getMessage());
        }
    }
}
