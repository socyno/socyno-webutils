package com.socyno.webbsc.exception;

import com.socyno.base.bscexec.HttpResponseException;

public class AuthorityScopeNotFoundException extends HttpResponseException {
    
    private static final long serialVersionUID = 1L;
    
    public AuthorityScopeNotFoundException() {
        this("Authrity Scope Not Found");
    }
    
    public AuthorityScopeNotFoundException(String message) {
        super(500, message);
    }
}
