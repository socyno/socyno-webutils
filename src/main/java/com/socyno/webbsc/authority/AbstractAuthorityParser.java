package com.socyno.webbsc.authority;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.RequestMethodsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.socyno.base.bscmixutil.StringUtils;
import com.socyno.webbsc.service.AbstractPermissionService;

import lombok.Setter;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Setter
public abstract class AbstractAuthorityParser implements ApplicationListener<ContextRefreshedEvent> {
    
    private final static Map<String, AuthorityEntity> authorities = new ConcurrentHashMap<>();
    private final static Map<String, AuthorityEntity> authoritiesByMethod = new ConcurrentHashMap<>();
    
    public void onApplicationEvent(ContextRefreshedEvent event)  {
        // 判断SPRING容器是否加载完成
        if (event.getApplicationContext().getParent() == null) {
            parseAndCheckAuthority(event.getApplicationContext());
        }
    }
    
    public static AuthorityEntity getByMethod(String clazz, String method) {
        return authoritiesByMethod.get(String.format("%s::%s", clazz, method));
    }
    
    protected abstract AbstractPermissionService getPermissionService();
    
    protected boolean checkInterfazeIgnored(Class<?> clazz) {
        return false;
    }
    
    private void parseAndCheckAuthority(ApplicationContext context) {
        /* 获取所有注册接口信息 */
        Map<RequestMappingInfo, HandlerMethod> requestMappings = context.getBean(RequestMappingHandlerMapping.class)
                                                    .getHandlerMethods();
        List<String> errorMessages = new ArrayList<>();
		for (Map.Entry<RequestMappingInfo, HandlerMethod> mapping : requestMappings.entrySet()) {
		    Class<?> methodClass;
			Method method = mapping.getValue().getMethod();
			if (checkInterfazeIgnored((methodClass = method.getDeclaringClass()))) {
				continue;
			}
            String controllerMethod = String.format("%s::%s", methodClass.getName(), method.getName());
            Authority authority;
            AuthorityScope authorityScope;
            if ((authority = method.getAnnotation(Authority.class)) == null 
                    || (authorityScope = getPermissionService().getProvidedAuthorityScope(authority.value())) == null
                    || (authorityScope.isCheckTargetId() && AuthorityScopeIdNoopParser.class.equals(authority.parser()))) {
                errorMessages.add(String.format("接口(%s)未明确定义授权信息, 或未实现 parser 解析器。", controllerMethod));
                continue;
            }
            String scopeType = authority.value();
            RequestMappingInfo info = mapping.getKey();
            PatternsRequestCondition p = info.getPatternsCondition();
            
            StringBuilder requestMethods = new StringBuilder();
            RequestMethodsRequestCondition methodsCondition = info.getMethodsCondition();
            for (RequestMethod requestMethod : methodsCondition.getMethods()) {
                requestMethods.append(requestMethod.toString()).append(",");
            }
            if (requestMethods.length() <= 0) {
                errorMessages.add(String.format("接口(%s/%s)未明确定义请求方法。",
                                            methodClass, method.getName()));
                continue;
            }
            for (String url : p.getPatterns()) {
                AuthorityEntity auth = new AuthorityEntity(scopeType, url);
                if (authorities.containsKey(url)) {
                    AuthorityEntity duplicated = authorities.get(url);
                    errorMessages.add(String.format("接口(%s)声明的访问地址与接口（%s）重名。", controllerMethod, duplicated.getAuth()));
                    continue;
                }
                authorities.put(url, auth);
                authoritiesByMethod.put(controllerMethod, auth);
            }
            try {
                getPermissionService().saveAuthorityEntitisForConfig("::interfaze", authorities.values());
            } catch(Exception e) {
                errorMessages.add(StringUtils.stringifyStackTrace(e));
            }
        }
        
        if (errorMessages.size() > 0) {
            throw new Error(StringUtils.join(errorMessages.toArray(), "\t\n"));
        }
    }
}
