package com.socyno.webbsc.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.v1.FieldSimpleOption;
import com.github.reinert.jjschema.v1.FieldType;
import com.socyno.base.bscfield.FieldPassword;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class SystemTenantDbInfo {
    
    public static enum TYPES {
        main;
        
        public static final TYPES get(String name) {
            for (TYPES v : TYPES.values()) {
                if (v.name().equalsIgnoreCase(name)) {
                    return v;
                }
            }
            return null;
        }
    }
    
    public static class FieldOptionsName extends FieldType {
    	
    	private final static FieldOptionsName INSTANCE = new FieldOptionsName();
    	
    	public static FieldOptionsName getInstance() {
    		return INSTANCE;
    	}
    	
        @SuppressWarnings("serial")
        private final static List<FieldSimpleOption> options = new ArrayList<FieldSimpleOption>() {{
            for (TYPES v : TYPES.values() ) {
                add(FieldSimpleOption.create(v.name(),  v.name()));
            }
        }};
        
        @Override
        public FieldType.FieldOptionsType getOptionsType() {
            return FieldOptionsType.STATIC;
        }
        
        @Override
        public List<FieldSimpleOption> getStaticOptions() {
            return Collections.unmodifiableList(options);
        }
    }
    
    public static class FieldOptionsDriver extends FieldType {
    	
    	private final static FieldOptionsDriver INSTANCE = new FieldOptionsDriver();
    	
    	public static FieldOptionsDriver getInstance() {
    		return INSTANCE;
    	}
    	
        @SuppressWarnings("serial")
        private final static List<FieldSimpleOption> options = new ArrayList<FieldSimpleOption>() {{
            add(FieldSimpleOption.create("com.mysql.jdbc.Driver",  "MySQL"));
        }};
        
        @Override
        public FieldType.FieldOptionsType getOptionsType() {
            return FieldOptionsType.STATIC;
        }
        
        @Override
        public List<FieldSimpleOption> getStaticOptions() {
            return Collections.unmodifiableList(options);
        }
    }
    
    @Attributes(title = "名称", required = true, position = 10, type = FieldOptionsName.class)
    private String name;
    
    @Attributes(title = "连接驱动", position = 20, required = true, type = FieldOptionsDriver.class)
    private String jdbcDriver;
    
    @Attributes(title = "连接地址", position = 30, required = true)
    private String jdbcUrl;
    
    @Attributes(title = "帐户", position = 40)
    private String jdbcUser;
    
    @Attributes(title = "密码", position = 50, type = FieldPassword.class)
    private String jdbcToken;
}
