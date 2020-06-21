package com.socyno.webbsc.exception;

import com.socyno.base.bscexec.MessageException;

public class TenantDbInfoNotFoundException extends MessageException {
    private static final long serialVersionUID = 1L;

    public TenantDbInfoNotFoundException(String tenant, String dbInfoName){
        super(String.format("租户(%s)数据连接(%s)未注册！", tenant, dbInfoName));
    }

}
