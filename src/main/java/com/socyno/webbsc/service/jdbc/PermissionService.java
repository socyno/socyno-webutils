package com.socyno.webbsc.service.jdbc;

import com.socyno.base.bscmixutil.ConvertUtil;
import com.socyno.base.bscmixutil.StringUtils;
import com.socyno.base.bscmodel.ObjectMap;
import com.socyno.base.bscmodel.SessionContext;
import com.socyno.base.bscsqlutil.AbstractDao;
import com.socyno.base.bscsqlutil.SqlQueryUtil;
import com.socyno.base.bscsqlutil.AbstractDao.ResultSetProcessor;
import com.socyno.webbsc.authority.AuthorityEntity;
import com.socyno.webbsc.authority.AuthorityScope;
import com.socyno.webbsc.ctxutil.ContextUtil;
import com.socyno.webbsc.service.AbstractPermissionService;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.adrianwalker.multilinestring.Multiline;
import org.apache.commons.lang3.ArrayUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.*;

@Slf4j
public class PermissionService extends AbstractPermissionService {
    
    @Getter
    private final static PermissionService instance = new PermissionService();
    
    private AbstractDao getDao() {
        return TenantSpecialDataSource.getMain();
    }
    
    /**
        SELECT DISTINCT
            f.feature_id
        FROM
            system_user_scope_role s,
            system_role r,
            system_role_feature f 
        WHERE
            s.user_id = ?
        AND
            r.id = s.role_id 
        AND
            r.state_form_status = 'enabled'
        AND
            r.id = f.role_id
    **/
    @Multiline
    private static final String SQL_QUERY_USER_SYSTEM_FEATURES = "X";
    
    @Override
    public String[] getMyAuths() throws Exception {
        List<Long> tenantFeatures = getDao().queryAsList(Long.class, SQL_QUERY_USER_SYSTEM_FEATURES,
                new Object[] { SessionContext.getUserId() });
        if (tenantFeatures == null || tenantFeatures.size() <= 0) {
            return new String[0];
        }
        return FeatureBasicService.getInstance().getTenantAuths(SessionContext.getTenant(),
                tenantFeatures.toArray(new Long[0]));
    }
    
    @Override
    public long[] queryMyScopeTargetIdsByAuthKey(String authKey, String authScope) throws Exception {
        /**
         * 确认当前用户、授权码及授权范围是否存在，以及当前租户是否被授予此授权码
         */
        AuthorityScope authScopeEntity;
        if (!SessionContext.hasUserSession() || StringUtils.isBlank(authKey)
                || (authScopeEntity = getProvidedAuthorityScope(authScope)) == null
                || !FeatureBasicService.getInstance().checkTenantAuth(SessionContext.getTenant(), authKey)) {
            return new long[0];
        }
        /**
         * 针对管理员(无论租户管理员，还是超级管理员)
         */
        if (SessionContext.isAdmin()) {
            return null;
        }
        Map<AuthorityScope, List<Long>> allAuthTargets;
        if ((allAuthTargets = queryAuthScopeTargetsByKey(authKey, false, null)) == null) {
            return new long[0];
        }
        /**
         * 如果无需做授权标的检查，那么则意味着对租户下的所有业务系统拥有该授权 */
        for (AuthorityScope scope : allAuthTargets.keySet()) {
            if (scope != null && scope.isSystem()) {
                return null;
            }
        }
        List<Long> scopeTargetIds;
        if ((scopeTargetIds = allAuthTargets.get(authScopeEntity)) == null) {
            return new long[0];
        }
        return ConvertUtil.asNonNullUniquePrimitiveLongArray(scopeTargetIds.toArray(new Long[0]));
    }
    
    /**
     * SELECT DISTINCT
     *     s.scope_type,
     *     s.scope_id
     * FROM
     *     system_user_scope_role s,
     *     system_role_feature f
     * WHERE
     *     s.role_id = f.role_id
     * AND
     *     s.user_id = ?
     * AND
     *     f.feature_id in (%s)
     *     %s
     */
    @Multiline
    private final String SQL_QUERY_AUTH_SCOPE_WITH_FEATURES = "X";
    
    private Map<AuthorityScope, List<Long>> queryAuthScopeTargetsByKey(String authKey, boolean checked, String authScope)
            throws Exception {
        if (!SessionContext.hasUserSession()) {
            return Collections.emptyMap();
        }
        long[] featureIds;
        if ((featureIds = FeatureBasicService.getInstance().getAuthTenantFeatures(SessionContext.getTenant(),
                authKey)) == null || featureIds.length <= 0) {
            return Collections.emptyMap();
        }
        
        StringBuffer sqlExtra = new StringBuffer();
        if (authScope != null) {
            sqlExtra.append(" AND s.scope_type = '").append(authScope).append("'");
        }
        if (checked) {
            sqlExtra.append(" LIMIT 1");
        }
        List<Map<String, Object>> scopeTargetList = getDao().queryAsList(
                String.format(SQL_QUERY_AUTH_SCOPE_WITH_FEATURES, StringUtils.join("?", featureIds.length, ","),
                                    sqlExtra.toString()),
                ArrayUtils.addAll(new Object[] { SessionContext.getUserId() }, featureIds));
        List<Long> scopeTargetIds;
        AuthorityScope scopeTargetType;
        Map<AuthorityScope, List<Long>> result = new HashMap<>();
        for (Map<String, Object> s : scopeTargetList) {
            if ((scopeTargetType = getProvidedAuthorityScope((String) s.get("scope_type"))) == null) {
                continue;
            }
            if ((scopeTargetIds = result.get(scopeTargetType)) == null) {
                result.put(scopeTargetType, scopeTargetIds = new ArrayList<Long>());
            }
            scopeTargetIds.add((Long) s.get("scope_id"));
        }
        return result;
    }
    
    @Override
    public boolean hasPermission(String authKey, String authScope, Long targetId) throws Exception {
        /* 确认用户信息是否存在，以及授权码是否为空 */
        AuthorityScope authScopeEntity;
        if (!SessionContext.hasUserSession() || StringUtils.isBlank(authKey)
                || (authScopeEntity = getProvidedAuthorityScope(authScope)) == null) {
            return false;
        }
        /* 只要认证用户，即可执行 Guest 授权的操作 */
        if (authScopeEntity.isGuest()) {
            return true;
        }
        /* 超级系统管理员，直接给与授权 */
        if (SessionContext.isAdmin() && TenantBasicService.getInstance().inSuperTenantContext()) {
            return true;
        }
        boolean result = false;
        if (FeatureBasicService.getInstance().checkTenantAuth(SessionContext.getTenant(), authKey)) {
            /* 管理员给予所有授权 */
            if (SessionContext.isAdmin()) {
                result = true;
            } else {
                long[] scopeTargatIds;
                if ((scopeTargatIds = queryMyScopeTargetIdsByAuthKey(authKey, authScope)) == null
                        || (targetId != null && ArrayUtils.contains(scopeTargatIds, targetId))) {
                    result = true;
                }
            }
        }
        if (ContextUtil.inDebugMode()) {
            log.info("Check permission(auth = {}) for user(admin = {}, username = {}) : result = {} ", authKey,
                    SessionContext.isAdmin(), SessionContext.getUsername(), result);
        }
        return result;
    }
    
    @Override
    public boolean hasAnyPermission(String authKey) throws Exception {
        return hasAnyPermission(authKey, null);
    }
    
    @Override
    public boolean hasAnyPermission(String authKey, String authScope) throws Exception {
        /* 确认用户信息是否存在，以及授权码是否为空 */
        if (!SessionContext.hasUserSession() || StringUtils.isBlank(authKey)) {
            return false;
        }
        AuthorityScope authScopeEntity = null;
        if (authScope != null) {
            if ((authScopeEntity = getProvidedAuthorityScope(authScope)) == null) {
                return false;
            }
        }
        /* 只要认证用户，即可执行 Guest 授权的操作 */
        if (authScopeEntity != null && authScopeEntity.isGuest()) {
            return true;
        }
        /* 超级系统管理员，直接给与授权 */
        if (SessionContext.isAdmin() && TenantBasicService.getInstance().inSuperTenantContext()) {
            return true;
        }
        /* 授权码不存在 */
        if (!FeatureBasicService.getInstance().checkTenantAuth(SessionContext.getTenant(), authKey)) {
            return false;
        }
        /* 针对管理员,直接给与授权 */
        if (SessionContext.isAdmin()) {
            return true;
        }
        if (authScopeEntity == null || authScopeEntity.isSubsystem()) {
            return !queryAuthScopeTargetsByKey(authKey, true, null).isEmpty();
        }
        return !queryAuthScopeTargetsByKey(authKey, true, authScopeEntity.getName()).isEmpty();
    }
    
    @Override
    public synchronized void saveAuthorityEntitisForConfig(String group, Collection<AuthorityEntity> entitis) throws Exception {
        final AbstractDao dao = ContextUtil.getBaseDataSource();
        dao.executeTransaction(new ResultSetProcessor() {
            @Override
            public void process(ResultSet arg0, Connection arg1) throws Exception {
                dao.executeUpdate("UPDATE system_auth SET deleted_at = NOW()"
                        + " WHERE app_name = ? AND group_name = ? AND deleted_at IS NULL",
                        new Object[] { ContextUtil.getAppName(), group });
                List<ObjectMap> kvPairs = new ArrayList<>();
                for (AuthorityEntity auth : entitis) {
                    kvPairs.add(new ObjectMap()
                        .put("auth", auth.getAuth())
                        .put("scope", auth.getScope())
                        .put("deleted_at", null)
                        .put("group_name", group)
                        .put("app_name", ContextUtil.getAppName())
                    );
                }
                dao.executeUpdate(SqlQueryUtil.pairs2InsertQuery(
                    "system_auth", kvPairs,
                    new ObjectMap().put("deleted_at", null)
                ));
            }
        });
    }
    
    /**
     * SELECT DISTINCT
     *     s.scope_type,
     *     s.user_id
     * FROM
     *     system_user_scope_role s,
     *     system_role_feature f
     * WHERE
     *     s.role_id = f.role_id
     * AND
     *     s.scope_id in (%s)
     * AND
     *     f.feature_id in (%s)
     */
    @Multiline
    private final String SQL_QUERY_AUTHED_USERS_WITH_FEATURES = "X";
    
    private Map<AuthorityScope, List<Long>> queryAuthedUserIdsByKey(String authKey, long... targetIds)
            throws Exception {
        if (!SessionContext.hasUserSession() || StringUtils.isBlank(authKey) || targetIds == null
                || targetIds.length <= 0) {
            return Collections.emptyMap();
        }
        long[] featureIds;
        if ((featureIds = FeatureBasicService.getInstance().getAuthTenantFeatures(SessionContext.getTenant(),
                authKey)) == null || featureIds.length <= 0) {
            return Collections.emptyMap();
        }
        List<Map<String, Object>> scopeTargetUsers = getDao()
                .queryAsList(String.format(SQL_QUERY_AUTHED_USERS_WITH_FEATURES, StringUtils.join(targetIds, ','),
                        StringUtils.join(featureIds, ',')));
        List<Long> scopeTargetUserIds;
        AuthorityScope scopeTargetType;
        Map<AuthorityScope, List<Long>> result = new HashMap<>();
        for (Map<String, Object> s : scopeTargetUsers) {
            if ((scopeTargetType = getProvidedAuthorityScope((String) s.get("scope_type"))) == null) {
                continue;
            }
            if ((scopeTargetUserIds = result.get(scopeTargetType)) == null) {
                result.put(scopeTargetType, scopeTargetUserIds = new ArrayList<Long>());
            }
            scopeTargetUserIds.add((Long) s.get("user_id"));
        }
        return result;
    }
    
    @Override
    public long[] queryAuthedUserIds(String authKey, String authScope, long... targetIds) throws Exception {
        AuthorityScope authorityScope;
        if ((authorityScope = getProvidedAuthorityScope(authScope)) == null) {
            return new long[0];
        }
        if (authorityScope.isSystem()) {
            targetIds = new long[] { 0 };
        }
        Map<AuthorityScope, List<Long>> authedAllUserIds = queryAuthedUserIdsByKey(authKey, targetIds);
        if ((authedAllUserIds = queryAuthedUserIdsByKey(authKey, targetIds)) == null || authedAllUserIds.isEmpty()) {
            return new long[0];
        }
        List<Long> authedUserIds;
        if ((authedUserIds = authedAllUserIds.get(authorityScope)) == null) {
            authedUserIds = new LinkedList<>();
        }
        if (authorityScope.isSubsystem()) {
            List<Long> authedScopeUserIds;
            for (AuthorityScope scope : getAllAuthorityScopes()) {
                if (scope.isSystem() && (authedScopeUserIds = authedAllUserIds.get(scope)) != null) {
                    authedUserIds.addAll(authedScopeUserIds);
                }
            }
        }
        return ConvertUtil.asNonNullUniquePrimitiveLongArray(authedUserIds.toArray(new Long[0]));
    }
}
