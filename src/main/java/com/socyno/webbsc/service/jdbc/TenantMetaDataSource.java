package com.socyno.webbsc.service.jdbc;

import com.socyno.base.bscsqlutil.AbstractDao;
import com.socyno.webbsc.ctxutil.ContextUtil;

public class TenantMetaDataSource extends AbstractDao {
    
    public TenantMetaDataSource() {
        super();
    }
    
    public TenantMetaDataSource(String propertiesFile) throws Exception {
        super(propertiesFile);
    }
    
    public boolean inDebugMode() {
        return super.inDebugMode() || ContextUtil.inDebugMode();
    }
    
    
    @Override
    public int getColumnMapperCase() {
        return SQL_COLUMN_MAPPER_CASE_LOWER;
    }
}
