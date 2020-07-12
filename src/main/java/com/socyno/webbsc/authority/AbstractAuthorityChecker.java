package com.socyno.webbsc.authority;

import com.socyno.base.bscexec.MissingUserException;
import com.socyno.base.bscmixutil.ClassUtil;
import com.socyno.base.bscmodel.SessionContext;
import com.socyno.webbsc.exception.NoAuthorityException;
import com.socyno.webbsc.service.AbstractPermissionService;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

@Slf4j
@Aspect
public abstract class AbstractAuthorityChecker {
    
    @Around(value = "@annotation(authority)")
    public Object aroundAuthority(ProceedingJoinPoint joinPoint, Authority authority) throws Throwable {
        checkAuthority(joinPoint.getArgs(), ((MethodSignature) joinPoint.getSignature()).getMethod());
        return joinPoint.proceed();
    }
    
    protected abstract AbstractPermissionService getPermissionService();
    
    /**
     * 校验权限注解
     */
    protected void checkAuthority(Object[] args, Method method) throws Exception {
        
        Authority authority;
        String className = method.getDeclaringClass().getName();
        String methodName = method.getName();
        if ((authority = method.getAnnotation(Authority.class)) == null) {
            log.error("未获取请求方法的授权注解实例信息({}::{})。", className, methodName);
            throw new NoAuthorityException("无法获取到授权配置，拒绝访问！");
        }
        AuthorityEntity systemAuth;
        if ((systemAuth = AbstractAuthorityParser.getByMethod(className, methodName)) == null) {
            log.error("未获取请求方法的授权注解缓存数据({}::{})。", className, methodName);
            throw new NoAuthorityException("无法获取到授权配置，拒绝访问！");
        }
        
        /* 访客接口，允许访问 */
        AuthorityScope scopeType = getPermissionService().getEnsuredAuthorityScope(authority.value());
        if ((scopeType = getPermissionService().getEnsuredAuthorityScope(authority.value())).isGuest()) {
            return;
        }
        
        /* 否则，匿名用户拒绝 */
        if ((SessionContext.getUserId()) == null) {
            throw new MissingUserException(String.format("接口禁止匿名访问： %s", systemAuth));
        }
        
        /* 提取访问授权标的 */
        Object scopeId = null;
        if (scopeType.isCheckTargetId()) {
            int paramIndex = authority.paramIndex();
            if (paramIndex < 0 || paramIndex >= args.length
                    || (scopeId = args[paramIndex]) == null
                    || ((scopeId = ClassUtil.getSingltonInstance(authority.parser()).getAuthorityScopeId(scopeId)) == null)) {
                throw new NoAuthorityException("无法获取到授权标的，拒绝访问！");
            }
        }
        if (getPermissionService().hasPermission(systemAuth.getAuth(), scopeType.getName(), (Long) scopeId)) {
            return;
        }
        throw new NoAuthorityException("授权校验失败，拒绝访问！");
    }
}
