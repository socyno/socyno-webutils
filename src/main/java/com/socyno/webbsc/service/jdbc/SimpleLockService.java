package com.socyno.webbsc.service.jdbc;

import com.socyno.base.bscservice.AbstractLockService;
import com.socyno.base.bscsqlutil.AbstractDao;

import lombok.Getter;

public class SimpleLockService extends AbstractLockService {
    
    protected AbstractDao getDao() {
        return TenantSpecialDataSource.getMain();
    }
    
    @Getter
    private final static AbstractLockService instance = new SimpleLockService();
    
}
