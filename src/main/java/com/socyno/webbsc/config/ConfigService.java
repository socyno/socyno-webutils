package com.socyno.webbsc.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.socyno.base.bscmixutil.StringUtils;

import lombok.NonNull;

public class ConfigService {
    
    private ConfigBackend backend;
    
    private final static Pattern REGEXP_CONFIG_KEY = Pattern.compile("^[a-z0-9_\\.\\-]+$", Pattern.CASE_INSENSITIVE);
    
    public ConfigBackend getBackend() {
        return backend;
    };
    
    public void setBackend(ConfigBackend backend) {
        this.backend = backend;
    };
    
    public ConfigItem getConfig(String name) {
        return getBackend().getConfig(name);
    }
    
    public String getValue(String name) {
        ConfigItem config;
        if ((config = getConfig(name)) == null) {
            return null;
        }
        return config.getValue();
    }
    
    public void setConfig(@NonNull ConfigItem c) throws Exception {
        if (!REGEXP_CONFIG_KEY.matcher(c.getName()).find()) {
            throw new InvalidConfigKeyException(String.format("key = %s", c.getName()));
        }
        getBackend().setConfig(c);
    }
    
    public void setConfig(String name, String value) throws Exception {
        setConfig(name, value, null);
    }
    
    public void setConfig(String name, String value, String comment) throws Exception {
        ConfigItem config = new ConfigItem();
        config.setName(name);
        config.setValue(value);
        config.setComment(comment);
        setConfig(config);
    }
    
    public void deleteConfigs(String... names) throws Exception {
        getBackend().deleteConfigs(names);
    }
    
    public List<String> getConfigKeys() {
        return getBackend().getConfigKeys();
    }
    
    public String getValue(String name, int flags) {
        String[] values;
        if ((values = splitValue(getValue(name), null, flags)).length <= 0) {
            return "";
        }
        return values[0];
    }
    
    public String[] getValues(String name, String regex) {
        return splitValue(getValue(name), regex, 0);
    }
    
    public String[] getValues(String name, String regex, int flags) {
        return splitValue(getValue(name), regex, flags);
    }
    
    synchronized public void reload() throws Exception {
        getBackend().reload();
    }
    
    private static String[] splitValue(String value, String regex, int flags) {
        value = StringUtils.nullToEmpty(value);
        if (value.isEmpty()) {
            return new String[0];
        }
        String[] values = regex == null ? new String[] { value } : value.split(regex);
        if (flags == 0) {
            return values;
        }
        List<String> processed = new ArrayList<String>();
        for (String v : values) {
            if ((flags & ConfigFlags.TRIMED) != 0) {
                v = v.trim();
            }
            if ((flags & ConfigFlags.LOWER) != 0) {
                v = v.toLowerCase();
            } else if ((flags & ConfigFlags.UPPER) != 0) {
                v = v.toUpperCase();
            }
            if ((flags & ConfigFlags.PATHEND) != 0) {
                if (!v.endsWith("/")) {
                    v = String.format("%s/", v);
                }
            }
            if ((flags & ConfigFlags.NONBLANK) != 0 && StringUtils.isBlank(v)) {
                continue;
            }
            if ((flags & ConfigFlags.UNIQUE) != 0 && processed.contains(v)) {
                continue;
            }
            processed.add(v);
        }
        return processed.toArray(new String[processed.size()]);
        
    }
}
