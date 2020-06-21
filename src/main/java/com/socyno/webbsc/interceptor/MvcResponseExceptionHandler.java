package com.socyno.webbsc.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.NoHandlerFoundException;

import com.socyno.base.bscexec.HttpResponseException;
import com.socyno.base.bscmixutil.CommonUtil;
import com.socyno.base.bscmixutil.JsonUtil;
import com.socyno.base.bscmixutil.StringUtils;
import com.socyno.base.bscmodel.R;
import com.socyno.webbsc.ctxutil.ContextUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
public class MvcResponseExceptionHandler {
     
    /** 捕获404异常, 
     * 为了能够拦截到404错误， 必须在web.xml配置 DispatchServlet
     * 属性 throwExceptionIfNoHandlerFound=true， 否则不会抛该异常 */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handle404Error(HttpServletRequest req, HttpServletResponse rsp, Exception e)
            throws Exception {
        handleException(req, rsp, e, HttpStatus.NOT_FOUND);
    }
    
    /* 捕获其他异常  */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public void handle500Error(HttpServletRequest req, HttpServletResponse rsp, Exception e)
            throws Exception {
        handleException(req, rsp, e, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    @SuppressWarnings("serial")
    private static final Map<String, Map<Pattern, String>> SpecialExceptionMessage = new HashMap<String, Map<Pattern, String>>() {
        {
            put("com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException",
                    new HashMap<Pattern, String>() {
                        {
                            put(Pattern.compile("^\\s*Duplicate\\s+entry", Pattern.CASE_INSENSITIVE),
                                    "数据库主键或唯一键冲突，请确认是否重复");
                        }
                    });
            put("org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException", new HashMap<Pattern, String>() {
                {
                    put(Pattern.compile("^\\s*Unique\\s+index\\s+or\\s+primary\\s+key\\s+violation",
                            Pattern.CASE_INSENSITIVE), "数据库主键或唯一键冲突，请确认是否重复");
                }
            });
            
        }
    };
    
    protected void handleException(HttpServletRequest req, HttpServletResponse rsp, Exception ex, HttpStatus status)
            throws Exception {
        if (ex != null) {
            log.error(ex.toString(), ex);
            /* 如果异常对象上定义了 ResponseStatus 注解，则不在这里处理  */
            if (AnnotationUtils.findAnnotation(ex.getClass(), ResponseStatus.class) != null) {   
                throw ex; 
            }
        }
        Throwable rx = ex;
        R r = new R(status.value(), status.getReasonPhrase());
        int loopWhileLimit = 50;
        while (rx != null && --loopWhileLimit > 0) {
            if (rx instanceof HttpResponseException) {
                r.setMessage(rx.getMessage());
                r.setStatus(((HttpResponseException) rx).getStatus());
            } else {
                Map<Pattern, String> patternMesgs;
                if ((patternMesgs = SpecialExceptionMessage.get(rx.getClass().getName())) != null) {
                    String defaultMsg = null;
                    for (Map.Entry<Pattern,String> mesg : patternMesgs.entrySet()) {
                        if (mesg.getKey() == null) {
                            defaultMsg = mesg.getValue();
                        } else if (mesg.getKey().matcher(rx.getMessage()).find()) {
                            r.setMessage(mesg.getValue());
                            break;
                        }
                    }
                    if (StringUtils.isNotBlank(defaultMsg)) {
                        r.setMessage(defaultMsg);
                    }
                }
            }
            if (rx.getCause() != null) {
                rx = rx.getCause();
            }
        }
        String data = JsonUtil.toJson(r);
        /* 允许通过参数设置异常统一处理时的响应状态码 */
        rsp.setStatus(CommonUtil.parseInteger(
                ContextUtil.getConfigTrimed("system.basic.exception.unified.response.status"), r.getStatus()));
        rsp.setContentType("application/json;charset=UTF-8");
        PrintWriter writer = rsp.getWriter();
        writer.write(data);
        writer.flush();
    }
}
