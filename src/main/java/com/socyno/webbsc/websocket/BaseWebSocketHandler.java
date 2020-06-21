package com.socyno.webbsc.websocket;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.socyno.base.bscexec.HttpResponseException;
import com.socyno.base.bscexec.MessageException;
import com.socyno.base.bscmixutil.JsonUtil;
import com.socyno.base.bscmixutil.StringUtils;
import com.socyno.base.bscmodel.R;


@Slf4j	
@Service
public class BaseWebSocketHandler extends TextWebSocketHandler {
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.info("Received message : {}", message.getPayload());
        try {
            handleRequest(session, message);
        } catch (Throwable ex) {
            log.error(ex.toString(), ex);
            onError(session, message, ex);
        }
    }
    
    protected void preHandle(WebSocketSession session, WebSocketRequest request) throws Exception {
        
    }
    
    protected void postHandle(WebSocketSession session, WebSocketRequest request) throws Exception {
        if (session.isOpen()) {
            // 借用 HTTP STAUTS 定义 : 204 不再有数据
            writeResponse(session, new R(204, "Finished"));
        }
    }
    
    protected void onError(WebSocketSession session, TextMessage message, Throwable ex) throws Exception {
        if (session.isOpen()) {
            int maxDeepth = 20;
            Throwable msgerr = ex;
            String errmsg = "系统异常";
            while (msgerr != null && maxDeepth-- > 0) {
                if (msgerr instanceof HttpResponseException) {
                    errmsg = msgerr.getMessage();
                }
                msgerr = msgerr.getCause();
            }
            writeResponse(session, R.error(errmsg));
            return;
        }
        if (ex instanceof Exception) {
            throw (Exception)ex;
        }
        throw new Exception(ex);
    }
    
    protected void writeResponse(WebSocketSession session, R response) throws IOException {
        session.sendMessage(new TextMessage(JsonUtil.toJson(response)));
    }
    
    private void handleRequest(WebSocketSession session, TextMessage message) throws IOException {
        WebSocketRequest request = JsonUtil.fromJson(message.getPayload(), WebSocketRequest.class);
        String action = String.format("handlerAction%s", StringUtils.trimToEmpty(request.getAction()));
        Method method = null;
        try {
            Class<?> clazz = this.getClass();
            method = clazz.getMethod(action, WebSocketSession.class, Map.class);
            if (Modifier.isStatic(method.getModifiers()) || !void.class.equals(method.getReturnType())) {
                method = null;
            }
        } catch (Exception e) {
            method = null;
        }
        if (method == null) {
            throw new MessageException(String.format("WEBSOCKET 请求操作(%s)不存在", request.getAction()));
        }
        try {
            preHandle(session, request);
            method.invoke(this, session, request.getParameters());
            postHandle(session, request);
        } catch (Exception e) {
            throw new MessageException(String.format("WEBSOCKET 请求(%s)处理异常", request.getAction()), e);
        }
    }
    
    @Getter
    @ToString
    protected static class WebSocketRequest {
        private String action;
        private Map<String, String> parameters;
    }
}
