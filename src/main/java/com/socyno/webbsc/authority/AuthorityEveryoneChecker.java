package com.socyno.webbsc.authority;

import com.socyno.base.bscmodel.SessionContext;

public class AuthorityEveryoneChecker implements AuthoritySpecialChecker {

    @Override
    public boolean check(Object scopeSource) throws Exception {
        return SessionContext.getUserId() != null;
    }
}
