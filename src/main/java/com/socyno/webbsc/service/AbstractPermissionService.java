package com.socyno.webbsc.service;


import com.socyno.webbsc.authority.AuthorityEntity;
import com.socyno.webbsc.authority.AuthorityScope;
import com.socyno.webbsc.authority.AuthorityScopeType;
import com.socyno.webbsc.authority.AuthorityScope.ScopeType;
import com.socyno.webbsc.exception.AuthorityScopeNotFoundException;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractPermissionService {
    
    @SuppressWarnings("serial")
    final static Map<String, AuthorityScope> DEFAULT_SCOPES = new HashMap<String, AuthorityScope>() {
        {
            put(AuthorityScopeType.Guest, new AuthorityScope(AuthorityScopeType.Guest, "匿名用户", ScopeType.GUEST));
            put(AuthorityScopeType.System, new AuthorityScope(AuthorityScopeType.System, "系统全局", ScopeType.SYSTEM));
            put(AuthorityScopeType.Subsystem, new AuthorityScope(AuthorityScopeType.Subsystem, "业务系统", ScopeType.SUBSYSTEM));
        }
    };
    
    /**
     * 获取预定义的授权范围清单
     */
    public AuthorityScope[] getAllAuthorityScopes() {
        return DEFAULT_SCOPES.values().toArray(new AuthorityScope[0]);
    }
    
    /**
     * 获取指定的授权范围定义（要求非空）
     */
    public final AuthorityScope getEnsuredAuthorityScope(String authScope) {
        AuthorityScope authorityScope;
        if ((authorityScope = getProvidedAuthorityScope(authScope)) == null) {
            throw new AuthorityScopeNotFoundException();
        }
        return authorityScope;
    }
    
    /**
     * 获取指定的授权范围定义（要求可空）
     */
    public AuthorityScope getProvidedAuthorityScope(String authScope) {
        return DEFAULT_SCOPES.get(authScope);
    }
    
    /**
     * 获取当前用户的所有授权清单，包括全局和特定授权范围的。
     */
    public abstract String[] getMyAuths() throws Exception;
    
    /**
     * 查询当前用户在特定授权范围的哪些标的上拥有指定操作的执行授权。
     * 
     * 授权码代表着某项操作权限，那该函数查询结果即为当前用户在指定授权范围内拥有该操作执行权限的标的清单。
     * 
     * 如果当前用户为管理员，则不用进行相关的授权查询，被允许访问所有数据，此时返回 null 值。
     * 
     * @param authKey 授权名称
     * 
     * @param authScope 授权范围
     * 
     * @return 注意： *null* - 表示在所有标的上均拥有此操作的授权
     */
    public abstract long[] queryMyScopeTargetIdsByAuthKey(String authKey, String authScope) throws Exception;
    
    /**
     * 检索指定授权标的上的授权人员清单
     * 
     * @param authKey 授权名称
     * 
     * @param authScope 授权范围
     * 
     * @param targetIds 授权标的
     */
    public abstract long[] queryAuthedUserIds(String authKey, String authScope, long... targetIds) throws Exception;
    
    /**
     * 检查当前用户是否拥有指定授权标的上的指定授权
     * @param authKey 授权名称
     * @param scopeType 授权范围
     * @param scopeTargetId 授权的标的
     */
    public abstract boolean hasPermission(String authKey, String authScope, Long targetId) throws Exception;

    /**
     * 检查当前用户是否拥有任一授权范围任一标的上的指定授权
     * 
     * @param authKey 授权名称
     */
    public abstract boolean hasAnyPermission(String authKey) throws Exception;
    
    /**
     * 检查当前用户是否拥有指定授权范围内任一标的上的指定授权
     * @param authKey 授权名称
     * @param scopeType 授权范围
     */
    public abstract boolean hasAnyPermission(String authKey, String authScope) throws Exception;
    
    /**
     * 当应用在完成启动后，会将所有接口或流程事件的授权配置信息做持久化存储，以便用于授权的配置
     */
    public abstract void saveAuthorityEntitisForConfig(String group, Collection<AuthorityEntity> entitis) throws Exception;
}
