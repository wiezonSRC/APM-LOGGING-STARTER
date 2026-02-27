package com.company.test.controller;

import com.company.test.mapper.TestMapper;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file) {
        return "File uploaded: " + file.getOriginalFilename() + ", size: " + file.getSize();
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> download() {
        String content = "This is a downloadable file content.";
        ByteArrayResource resource = new ByteArrayResource(content.getBytes());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"download.txt\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(content.length())
                .body(resource);
    }


    @GetMapping(value = "/html", produces = MediaType.TEXT_HTML_VALUE)
    public String html() {
        return "<html><body><h1>Hello World</h1><script>console.log('hi');</script></body></html>";
    }

    @GetMapping("/test-many-queries")
    public String testManyQueries(@RequestParam(defaultValue = "110") int count) {
        for (int i = 0; i < count; i++) {
            testMapper.selectOne();
        }
        return "Executed " + count + " queries";
    }

    @GetMapping("/test-error-with-limit")
    public String testErrorWithLimit(@RequestParam(defaultValue = "105") int count) {
        for (int i = 0; i < count; i++) {
            testMapper.selectOne();
        }
        testMapper.selectError();
//        try {
//            testMapper.selectError();
//        } catch (Exception e) {
//            // Ignore to let the filter finish normally
//        }
        return "Executed " + count + " queries and one error query";
    }

    @GetMapping("/test-truncate")
    public String testTruncate() {
        testMapper.selectLongText();
        return "Truncate test";
    }
}
