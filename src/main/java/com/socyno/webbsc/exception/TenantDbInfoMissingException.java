package com.socyno.webbsc.exception;

import com.socyno.base.bscexec.MessageException;

public class TenantDbInfoMissingException extends MessageException {
    private static final long serialVersionUID = 1L;

    public TenantDbInfoMissingException(String tenant, String dbInfoName){
        super(String.format("租户(%s)数据连接名称(%s)未定义！", tenant, dbInfoName));
    }

}
