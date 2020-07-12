package com.socyno.webbsc.service.jdbc;

import java.sql.Connection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.sql.DataSource;

import org.apache.commons.dbcp2.BasicDataSource;

import com.socyno.base.bscexec.MessageException;
import com.socyno.base.bscexec.TenantMissingException;
import com.socyno.base.bscmixutil.StringUtils;
import com.socyno.base.bscmodel.SessionContext;
import com.socyno.base.bscsqlutil.AbstractDao;
import com.socyno.webbsc.exception.TenantDbInfoConnectException;
import com.socyno.webbsc.exception.TenantDbInfoMissingException;
import com.socyno.webbsc.exception.TenantDbInfoNotFoundException;
import com.socyno.webbsc.model.SystemTenantDbInfo;
import com.socyno.webbsc.service.SimpleEncryptService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class TenantSpecialDataSource extends TenantMetaDataSource {
    
    private TenantSpecialDataSource() {
        
    }
    
    protected abstract String getDbInfoName();
    
    private static final TenantSpecialDataSource MainDataSource = new TenantSpecialDataSource() {
        @Override
        protected String getDbInfoName() {
            return SystemTenantDbInfo.TYPES.main.name();
        }
    };
    
    public static AbstractDao getMain() {
        return MainDataSource;
    }
    
    private static final Map<String, DataSource> tenantDataSources = new ConcurrentHashMap<>();
    
    @Override
    public DataSource getDataSource() {
        String tenant;
        if (StringUtils.isBlank(tenant = SessionContext.getTenant())) {
            throw new TenantMissingException();
        }
        String dbInfoName;
        if (StringUtils.isBlank(dbInfoName = getDbInfoName())) {
            throw new TenantDbInfoMissingException(tenant, dbInfoName);
        }
        /**
         * 当访问超级租户的主数据库时，直接返回元数据数据库
         */
        if (TenantBasicService.getInstance().inSuperTenantContext() && SystemTenantDbInfo.TYPES.main.name().equals(dbInfoName)) {
            return TenantBasicService.getInstance().getDao().getDataSource();
        }
        if (!tenantDataSources.containsKey(tenant)) {
            synchronized (this.getClass()) {
                if (!tenantDataSources.containsKey(tenant)) {
                    SystemTenantDbInfo sysTenantDb = null;
                    try {
                        if ((sysTenantDb = TenantBasicService.getInstance().getTenantDatabaseWithToken(tenant, dbInfoName)) == null) {
                            throw new TenantDbInfoNotFoundException(tenant, dbInfoName);
                        }
                        tenantDataSources.put(tenant, getDbcp2DataSource(sysTenantDb));
                    } catch (MessageException e) {
                        throw e;
                    } catch (Throwable e) {
                        log.error(e.toString(), e);
                        throw new TenantDbInfoConnectException(tenant, dbInfoName);
                    }
                }
            }
        }
        return tenantDataSources.get(tenant);
    }
    
    private DataSource getDbcp2DataSource(SystemTenantDbInfo tenantDb) throws Exception {
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setUrl(tenantDb.getJdbcUrl());
        dataSource.setUsername(tenantDb.getJdbcUser());
        if (StringUtils.isNotBlank(tenantDb.getJdbcToken())) {
            dataSource.setPassword(
                    new String(SimpleEncryptService.getDefault().decryptFromBase64(tenantDb.getJdbcToken()), "UTF-8"));
        }
        dataSource.setDriverClassName(tenantDb.getJdbcDriver());
        // 初始连接数量
        dataSource.setInitialSize(1);
        // 最小空闲连接数
        dataSource.setMinIdle(1);
        // 最大空闲连接数
        dataSource.setMaxIdle(5);
        // 最大活动连接数
        dataSource.setMaxTotal(10);
        // 最大连接等待时间（单位：毫秒）
        dataSource.setMaxWaitMillis(60000);
        
        // 确保连接信息正确
        Connection conn = null;
        try {
            conn = dataSource.getConnection();
        } finally {
            if (conn != null) {
                conn.close();
            }
        }
        return dataSource;
    }
}