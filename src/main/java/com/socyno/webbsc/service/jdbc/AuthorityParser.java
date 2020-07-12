package com.socyno.webbsc.service.jdbc;

import com.socyno.webbsc.authority.AbstractAuthorityParser;
import com.socyno.webbsc.service.AbstractPermissionService;

public class AuthorityParser extends AbstractAuthorityParser {
    @Override
    protected AbstractPermissionService getPermissionService() {
        return PermissionService.getInstance();
    }
}
