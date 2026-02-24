package com.company.logging.tool.core.context;

public class LogSqlContext {
    private String sqlId;
    private String sql;
    private String sqlParam;
    private long elapsed;

    LogSqlContext(Builder builder) {
        this.setSqlId(builder.sqlId);
        this.setSql(builder.sql);
        this.setSqlParam(builder.sqlParam);
        this.setElapsed(builder.elapsed);
    }

    public String getSqlId() {
        return sqlId;
    }

    public void setSqlId(String sqlId) {
        this.sqlId = sqlId;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public String getSqlParam() {
        return sqlParam;
    }

    public void setSqlParam(String sqlParam) {
        this.sqlParam = sqlParam;
    }

    public long getElapsed() {
        return elapsed;
    }

    public void setElapsed(long elapsed) {
        this.elapsed = elapsed;
    }

    public static class Builder {
        private String sqlId;
        private String sql;
        private String sqlParam;
        private long elapsed;

        public Builder sqlId(String sqlId) {
            this.sqlId = sqlId;
            return this;
        }

        public Builder sql(String sql) {
            this.sql = sql;
            return this;
        }

        public Builder sqlParam(String sqlParam) {
            this.sqlParam = sqlParam;
            return this;
        }

        public Builder elapsed(long elapsed) {
            this.elapsed = elapsed;
            return this;
        }

        public LogSqlContext build() {
            return new LogSqlContext(this);
        }
    }
}
