package com.company.test.controller;

import com.company.test.mapper.TestMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    private final TestMapper testMapper;

    public TestController(TestMapper testMapper) {
        this.testMapper = testMapper;
    }

    @GetMapping("/test")
    public String test() {
        int result = testMapper.selectOne();
        return "Result: " + result;
    }

    @GetMapping("/test-param")
    public String testParam(@RequestParam(defaultValue = "testValue") String value) {
        String result = testMapper.selectWithParam(value);
        return "Result with param: " + result;
    }
}
