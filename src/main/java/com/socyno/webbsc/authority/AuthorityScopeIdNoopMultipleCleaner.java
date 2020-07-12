package com.socyno.webbsc.authority;


public class AuthorityScopeIdNoopMultipleCleaner implements AuthorityScopeIdMultipleCleaner {
    @Override
    public String[] getEventsToClean() {
        return null;
    }
}
