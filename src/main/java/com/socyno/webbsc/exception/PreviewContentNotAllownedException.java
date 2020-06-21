package com.socyno.webbsc.exception;

import com.socyno.base.bscexec.HttpResponseException;

public class PreviewContentNotAllownedException extends HttpResponseException {
    private static final long serialVersionUID = 1L;

    public PreviewContentNotAllownedException() {
        super(500, "Preview File Content Type Not Allowned.");
    }
}
