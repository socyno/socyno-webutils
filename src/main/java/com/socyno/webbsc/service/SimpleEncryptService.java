package com.socyno.webbsc.service;

import com.socyno.base.bscmixutil.Base64Util;
import com.socyno.base.bscservice.AbstractAesEncrypt;
import com.socyno.webbsc.ctxutil.ContextUtil;

import lombok.Getter;

public class SimpleEncryptService extends AbstractAesEncrypt {
    
    @Getter
	private static final SimpleEncryptService Default = new SimpleEncryptService();
    
    public static final String CONFIG_SIMPLE_ENCRYPT_KEY = "system.simple.encrypt.default.key";
    
	@Override
	protected byte[] getKey() throws Exception {
		return Base64Util.decode(ContextUtil.getConfigTrimed(CONFIG_SIMPLE_ENCRYPT_KEY));
	}
}
