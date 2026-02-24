package com.company.logging.tool.servlet.wrapper;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * HttpServletResponse를 래핑하여 응답 본문을 캡처(Capture)할 수 있도록 하는 클래스입니다.
 * 클라이언트로 전송되는 데이터를 가로채어 로깅에 사용하기 위해 메모리에 복사해둡니다.
 */
public class ResponseWrapper extends HttpServletResponseWrapper {

    private static final int MAX_CAPTURE_SIZE = 1024 * 1024; // 1MB 제한
    private final ByteArrayOutputStream capture = new ByteArrayOutputStream();
    private ServletOutputStream outputStream;
    private PrintWriter writer;

    public ResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {

        if (writer != null) {
            throw new IllegalStateException("getWriter() already called");
        }

        if (outputStream == null) {
            ServletOutputStream original = super.getOutputStream();

            outputStream = new ServletOutputStream() {

                @Override
                public void write(int b) throws IOException{
                    original.write(b);
                    // 캡처 용량 제한을 넘지 않는 선에서 데이터 복사
                    if(capture.size() < MAX_CAPTURE_SIZE){
                        capture.write(b);
                    }
                }

                @Override
                public boolean isReady() {
                    return original.isReady();
                }


                @Override
                public void setWriteListener(WriteListener writeListener) {
                    original.setWriteListener(writeListener);
                }
            };
        }
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {

        if (outputStream != null) {
            throw new IllegalStateException("getOutputStream() already called");
        }

        if (writer == null) {
            Charset charset = Charset.forName(getCharacterEncoding());

            PrintWriter originalWriter = super.getWriter();

            writer = new PrintWriter(new OutputStreamWriter(capture, charset)) {

                @Override
                public void write(int c) {
                    originalWriter.write(c);
                    if (capture.size() < MAX_CAPTURE_SIZE) {
                        super.write(c);
                    }
                }

                @Override
                public void write(char[] buf, int off, int len) {
                    originalWriter.write(buf, off, len);
                    if (capture.size() < MAX_CAPTURE_SIZE) {
                        super.write(buf, off, len);
                    }
                }

                @Override
                public void write(String s, int off, int len) {
                    originalWriter.write(s, off, len);
                    if (capture.size() < MAX_CAPTURE_SIZE) {
                        super.write(s, off, len);
                    }
                }

                @Override
                public void flush() {
                    originalWriter.flush();
                    super.flush();
                }

                @Override
                public void close() {
                    originalWriter.close();
                    super.close();
                }
            };
        }

        return writer;
    }

    /**
     * 캡처된 응답 본문의 원시 바이트를 반환합니다.
     */
    public byte[] getBody() {
        return capture.toByteArray();
    }

    /**
     * 캡처된 응답 본문을 UTF-8 문자열로 반환합니다.
     */
    public String getBodyAsString() {
        return capture.toString(StandardCharsets.UTF_8);
    }
}
