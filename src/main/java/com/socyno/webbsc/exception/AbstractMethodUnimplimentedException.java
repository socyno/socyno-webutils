package com.socyno.webbsc.exception;

import com.socyno.base.bscexec.MessageException;

public class AbstractMethodUnimplimentedException extends MessageException {
    
    private static final long serialVersionUID = 1L;
    
    public AbstractMethodUnimplimentedException() {
        super("未实现的抽象或静态方法调用。");
    }
    
}
