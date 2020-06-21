package com.socyno.webbsc.config;

import java.util.List;

public interface ConfigBackend {
    public void reload() throws Exception;
    public List<String> getConfigKeys();
    public void deleteConfigs(String ...name) throws Exception;
    public ConfigItem getConfig(String name);
    public ConfigItem setConfig(ConfigItem config) throws Exception;
}
