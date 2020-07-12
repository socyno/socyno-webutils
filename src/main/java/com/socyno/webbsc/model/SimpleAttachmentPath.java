package com.socyno.webbsc.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class SimpleAttachmentPath extends SimpleAttachmentItem {
    private String path;
    private String contentType;
}
