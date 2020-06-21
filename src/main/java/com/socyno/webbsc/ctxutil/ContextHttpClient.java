package com.socyno.webbsc.ctxutil;

import com.socyno.base.bscmixutil.StringUtils;
import com.socyno.base.bscmodel.SessionContext;
import com.socyno.base.bscservice.HttpUtil;

import lombok.Getter;

public class ContextHttpClient extends HttpUtil {
    
    @Getter
	private final static ContextHttpClient Default = new ContextHttpClient();

	@Override
	public String getLogUsername() {
		String username = null;
		if (SessionContext.hasUserSession()) {
			String proxyuser = null;
			username = SessionContext.getUsername();
			if (StringUtils.isNotBlank(proxyuser = SessionContext.getProxyUsername())) {
				username = String.format("%s(Proxy by %s)", username, proxyuser);
			}
		}
		return username;
	}

	@Override
	public String replaceLogSensitive(String logmesg) {
		return ContextUtil.replaceSensitive(logmesg);
	}

}
