package com.socyno.webbsc.ctxutil;

import com.google.gson.reflect.TypeToken;
import com.socyno.base.bscmixutil.JsonUtil;
import com.socyno.base.bscmixutil.StringUtils;
import com.socyno.base.bscsqlutil.AbstractDao;
import com.socyno.webbsc.config.ConfigFlags;
import com.socyno.webbsc.config.ConfigService;
import com.socyno.webbsc.config.PropertyPlaceholderLoader;

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
public class ContextUtil {
    
    private static final Pattern regexpSensitive = Pattern
            .compile("(token|password)(\\\"?\\s*[\\=\\:]\\s*\\[?\\\"?)([^,;\\\"\\'&}]+)",
                    Pattern.CASE_INSENSITIVE);
    
    public static boolean configAccessable() {
        ConfigService srv = null;
        try {
            srv = getConfigService();
        } catch (Exception e) {
            log.trace(e.toString(), e);
        }
        return srv != null;
    }
    
    public static String getAppName() {
        return PropertyPlaceholderLoader.getAppName();
    }
    
    private static ConfigService getConfigService() {
        return PropertyPlaceholderLoader.getConfigService();
    }
    
    public static AbstractDao getBaseDataSource() {
        return PropertyPlaceholderLoader.getBaseDataSource();
    }
    
    public static void deleteConfigs(String... names) throws Exception {
        getConfigService().deleteConfigs(names);
    }
    
    public static void setConfig(String name, String value) throws Exception {
        getConfigService().setConfig(name, value);
    }
    
    public static void setConfig(String name, String value, String comment) throws Exception {
        getConfigService().setConfig(name, value, comment);
    }
    
    public static String getConfig(String key) {
        return getConfigService().getValue(key);
    }
    
    public static String getConfig(String key, int flags) {
        return getConfigService().getValue(key, flags);
    }
    
    public static String getConfigTrimed(String key) {
        return getConfigService().getValue(key, ConfigFlags.TRIMED);
    }
    
    public static String[] getConfigs(String key) {
        return getConfigService().getValues(key, "[,;]+");
    }
    
    public static String[] getConfigsNonBlankTrimed(String key) {
        return getConfigService().getValues(key, "[,;]+",
                ConfigFlags.TRIMED | ConfigFlags.NONBLANK | ConfigFlags.UNIQUE);
    }
    
    public static String[] getConfigs(String key, String regexp) {
        return getConfigService().getValues(key, regexp);
    }
    
    public static String[] getConfig(String key, String regexp, int flags) {
        return getConfigService().getValues(key, regexp, flags);
    }
    
    public static List<String> getConfigKeys() {
        return getConfigService().getConfigKeys();
    }
    
    public static boolean inDebugMode() {
        return configAccessable() && "yes".equals(getConfigTrimed("system.basic.debug.enabled"));
    }
    
    public static String replaceSensitive(String str) {
        if (StringUtils.isBlank(str)) {
            return str;
        }
        
        return regexpSensitive.matcher(str).replaceAll("$1$2######");
    }
    
    /**
     * 获取配置参数，并以简单字串散列的形式执行JSON反序列化。
     * 
     * @return
     * 如果参数未定义或为空白串，则返回 empty map。
     * 
     * @throws
     * JsonSyntaxException Json 语法错误
     * 
     * @throws
     * JsonParseException 数据反序列化错误
     */
    public static Map<String, String> getConfigAsStringMap(String key) {
        String config = getConfig(key);
        if (StringUtils.isBlank(config)) {
            return Collections.emptyMap();
        }
        return JsonUtil.fromJson(config, new TypeToken<Map<String, String>>() {}.getType());
    }
}