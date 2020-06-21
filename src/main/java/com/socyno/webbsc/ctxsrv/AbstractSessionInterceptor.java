package com.socyno.webbsc.ctxsrv;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import com.socyno.base.bscexec.MessageException;
import com.socyno.base.bscexec.MissingUserException;
import com.socyno.base.bscmixutil.CommonUtil;
import com.socyno.base.bscmixutil.StringUtils;
import com.socyno.base.bscmodel.AbstractUser;
import com.socyno.base.bscmodel.SessionContext;
import com.socyno.base.bscmodel.UserContext;
import com.socyno.webbsc.ctxutil.ContextUtil;
import com.socyno.webbsc.ctxutil.LoginTokenUtil;

@Slf4j
public abstract class AbstractSessionInterceptor extends HandlerInterceptorAdapter {
    
    public String getTokenHeader() {
        return StringUtils.ifBlank(ContextUtil.getConfigTrimed("system.session.token.header"), "socyno-token");
    }

    protected abstract AbstractUser getAbstractUser(String username) throws Exception;
    
    protected abstract void checkUserAndTokenInvlid (AbstractUser user, String token) throws Exception;
    
    private enum WeakValidation {
        Yes {
            @Override
            public boolean checkToken() {
                return false;
            }
        },
        NotAll {
            @Override
            public boolean checkToken() {
                return true;
            }
        };
        
        public abstract boolean checkToken();
    };
    
    private final WeakValidation weakValidation;
    
    private final int allowedExpiredMinites;
    
    public AbstractSessionInterceptor() {
        this(WeakValidation.NotAll.name(), 0);
    }
    
    public AbstractSessionInterceptor(String weakValidation) {
        this(weakValidation, 0);
    }
    
    public AbstractSessionInterceptor(String weakValidation, int allowedExpiredMinites) {
        
        this.allowedExpiredMinites = allowedExpiredMinites;
        for (WeakValidation v : WeakValidation.values()) {
            if (v.name().equalsIgnoreCase(weakValidation)) {
                this.weakValidation = v;
                return;
            }
        }
        throw new MessageException(String.format("错误的拦截验证设置(%s)", weakValidation));
    }
    
    /**
     * 请求进入Controller层方法执行前验证身份信息
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        SessionContext.setUserContext(null);
        String tokenHeader = getTokenHeader();
        String tokenContent = StringUtils.ifBlank(request.getParameter("__" + tokenHeader), request.getHeader(tokenHeader));
        return tokenValidation(tokenHeader, tokenContent);
    }
    
    /**
     * 令牌弱校验，即当令牌缺失或非法时，视为匿名用户（用于网关层）。
     * 
     */
    private boolean tokenValidation(String header, String token) throws Exception {
        /* 没有令牌 */
        if (StringUtils.isBlank(token)) {
            log.info("没有令牌，视为匿名用户访问。");
            if (weakValidation.checkToken()) {
                throw new MissingUserException();
            }
            return true;
        }
        
        /* 验证令牌的签名 */
        Map<String, Object> tokenInfo;
        if ((tokenInfo = LoginTokenUtil.verifyToken(token, allowedExpiredMinites)) == null || tokenInfo.isEmpty()) {
            log.info("令牌验签失败，视为匿名用户访问。");
            if (weakValidation.checkToken()) {
                throw new MissingUserException();
            }
            return true;
        }
        
        /* 验证令牌用户合法性 */
        Object userId = tokenInfo.get("userId");
        Object display = tokenInfo.get("display");
        Object username = tokenInfo.get("username");
        if ((username == null || StringUtils.isBlank(username.toString()))
                || (userId == null || StringUtils.isBlank(userId.toString()))
                || (display == null || StringUtils.isBlank(display.toString()))) {
            log.info("无效的令牌，未获取到用户基本信息，视为匿名用户访问。");
            if (weakValidation.checkToken()) {
                throw new MissingUserException();
            }
            return true;
        }
        String tokenTenant = null;
        try {
            SessionContext.setUserContext(new UserContext()
                    .setTenant(tokenTenant = AbstractUser.parseTenantFromUsername(username.toString())));
        } catch (Exception ex) {
            log.info("令牌中的账户无法解析到租户信息，视为匿名用户访问。");
            if (weakValidation.checkToken()) {
                throw new MissingUserException();
            }
            return true;
        }
        
        AbstractUser abstractUser = null;
        /* 用户及令牌的有效性扩展校验 */
        try {
            checkUserAndTokenInvlid((abstractUser = getAbstractUser(username.toString())), token);
        }
        catch (Exception e) {
            log.info("用户及令牌扩展校验失败，视为匿名用户访问。", e);
            throw new MissingUserException();
        }
        
        UserContext userContext = new UserContext()
                    .setToken(token)
                    .setSysUser(abstractUser)
                    .setTenant(tokenTenant)
                    .setTokenHead(header)
                    .setTokenUserId(Long.valueOf(userId.toString()))
                    .setTokenDisplay(display.toString())
                    .setTokenUsername(username.toString())
                    .setAdmin(CommonUtil.parseBoolean(tokenInfo.get("isAdmin")));
        
        /* 检查代理人信息 */
        Object proxyUsername;
        if ((proxyUsername = tokenInfo.get("proxyUsername")) != null) {
            if (StringUtils.isBlank(proxyUsername.toString())) {
                throw new MissingUserException("无效的令牌数据，代理人信息不完整。");
            }
            Object proxyDisplay;
            if ((proxyDisplay = tokenInfo.get("proxyDisplay")) == null
                    || StringUtils.isBlank(proxyDisplay.toString())) {
                throw new MissingUserException("无效的令牌数据，代理人信息不完整。");
            }
            userContext.setProxyUsername(proxyUsername.toString())
                        .setProxyDisplay(proxyDisplay.toString());
        }
        
        /* 填充会话信息 */
        SessionContext.setUserContext(userContext);
        return true;
    }
    
    /**
     * 请求完成时，清除会话信息
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception {
        SessionContext.setUserContext(null);
    }
}
