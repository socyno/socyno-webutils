package com.socyno.webbsc.service.jdbc;

import com.socyno.webbsc.authority.AbstractAuthorityChecker;
import com.socyno.webbsc.service.AbstractPermissionService;

public class AuthorityChecker extends AbstractAuthorityChecker {

    @Override
    protected AbstractPermissionService getPermissionService() {
        return PermissionService.getInstance();
    }
    
}
