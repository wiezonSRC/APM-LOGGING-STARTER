package com.company.test.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TestMapper {
    @Select("SELECT 1")
    int selectOne();

    @Select("SELECT #{value} as val")
    String selectWithParam(@Param("value") String value);

    @Select("SELECT 'Long text to test truncation' as val")
    String selectLongText();

    @Select("SELECT * FROM NON_EXISTENT_TABLE")
    void selectError();
}
