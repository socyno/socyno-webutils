package com.socyno.webbsc.ctxutil;


import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator.Builder;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.socyno.base.bscexec.MessageException;
import com.socyno.base.bscmixutil.CommonUtil;
import com.socyno.base.bscmixutil.StringUtils;
import com.socyno.base.bscmodel.AbstractUser;
import com.socyno.base.bscmodel.ObjectMap;

import lombok.extern.slf4j.Slf4j;


import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
@Slf4j
public class LoginTokenUtil {
    
    
    public static final String CONFIG_TOKEN_SECRET_KEY = "system.session.token.secret.key";
    
    public static final String CONFIG_TOKEN_EXPIRATION_KEY = "system.session.token.expiration";
    
    private static long getExpirationMS() {
        return CommonUtil.parseLong(ContextUtil.getConfigTrimed(CONFIG_TOKEN_EXPIRATION_KEY), 72000000L);
    }
    
	private static String getSecretKey() {
		String loginKey;
		if (StringUtils.isBlank(loginKey = ContextUtil.getConfigTrimed(CONFIG_TOKEN_SECRET_KEY))) {
			throw new MessageException("系统登陆密钥未设置,无法登陆系统");
		}
		return loginKey;
	}
    
    /**
     * 创建用户令牌
     * 
     * @param sysUser 用户信息
     * @param isAdmin 是否管理员
     * @param proxyUser 代理人用户。 Null 为正常模式， 否则为被授权的代理用户实体。
     */
    public static String generateToken(AbstractUser sysUser, boolean isAdmin, AbstractUser proxyUser, ObjectMap customProperties) {
        Date nowTime = new Date();
        Builder builder = JWT.create()
                .withClaim("isAdmin",  isAdmin)
                .withClaim("userId", sysUser.getId())
                .withClaim("username", sysUser.getUsername())
                .withClaim("display",  sysUser.getDisplay())
                .withExpiresAt(new Date(nowTime.getTime() + getExpirationMS()))
                .withIssuedAt(nowTime);
        if (proxyUser != null) {
            builder.withClaim("proxyUsername", proxyUser.getUsername())
                    .withClaim("proxyDisplay", proxyUser.getDisplay());
        }
        if (customProperties != null) {
            Object propval;
            for (Map.Entry<String,Object> prop : customProperties.asMap().entrySet()) {
                if ((propval = prop.getValue()) == null) {
                    continue;
                }
                if (long.class.equals(propval.getClass()) || Long.class.equals(propval.getClass())) {
                    builder.withClaim(prop.getKey(), (Long) propval);
                } else if (int.class.equals(propval.getClass()) || Integer.class.equals(propval.getClass())) {
                    builder.withClaim(prop.getKey(), (Integer) propval);
                } else if (boolean.class.equals(propval.getClass()) || Boolean.class.equals(propval.getClass())) {
                    builder.withClaim(prop.getKey(), (Boolean) propval);
                } else if (double.class.equals(propval.getClass()) || Double.class.equals(propval.getClass())) {
                    builder.withClaim(prop.getKey(), (Double) propval);
                } else if (Date.class.equals(propval.getClass())) {
                    builder.withClaim(prop.getKey(), (Date) propval);
                } else if (Integer[].class.equals(propval.getClass())) {
                    builder.withArrayClaim(prop.getKey(), (Integer[]) propval);
                } else if (Long[].class.equals(propval.getClass())) {
                    builder.withArrayClaim(prop.getKey(), (Long[]) propval);
                } else if (String[].class.equals(propval.getClass())) {
                    builder.withArrayClaim(prop.getKey(), (String[]) propval);
                } else {
                    builder.withClaim(prop.getKey(), propval.toString());
                }
            }
        }
        return builder.sign(Algorithm.HMAC256(getSecretKey()));
    }

    public static String generateToken(AbstractUser sysUser, boolean isAdmin, AbstractUser proxyUser) {
        return generateToken(sysUser, isAdmin, proxyUser, null);
    }

    public static String generateToken(AbstractUser sysUser, boolean isAdmin) {
        return generateToken(sysUser, isAdmin, null, null);
    }
    
    /**
     * 解析并获取令牌对象
     * 
     */
    public static Map<String, Object>  parseToken(String token) {
        DecodedJWT jwt;
        if ((jwt = getToken(token)) == null) {
            return null;
        }
        return convertTokenData(jwt.getClaims());
    }
    
    /**
     * 验证并获取令牌对象
     * 
     */
    public static Map<String, Object> verifyToken(String token) {
        return verifyToken(token, 0);
    }
    
    public static Map<String, Object> verifyToken(String token, int allowedExpiredMinites) {
        DecodedJWT jwt;
        if ((jwt = getVerifiedToken(token, allowedExpiredMinites)) == null) {
            return null;
        }
        return convertTokenData(jwt.getClaims());
    }
    
    /**
     * 解析并获取令牌对象
     * 
     */
    public static DecodedJWT getToken(String token) {
        DecodedJWT jwt = null;
        try {
            jwt = JWT.decode(token);
            Date expires = jwt.getExpiresAt();
            if (expires.compareTo(new Date()) <= 0) {
                return null;
            }
        } catch (Exception e) {
            log.error(e.toString(), e);
            return null;
        }
        return jwt;
    }
    
    /**
     * 验证并获取令牌对象
     * 
     */
    private static DecodedJWT getVerifiedToken(String token, int allowedExpiredMinites) {
        JWTVerifier verifier = JWT.require(Algorithm.HMAC256(getSecretKey()))
                    .ignoreIssuedAt().build();
        DecodedJWT jwt = null;
        try {
            jwt = verifier.verify(token);
        } catch (Exception e) {
            if (allowedExpiredMinites > 0 && (e instanceof TokenExpiredException) && (jwt = JWT.decode(token)) != null
                    && (new Date().getTime() - jwt.getExpiresAt().getTime()) < allowedExpiredMinites * 60000) {
                return jwt;
            }
            log.error(e.toString(), e);
            return null;
        }
        return jwt;
    }
    
    /**
     * 解析令牌数据
     */
    private static Map<String, Object> convertTokenData(Map<String, Claim> map) {
        Map<String, Object> tokenData = new HashMap<>(map.size());
        for (Entry<String, Claim> maps : map.entrySet()) {
            Claim val = maps.getValue();
            if (val.asLong() != null) {
                tokenData.put(maps.getKey(), val.asLong());
                continue;
            } else if (val.asBoolean() != null) {
                tokenData.put(maps.getKey(), val.asBoolean());
                continue;
            } else if (val.asInt() != null) {
                tokenData.put(maps.getKey(), val.asInt());
                continue;
            } else {
                tokenData.put(maps.getKey(), val.asString());
                continue;
            }
        }
        return tokenData;
    }
}
