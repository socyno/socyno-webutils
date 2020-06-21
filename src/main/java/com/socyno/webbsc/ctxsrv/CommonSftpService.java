package com.socyno.webbsc.ctxsrv;

import com.socyno.base.bscservice.AbstractSftpService;
import com.socyno.webbsc.ctxutil.ContextUtil;

import lombok.Getter;

public class CommonSftpService {
    
    @Getter
    private final static AbstractSftpService Default = new AbstractSftpService() {
        
        @Override
        protected String getHostname() {
            return ContextUtil.getConfigTrimed("system.sftp.common.host.name");
        }
        
        @Override
        protected String getUsername() {
            return ContextUtil.getConfigTrimed("system.sftp.common.host.user");
        }
        
        @Override
        protected String getPassword() {
            return ContextUtil.getConfigTrimed("system.sftp.common.host.password");
        }
        
        @Override
        protected String getHttpUrl() {
            return ContextUtil.getConfigTrimed("system.sftp.common.host.httpurl");
        }
        
        @Override
        protected String toHttpUrl(String path) {
            if (path == null) {
                path = "";
            }
            if (path.startsWith(getRootDir())) {
                path = path.substring(getRootDir().length());
            }
            return String.format("%s/%s", getHttpUrl(), path).replace("\\", "/");
        }
        
        @Override
        protected String getRootDir() {
            return "/data/";
        }
    };
}
