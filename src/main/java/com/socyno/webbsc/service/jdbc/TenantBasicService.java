package com.socyno.webbsc.service.jdbc;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.adrianwalker.multilinestring.Multiline;

import com.socyno.base.bscexec.MessageException;
import com.socyno.base.bscmixutil.ClassUtil;
import com.socyno.base.bscmixutil.ConvertUtil;
import com.socyno.base.bscmixutil.StringUtils;
import com.socyno.base.bscmodel.AbstractUser;
import com.socyno.base.bscmodel.ObjectMap;
import com.socyno.base.bscmodel.SessionContext;
import com.socyno.base.bscsqlutil.AbstractDao;
import com.socyno.base.bscsqlutil.AbstractDao.ResultSetProcessor;
import com.socyno.base.bscsqlutil.SqlQueryUtil;
import com.socyno.webbsc.ctxutil.ContextUtil;
import com.socyno.webbsc.model.SystemTenantDbInfo;
import com.socyno.webbsc.model.SystemTenantDbInfoWithId;

import lombok.Getter;

public class TenantBasicService {
    
    @Getter
    private static final TenantBasicService instance = new TenantBasicService();
    
    public static final String CONFIG_SUPER_TENANT_KEY = "system.tenant.super.code";
    
    public AbstractDao getDao() {
        return ContextUtil.getBaseDataSource();
    }
    
    public String getSuperTenant() {
        return StringUtils.ifBlank(ContextUtil.getConfigTrimed(CONFIG_SUPER_TENANT_KEY), "socyno.super");
    }
    
    /**
     * 确认给定的租户是否为超级租户
     */
    public boolean equalsSuperTenant(String tenantCode) {
        return StringUtils.isNotBlank(tenantCode) && getSuperTenant().equals(tenantCode);
    }
    
    /**
     * 确认当前位于超级管理租户*代理*上下文
     */
    public boolean inSuperTenantProxyContext() {
        if (StringUtils.isBlank(SessionContext.getProxyUsername())) {
            return false;
        }
        return equalsSuperTenant(AbstractUser.parseTenantFromUsername(SessionContext.getProxyUsername()));
    }
    
    /**
     * 确认当前位于超级管理租户上下文
     */
    public boolean inSuperTenantContext() {
        return equalsSuperTenant(SessionContext.getTenantOrNull());
    }
    
    /**
         SELECT
             COUNT(1)
         FROM
             system_tenant t
         WHERE
             t.code = ?
         AND
             t.state_form_status = 'enabled'
         LIMIT 1
     */
    @Multiline
    private static final String SQL_CHECK_TENANT_ENABLED = "X";
    
    /**
     * 确认租户是否存在
     */
    public boolean checkTenantEnabled(String tenantCode) throws Exception {
        return getDao().queryAsObject(Long.class, SQL_CHECK_TENANT_ENABLED,
                new Object[] { tenantCode }) > 0;
    }
    
    /**
     *  INSERT IGNORE INTO system_tenant_feature (
     *      tenant_id,
     *      feature_id
     *  )   SELECT
     *          t.id,
     *          f.id
     *      FROM
     *          system_tenant t,
     *          system_feature f
     *      WHERE
     *          t.code = ?
     *      AND 
     *          f.id IS NOT NULL
     */
    @Multiline
    private static final String SQL_GRANT_TENANT_ALL_FEATURES = "X";
    
    /**
     * 创建租户基本信息
     */
    public void createTenantIfMissing(String code, String name, boolean asAllFeatures) throws Exception {
        getDao().executeUpdate(SqlQueryUtil.prepareInsertQuery("system_tenant",
                new ObjectMap().put("=code", code).put("name", name)));
        if (asAllFeatures) {
            getDao().executeUpdate(SQL_GRANT_TENANT_ALL_FEATURES, new Object[] {code});
        }
    }
    
    /**
         SELECT
             d.*
         FROM
             system_tenant_dbinfo d,
             system_tenant t
         WHERE
             t.id = d.tenant_id
         AND
             d.name = ?
         AND 
             t.id > 0
         AND 
             (t.code = ? OR t.id = ?)
     */
    @Multiline
    private static final String SQL_QUERY_NAMED_DBINFO = "X";
    
    /**
         SELECT
             d.*
         FROM
             system_tenant_dbinfo d
         WHERE
             d.tenant_id IN (%s)
     */
    @Multiline
    private static final String SQL_QUERY_MUTITENANT_DBINFOS = "X";
    
    /**
     * 获取指定的数据库信息
     */

    public SystemTenantDbInfo getTenantDatabaseWithToken(long tenantId, String dataName) throws Exception {
        return getTenantDatabaseWithToken("" + tenantId, dataName);
    }
    
    public SystemTenantDbInfo getTenantDatabaseWithToken(String tenant, String dataName) throws Exception {
        if (StringUtils.isAnyBlank(tenant, dataName)) {
            return null;
        }
        List<SystemTenantDbInfo> result = getDao().queryAsList(SystemTenantDbInfo.class,
                SQL_QUERY_NAMED_DBINFO, new Object[] {dataName, tenant, tenant});
        if (result == null || result.size() != 1) {
            return null;
        }
        return result.get(0);
    }
    
    @SuppressWarnings("serial")
    public List<SystemTenantDbInfoWithId> getTenantDatabases(Long... tenantIds) throws Exception {
        Long[] tenantPrimitiveIds = ConvertUtil.asNonNullUniqueLongArray((Object[]) tenantIds);
        if (tenantPrimitiveIds == null || tenantPrimitiveIds.length <= 0) {
            return Collections.emptyList();
        }
        return getDao().queryAsList(SystemTenantDbInfoWithId.class,
                String.format(SQL_QUERY_MUTITENANT_DBINFOS, StringUtils.join("?", tenantPrimitiveIds.length, ",")),
                tenantPrimitiveIds, new HashMap<String, String>(){{
                    put("-jdbcToken",  "******");
                    put("-JDBC_TOKEN", "******");
                    put("-jdbc_token", "******");
                }});
    }
    
    public void addTenantDatabases(final long tenantId, final List<? extends SystemTenantDbInfo> databases) throws Exception {
        if (databases == null || databases.isEmpty()) {
            return;
        }
        Set<String> sameJdbcNames = new HashSet<>();
        final List<SystemTenantDbInfo> dbs = new ArrayList<>();
        for (SystemTenantDbInfo db : databases) {
            if (db == null) {
                continue;
            }
            ClassUtil.checkFormRequiredAndOpValue(db, true);
            if (sameJdbcNames.contains(db.getName())) {
                throw new MessageException(String.format("租户数据库记录存在重复(%s)！", db.getName()));
            }
            if (getTenantDatabaseWithToken(tenantId, db.getName()) != null) {
                throw new MessageException(String.format("租户数据库记录已存在(%s)！", db.getName()));
            }
            dbs.add(db);
            sameJdbcNames.add(db.getName());
        }
        getDao().executeTransaction(new ResultSetProcessor() {
            @Override
            public void process(ResultSet arg0, Connection arg1) throws Exception {
                for (SystemTenantDbInfo db : dbs) {
                    getDao().executeUpdate(SqlQueryUtil.prepareInsertQuery(
                            "system_tenant_dbinfo", new ObjectMap()
                                .put("tenant_id", tenantId)
                                .put("name", db.getName())
                                .put("jdbc_url", db.getJdbcUrl())
                                .put("jdbc_driver", db.getJdbcDriver())
                                .put("jdbc_user", StringUtils.trimToEmpty(db.getJdbcUser()))
                                .put("jdbc_token", StringUtils.trimToEmpty(db.getJdbcToken()))
                    ));
                }
            }
        });
    }

    public void delTenantDatabases(final long tenantId, final List<String> dbnames) throws Exception {
        if (dbnames == null || dbnames.isEmpty()) {
            return;
        }
        getDao().executeTransaction(new ResultSetProcessor() {
            @Override
            public void process(ResultSet arg0, Connection arg1) throws Exception {
                for (String dbname : dbnames) {
                    if (StringUtils.isBlank(dbname)) {
                        continue;
                    }
                    getDao().executeUpdate(SqlQueryUtil.prepareDeleteQuery(
                            "system_tenant_dbinfo", new ObjectMap()
                                .put("=tenant_id", tenantId)
                                .put("=name", dbname)
                    ));
                }
            }
        });
    }
}
