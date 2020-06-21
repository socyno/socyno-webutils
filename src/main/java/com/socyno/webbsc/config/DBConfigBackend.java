package com.socyno.webbsc.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.socyno.base.bscmixutil.CommonUtil;
import com.socyno.base.bscmixutil.StringUtils;
import com.socyno.base.bscmodel.ObjectMap;
import com.socyno.base.bscsqlutil.AbstractDao;
import com.socyno.base.bscsqlutil.AbstractDao.ResultSetProcessor;
import com.socyno.base.bscsqlutil.SqlQueryUtil;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DBConfigBackend implements ConfigBackend {
    
    private AbstractDao baseDao;
    private final String configTableName;
    private final Map<String, String> configFieldMapper;
    private final ScheduledExecutorService scheduledService = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, ConfigItem> initConfigs = new ConcurrentHashMap<String, ConfigItem>();
    private final Map<String, ConfigItem> loadedConfigs = new ConcurrentHashMap<String, ConfigItem>();
    
    private String getTableName() {
        return configTableName;
    }
    
    private String getFieldName() {
        String name;
        if (configFieldMapper != null && StringUtils.isNotBlank(name = configFieldMapper.get("name"))
                && SqlQueryUtil.checkSQLName(name, true)) {
            return name;
        }
        return "name";
    }
    
    private String getFieldValue() {
        String name;
        if (configFieldMapper != null && StringUtils.isNotBlank(name = configFieldMapper.get("value"))
                && SqlQueryUtil.checkSQLName(name, true)) {
            return name;
        }
        return "value";
    }
    
    private String getFieldComment() {
        String name;
        if (configFieldMapper != null && StringUtils.isNotBlank(name = configFieldMapper.get("comment"))
                && SqlQueryUtil.checkSQLName(name, true)) {
            return name;
        }
        return "comment";
    }
    
    public DBConfigBackend(AbstractDao baseDao) throws Exception {
        this(baseDao, null, null, null);
    }
    
    public DBConfigBackend(AbstractDao baseDao, String table, Map<String, String> fieldMapper) throws Exception {
        this(baseDao, null, table, fieldMapper);
    }
    
    public DBConfigBackend(@NonNull AbstractDao baseDao, Map<String, String> configs, String configTableName,
            Map<String, String> configFieldMapper) throws Exception {
        this(baseDao, configs, 5, 30, configTableName, configFieldMapper);
    }
    
    public DBConfigBackend(@NonNull AbstractDao baseDao, Map<String, String> configs, long initDelaySec,
            long reloadDelaySec, String configTableName, Map<String, String> configFieldMapper) throws Exception {
        this.baseDao = baseDao;
        this.configFieldMapper = configFieldMapper;
        this.configTableName = StringUtils.isBlank(configTableName) ? "system_configs" : configTableName;
        SqlQueryUtil.checkSQLName(this.configTableName);
        if (configs != null) {
            for (Entry<String, String> c : configs.entrySet()) {
                if (c.getValue() == null || c.getKey() == null) {
                    continue;
                }
                initConfigs.put(c.getKey(), new ConfigItem().setName(c.getKey()).setValue(c.getValue()));
            }
        }
        reload();
        initDelaySec = CommonUtil.parseMaximalLong(initDelaySec, 0);
        reloadDelaySec = CommonUtil.parseMaximalLong(reloadDelaySec, 3);
        scheduledService.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    reload();
                } catch (Exception e) {
                    log.error("Failed to load configs.", e);
                }
            }
        }, initDelaySec, reloadDelaySec, TimeUnit.SECONDS);
    }
    
    @Override
    synchronized public void reload() throws Exception {
        /* 遍历并获取所有配置信息 */
        Set<String> names = new HashSet<String>();
        List<Map<String, Object>> queries = baseDao.queryAsList(
                String.format("SELECT %s, %s, %s FROM %s", getFieldName(), 
                        getFieldValue(), getFieldComment(), getTableName()),
                null);
        for (Map<String, Object> item : queries) {
            String name;
            if ((name = (String) item.get(getFieldName())) == null) {
                continue;
            }
            names.add(name);
            String value = (String) item.get(getFieldValue());
            String comment = (String) item.get(getFieldComment());
            if (!loadedConfigs.containsKey(name)) {
                log.info("Config {} has been added", name);
            } else if (!StringUtils.equals(loadedConfigs.get(name).getValue(), value)) {
                log.info("Config {} has been updated", name);
            }
            ConfigItem config = new ConfigItem();
            config.setName(name);
            config.setValue(value);
            config.setComment(comment);
            loadedConfigs.put(name, config);
        }
        for (String name : loadedConfigs.keySet()) {
            if (!names.contains(name)) {
                loadedConfigs.remove(name);
                log.info("Config {} has been removed", name);
            }
        }
        
        /* 如果初始化的配置参数不存在，则添加到数据库中 */
        for (String key : initConfigs.keySet()) {
            if (!loadedConfigs.containsKey(key)) {
                setConfig(initConfigs.get(key));
            }
        }
    }
    
    @Override
    public List<String> getConfigKeys() {
        List<String> keys = new ArrayList<String>();
        keys.addAll(initConfigs.keySet());
        keys.addAll(loadedConfigs.keySet());
        return keys;
    }
    
    @Override
    public ConfigItem getConfig(String name) {
        ConfigItem config;
        if ((config = loadedConfigs.get(name)) == null) {
            config = initConfigs.get(name);
        }
        return config;
    }
    
    @Override
    public ConfigItem setConfig(final ConfigItem c) throws Exception {
        ConfigItem origin = getConfig(c.getName());
        if (c.getValue() == null) {
            baseDao.executeUpdate(
                    SqlQueryUtil.prepareDeleteQuery(getTableName(), new ObjectMap().put(getFieldName(), c.getName())));
        } else {
            baseDao.executeUpdate(SqlQueryUtil.prepareInsertQuery(getTableName(),
                    new ObjectMap().put(String.format("=%s", getFieldName()), c.getName())
                            .put(String.format("=%s", getFieldValue()), c.getValue())
                            .put(String.format("=%s", getFieldComment()), c.getComment())));
        }
        loadedConfigs.put(c.getName(), c);
        return origin;
    }
    
    @Override
    public void deleteConfigs(final String... names) throws Exception {
        if (names == null || names.length <= 0) {
            return;
        }
        baseDao.executeTransaction(new ResultSetProcessor() {
            @Override
            public void process(ResultSet result, Connection conn) throws Exception {
                baseDao.executeUpdate(String.format("DELETE FROM %s WHERE %s IN (%s)", getTableName(), getFieldName(),
                        StringUtils.join("?", names.length, ",")), names);
            }
        });
    }
}
