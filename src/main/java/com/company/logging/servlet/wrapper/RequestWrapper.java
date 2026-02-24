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

    private final byte[] cachedBody;

    public RequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        // 요청 본문을 바이트 배열로 읽어 캐싱합니다.
        cachedBody = request.getInputStream().readAllBytes();
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