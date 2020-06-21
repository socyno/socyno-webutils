package com.socyno.webbsc.exception;

import com.socyno.base.bscexec.MessageException;

public class TenantDbInfoConnectException extends MessageException {
    private static final long serialVersionUID = 1L;

    public TenantDbInfoConnectException(String tenant, String dbInfoName){
        super(String.format("租户(%s)数据连接(%s)初始化失败！", tenant, dbInfoName));
    }

}
