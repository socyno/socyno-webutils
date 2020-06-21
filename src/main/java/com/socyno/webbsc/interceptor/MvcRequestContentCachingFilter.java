package com.socyno.webbsc.interceptor;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public class MvcRequestContentCachingFilter implements Filter {
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        
    }
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        MvcRequestContentCachingWrapper requestWrapper = new MvcRequestContentCachingWrapper(
                (HttpServletRequest) request);
        chain.doFilter(requestWrapper, response);
    }
    
    @Override
    public void destroy() {
        
    }
}
