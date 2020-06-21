package com.socyno.webbsc.interceptor;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.IOUtils;
import org.springframework.web.util.ContentCachingRequestWrapper;

import com.socyno.base.bscmixutil.StringUtils;
import com.socyno.webbsc.ctxutil.ContextUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MvcRequestContentCachingWrapper extends ContentCachingRequestWrapper {
    
    private BufferedReader reader;
    
    private ServletInputStream inputStream;
    
    public MvcRequestContentCachingWrapper(HttpServletRequest request) throws IOException {
        super(request);
        loadBody(request);
    }
    
    private void loadBody(HttpServletRequest request) throws IOException {
        if (!StringUtils.containsIgnoreCase(request.getContentType(), "multipart/form-data")) {
            byte[] data;
            super.getParameterMap();
            IOUtils.toByteArray(super.getInputStream());
            inputStream = new RequestCachingInputStream(data = super.getContentAsByteArray());
            if (log.isDebugEnabled() || ContextUtil.inDebugMode()) {
                log.info("Request body cache created : {} => {}", request.getRequestURI(), data);
            }
        }
    }
    
    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (inputStream != null) {
            if (log.isDebugEnabled() || ContextUtil.inDebugMode()) {
                log.info("Request body cache fetched : {}", getRequestURI());
            } 
            return inputStream;
        }
        return super.getInputStream();
    }
    
    @Override
    public BufferedReader getReader() throws IOException {
        if (inputStream != null) {
            if (reader == null) {
                reader = new BufferedReader(new InputStreamReader(inputStream, getCharacterEncoding()));
            }
            return reader;
        }
        return super.getReader();
    }
    
    private static class RequestCachingInputStream extends ServletInputStream {
        
        private final ByteArrayInputStream inputStream;
        
        public RequestCachingInputStream(byte[] bytes) {
            inputStream = new ByteArrayInputStream(bytes);
        }
        
        @Override
        public int read() throws IOException {
            return inputStream.read();
        }
        
        @Override
        public void reset() throws IOException {
            inputStream.reset();
        }
        
        @Override
        public void mark(int readlimit) {
            inputStream.mark(readlimit);
        }
        
        @Override
        public int available() throws IOException {
            return inputStream.available();
        }
        
        @Override
        public void close() throws IOException {
            inputStream.close();
        }
        
        @Override
        public int read(byte b[], int off, int len) {
            return inputStream.read(b, off, len);
        }
        
        @Override
        public boolean markSupported() {
            return inputStream.markSupported();
        }
        
        @Override
        public long skip(long n) {
            return inputStream.skip(n);
        }
    }
}
