package com.socyno.webbsc.model;


import com.github.reinert.jjschema.Attributes;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class SimpleFormAttachment extends SimpleAttachmentItem {
    
    @Attributes(title = "流程单名称", readonly = true)
    private String formName;
    
}
