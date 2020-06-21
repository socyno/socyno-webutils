package com.socyno.webbsc.config;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain=true)
public class ConfigItem {
	private String name;
	private String value;
	private String comment;
}