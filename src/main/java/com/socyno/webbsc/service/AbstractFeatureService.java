package com.socyno.webbsc.service;


public interface AbstractFeatureService {
    
    /**
     * 获取租户下包含任一授权的功能清单
     */
    public long[] getAuthTenantFeatures(String tenant, String ...authKeys) throws Exception;
    
    /**
     * 获取租户下的所有功能清单
     */
    public long[] getTenantFeatures(String tenant) throws Exception;
    
    /**
     * 获取租户的在给定功能中的所有接口清单 
     */
    public String[] getTenantAuths(String tenant, Long... features) throws Exception;
    
    /**
     * 获取租户的所有授权操作清单 
     */
    public String[] getTenantAllAuths(String tenant) throws Exception;
    
    /**
     * 获取租户下是否拥有指定的授权操作
     */
    public boolean checkTenantAuth(String tenant, String authKey) throws Exception;
}
