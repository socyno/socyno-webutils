package com.socyno.webbsc.authority;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.v1.FieldOption;

import lombok.Data;

@Data
public class AuthorityEntity implements FieldOption {
    
    @Attributes(title="授权范围")
    private String scope;
    
    @Attributes(title="授权标识")
    private String auth;
    
    @Attributes(title="所属应用")
    private String appName;
    
    public AuthorityEntity() {
        this(null, null);
    }
    
    public AuthorityEntity(String auth) {
        this(null, auth);
    }
    
    public AuthorityEntity(String scope, String auth) {
        this.scope = scope;
        this.auth = auth;
    }
    
    @Override
    public String getOptionValue() {
        return getAuth();
    }
    
    @Override
    public String getOptionDisplay() {
        return String.format("%s:%s", getScope(), getAuth());
    }

    @Override
    public String getOptionGroup() {
        return getScope();
    }
    
    @Override
    public void setOptionValue(String value) {
        setAuth(value);
    }
}
