package com.company.logging.wrapper;

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

public class ResponseWrapper extends HttpServletResponseWrapper {

    private static final int MAX_CAPTURE_SIZE = 1024 * 1024; // 1MB
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
            writer = new PrintWriter(new OutputStreamWriter(capture, charset));
        }
        return writer;
    }

    /**
     * response body raw bytes
     */
    public byte[] getBody() {
        return capture.toByteArray();
    }

    /**
     * response body as string
     */
    public String getBodyAsString() {
        return capture.toString(StandardCharsets.UTF_8);
    }
}