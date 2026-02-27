package com.company.test.mapper;

public class LongSqlProvider {
    public static String buildSql() {
        StringBuilder sb = new StringBuilder();

        sb.append("SELECT * FROM (");

        for (int i = 0; i < 300; i++) {
            sb.append("SELECT 'TEST").append(i).append("' AS COL ");
            if (i != 299) {
                sb.append("UNION ALL ");
            }
        }

        sb.append(") A");

        return sb.toString();
    }
}
