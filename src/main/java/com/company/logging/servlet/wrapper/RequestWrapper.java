package com.company.logging.servlet.wrapper;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * HttpServletRequest를 래핑하여 요청 본문(Body)을 여러 번 읽을 수 있도록 하는 클래스입니다.
 * 기본 서블릿 스트림은 한 번 읽으면 소모되므로, 로깅을 위해 본문을 캐싱해둡니다.
 */
public class RequestWrapper extends HttpServletRequestWrapper {

    // readAllBytes()는 크기 제한이 없어 대용량 바디 수신 시 OOM을 유발할 수 있음
    // PG API 특성상 JSON 바디가 1MB를 초과하는 경우는 비정상 요청으로 간주하고 캐싱을 중단
    private static final int MAX_BODY_CACHE_BYTES = 1024 * 1024;

    private final byte[] cachedBody;

    public RequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        cachedBody = request.getInputStream().readNBytes(MAX_BODY_CACHE_BYTES);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        final ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);
        return new ServletInputStream() {
            @Override
            public int read() throws IOException {
                return bais.read();
            }

            @Override
            public boolean isFinished() {
                return bais.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }


    public String getBody(){
        return new String(cachedBody, StandardCharsets.UTF_8);
    }
}