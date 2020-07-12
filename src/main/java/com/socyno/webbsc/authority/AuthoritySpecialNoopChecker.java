package com.socyno.webbsc.authority;


public class AuthoritySpecialNoopChecker implements AuthoritySpecialChecker {
    public boolean check(Object scopeSource) {
        return false;
    }
}
