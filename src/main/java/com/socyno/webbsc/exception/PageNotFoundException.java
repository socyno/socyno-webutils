package com.socyno.webbsc.exception;

import com.socyno.base.bscexec.HttpResponseException;

public class PageNotFoundException extends HttpResponseException {
    private static final long serialVersionUID = 1L;

    public PageNotFoundException() {
        super(404, "Page Not Found.");
    }
}
