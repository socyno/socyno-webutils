package com.socyno.webbsc.authority;


public class AuthoritySpecialNoopRejecter implements AuthoritySpecialRejecter {
    public boolean check(Object scopeSource) {
        return false;
    }
}
