package com.socyno.webbsc.authority;


public interface AuthoritySpecialRejecter {
    public boolean check(Object scopeSource) throws Exception;
}
