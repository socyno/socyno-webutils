package com.socyno.webbsc.config;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import lombok.Setter;

import org.springframework.beans.factory.config.PreferencesPlaceholderConfigurer;

import com.socyno.base.bscsqlutil.AbstractDao;

@Setter
public class PropertyPlaceholderLoader extends PreferencesPlaceholderConfigurer {
    
    private String propertiesFile = "init.properties";
    private static AbstractDao baseDataSource;
    private static ConfigService configService;
    
    public PropertyPlaceholderLoader() {
        super();
    }
    
    synchronized public void setConfigService(ConfigService cs) {
        configService = cs;
    }
    
    synchronized public void setBaseDataSource(AbstractDao db) {
        baseDataSource = db;
    }
    
    public static AbstractDao getBaseDataSource() {
        return baseDataSource;
    }
    
    public static ConfigService getConfigService() {
        return configService;
    }
    
    @Override
    protected Properties mergeProperties() throws IOException {
        return LoadProperties(super.mergeProperties());
    }
    
    protected Properties LoadProperties(Properties properties) throws IOException {
        if (configService != null) {
            List<String> names = configService.getConfigKeys();
            for (String name : names) {
                properties.setProperty(name, configService.getValue(name));
            }
            for (String prop : properties.stringPropertyNames()) {
                if (names.contains(prop)) {
                    continue;
                }
                try {
                    configService.setConfig(prop, properties.getProperty(prop));
                } catch (Exception e) {
                    throw new IOException(e);
                }
            }
        }
        FileOutputStream oFile = new FileOutputStream(String.format("%s%s%s", System.getProperty("java.io.tmpdir"),
                System.getProperty("file.separator"), propertiesFile), false);
        try {
            properties.store(oFile, "");
        } finally {
            oFile.close();
        }
        return properties;
    }
    
}
