package com.socyno.webbsc.service.jdbc;

import org.adrianwalker.multilinestring.Multiline;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.socyno.base.bscmixutil.ConvertUtil;
import com.socyno.base.bscsqlutil.AbstractDao;
import com.socyno.webbsc.ctxutil.ContextUtil;
import com.socyno.webbsc.service.AbstractFeatureService;

import lombok.Getter;

public class FeatureBasicService implements AbstractFeatureService {
    
    @Getter
    private final static FeatureBasicService instance = new FeatureBasicService();
    
    private AbstractDao getDao() {
        return ContextUtil.getBaseDataSource();
    }
    
    /**
     * SELECT DISTINCT
     *     a.feature_id 
     * FROM
     *     system_feature_auth a,
     *     system_tenant_feature f,
     *     system_tenant t
     * WHERE
     *     f.feature_id = a.feature_id
     * AND
     *     t.id = f.tenant_id
     * AND
     *     t.code = ?
     * AND
     *     a.auth_key IN (%s)
     */
    @Multiline
    private static final String SQL_QUERY_AUTH_FEATURES = "X";
    
    /**
     * 包含指定接口或操作的功能列表
     */
    @Override
    public long[] getAuthTenantFeatures(String tenant, String ...authKeys) throws Exception {
        if (authKeys == null || authKeys.length <= 0 || StringUtils.isBlank(tenant)) {
            return new long[0];
        }
        return ConvertUtil.asNonNullUniquePrimitiveLongArray(getDao().queryAsList(Long.class,
                String.format(SQL_QUERY_AUTH_FEATURES, StringUtils.join("?", authKeys.length, ",")),
                ArrayUtils.addAll(new Object[] { tenant }, (Object[])authKeys)));
    }
    
    /**
     * SELECT DISTINCT
     *     f.feature_id 
     * FROM
     *     system_feature f,
     *     system_tenant  t,
     *     system_tenant_feature tf
     * WHERE
     *     t.id = tf.tenant_id
     * AND
     *     f.id = tf.feature_id
     * AND
     *     t.code = ?
     */
    @Multiline
    private static final String SQL_QUERY_TENANT_FEATURES = "X";
    
    @Override
    public long[] getTenantFeatures(String tenant) throws Exception {
        if (StringUtils.isBlank(tenant)) {
            return new long[0];
        }
        return ConvertUtil.asNonNullUniquePrimitiveLongArray(
                getDao().queryAsList(Long.class, SQL_QUERY_TENANT_FEATURES, new Object[] { tenant }));
    }
    
    /**
     * SELECT DISTINCT
     *     a.auth_key 
     * FROM
     *     system_feature f,
     *     system_tenant  t,
     *     system_tenant_feature tf,
     *     system_feature_auth a
     * WHERE
     *     t.id = tf.tenant_id
     * AND
     *     f.id = tf.feature_id
     * AND
     *     a.feature_id = f.id
     * AND
     *     t.code = ?
     */
    @Multiline
    private static final String SQL_QUERY_TENANT_AUTHS = "X";
    
    @Override
    public String[] getTenantAuths(String tenant, Long... features) throws Exception {
        if (StringUtils.isBlank(tenant) || features == null || features.length <= 0) {
            return new String[0];
        }
        return getDao().queryAsList(String.class,
                String.format("%s AND tf.feature_id IN (%s)", SQL_QUERY_TENANT_AUTHS,
                        StringUtils.join("?", features.length, ",")),
                ArrayUtils.addAll(new Object[] { tenant }, (Object[]) features)
        ).toArray(new String[0]);
    }
    
    @Override
    public String[] getTenantAllAuths(String tenant) throws Exception {
        if (StringUtils.isBlank(tenant)) {
            return new String[0];
        }
        return getDao().queryAsList(String.class, SQL_QUERY_TENANT_AUTHS, new Object[] { tenant })
                .toArray(new String[0]);
    }
    
    @Override
    public boolean checkTenantAuth(String tenant, String authKey) throws Exception {
        if (StringUtils.isBlank(tenant) || StringUtils.isBlank(authKey)) {
            return false;
        }
        return getDao().queryAsList(String.class, String.format("%s AND a.auth_key = ?", SQL_QUERY_TENANT_AUTHS),
                new Object[] { tenant, authKey }).size() > 0;
    }
}
