package com.socyno.webbsc.model;

import com.github.reinert.jjschema.Attributes;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class SystemTenantDbInfoWithId extends SystemTenantDbInfo {
    
    @Attributes(title = "租户编号")
    private Long tenantId;
}
