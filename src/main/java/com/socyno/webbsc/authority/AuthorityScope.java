package com.socyno.webbsc.authority;

import com.socyno.base.bscmixutil.StringUtils;

import lombok.Getter;
import lombok.NonNull;

@Getter
public final class AuthorityScope {
    
    public enum ScopeType {
        GUEST,
        SYSTEM,
        SUBSYSTEM;
    }
    
    private final String name;
    
    private final String display;
    
    private final ScopeType type;
    
    public AuthorityScope(@NonNull String name, @NonNull String display, @NonNull ScopeType type) {
        this.type = type;
        this.name = name;
        this.display = display;
    }
    
    public boolean isGuest() {
        return ScopeType.GUEST.equals(type);
    }
    
    public boolean isSystem() {
        return ScopeType.SYSTEM.equals(type);
    }
    
    public boolean isSubsystem() {
        return ScopeType.SUBSYSTEM.equals(type);
    }
    
    public boolean isCheckTargetId() {
        return isSubsystem();
    }
    
    public int hashCode() {
        return name == null ? 0 : name.hashCode();
    }
    
    public boolean equals(Object another) {
        if (another == null || !getClass().equals(another.getClass())) {
            return false;
        }
        return StringUtils.equals(name, ((AuthorityScope)another).getName());
    }
}
