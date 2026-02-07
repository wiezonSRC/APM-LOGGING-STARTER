package com.company.logging.sql;


/**
 * 단일 SQL 실행 정보를 담는 불변(Immutable) 객체에 가까운 클래스입니다.
 * SQL ID, 실행된 SQL 문, 파라미터, 실행 시간을 저장합니다.
 */
public class SqlTrace {
    private String sqlId;
    private String sql;
    private String sqlParam;
    private long elapsed;


    public SqlTrace(){}

    public SqlTrace(String sqlId, String sql, String sqlParam, long elapsed){
        this.sqlId=sqlId;
        this.sql=sql;
        this.sqlParam = sqlParam;
        this.elapsed=elapsed;
    }


    public String getSqlId(){ return this.sqlId;}
    public String getSql(){ return this.sql;}
    public String getSqlParam() {return this.sqlParam;}
    public long getElapsed(){ return this.elapsed;}
}