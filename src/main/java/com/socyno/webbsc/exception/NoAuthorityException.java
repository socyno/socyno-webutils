package com.socyno.webbsc.exception;

import com.socyno.base.bscexec.HttpResponseException;

public class NoAuthorityException extends HttpResponseException {
    
    private static final long serialVersionUID = 1L;
    
    public NoAuthorityException() {
        this("No Authority Error");
    }
    
    public NoAuthorityException(String message) {
        super(403, message);
    }
}
