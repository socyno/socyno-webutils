package com.socyno.webbsc.service.jdbc;

import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.adrianwalker.multilinestring.Multiline;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import com.socyno.base.bscmixutil.CommonUtil;
import com.socyno.base.bscmixutil.ConvertUtil;
import com.socyno.base.bscmixutil.StringUtils;
import com.socyno.base.bscmodel.ObjectMap;
import com.socyno.base.bscmodel.SessionContext;
import com.socyno.base.bscsqlutil.AbstractDao;
import com.socyno.base.bscsqlutil.AbstractDao.ResultSetProcessor;
import com.socyno.base.bscsqlutil.SqlQueryUtil;
import com.socyno.webbsc.ctxsrv.CommonSftpService;
import com.socyno.webbsc.ctxutil.ContextUtil;
import com.socyno.webbsc.exception.PageNotFoundException;
import com.socyno.webbsc.exception.PreviewContentNotAllownedException;
import com.socyno.webbsc.exception.PreviewTooLargeException;
import com.socyno.webbsc.model.SimpleAttachmentItem;
import com.socyno.webbsc.model.SimpleAttachmentPath;

import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;

public class SimpleAttachmentService {
    
    @Getter
    private static final SimpleAttachmentService Default = new SimpleAttachmentService();
    
    public AbstractDao getDao() {
        return TenantSpecialDataSource.getMain();
    }
    
    @Data
    @Accessors(chain = true)
    private static class UploadFile {
        private String field;
        private String name;
        private String path;
        private String contentType;
        private long size;
    }
    
    /**
     * 上传附件
     */
	public List<SimpleAttachmentItem> upload(String type, MultipartHttpServletRequest req) throws Exception {
	    if (StringUtils.isBlank(type)) {
	        throw new IllegalArgumentException();
	    }
	    Map<String, List<MultipartFile>> files;
	    if ((files = req.getMultiFileMap()) == null || files.size() <= 0) {
	        return Collections.emptyList();
	    }
	    final List<UploadFile> uploaded = new ArrayList<>();
	    for (List<MultipartFile> fileset : files.values()) {
	        if (fileset == null || fileset.size() <= 0) {
	            continue;
	        }
	        for (MultipartFile file : fileset) {
	            String storePath = String.format("/form-attachments/%s/%s/%s", type,
                        DateFormatUtils.format(new Date(), "yyyy-MM"), StringUtils.randomGuid());
	            CommonSftpService.getDefault().put(file.getInputStream(),  storePath);
                uploaded.add(new UploadFile().setField(file.getName()).setSize(file.getSize())
                        .setName(file.getOriginalFilename()).setContentType(file.getContentType())
                        .setPath(storePath));
	        }
	    }
	    final List<Long> attachmentsIds = new ArrayList<>();
        getDao().executeTransaction(new ResultSetProcessor() {
            @Override
            public void process(ResultSet r, Connection c) throws Exception {
                for (UploadFile upload : uploaded) {
                    getDao().executeUpdate(SqlQueryUtil.prepareInsertQuery(
                        "system_common_attachment", new ObjectMap()
                            .put("type",  type)
                            .put("name",  upload.getName())
                            .put("path",  upload.getPath())
                            .put("size",  upload.getSize())
                            .put("field", upload.getField())
                            .put("content_type", CommonUtil.ifNull(upload.getContentType(), ""))
                            .put("created_id", SessionContext.getUserId())
                            .put("created_by", SessionContext.getUsername())
                            .put("created_name", SessionContext.getDisplay())
                            .put("created_at", new Date())
                    ), new ResultSetProcessor() {
                        @Override
                        public void process(ResultSet r, Connection c) throws Exception {
                            r.next();
                            attachmentsIds.add(r.getLong(1));
                        }
                        
                    });
                }
            }
        });
        return queryByIds(attachmentsIds);
	}
	
    /**
     * 上传通用流程表单附件
     */
    public List<SimpleAttachmentItem> formUpload(String formName, MultipartHttpServletRequest req) throws Exception {
        return upload(String.format("form:%s", formName), req);
    }
    
    
    /**
     * 上传附件，并建立与表单的关联关系
     */
    public void bindWithForm(String formName, Object formId, Long... attachmentIds) throws Exception {
        if (StringUtils.isBlank(formName) || formId == null || StringUtils.isBlank(formId.toString())) {
            throw new IllegalArgumentException();
        }
        if (attachmentIds == null || attachmentIds.length <= 0) {
            return;
        }
        getDao().executeTransaction(new ResultSetProcessor() {
            @Override
            public void process(ResultSet r, Connection c) throws Exception {
                for (Long attachmentId : attachmentIds) {
                    if (attachmentId == null) {
                        continue;
                    }
                    getDao().executeUpdate(SqlQueryUtil.prepareInsertQuery(
                        "system_form_attachment", new ObjectMap()
                            .put("form_name",       formName)
                            .put("form_id",         formId)
                            .put("attachment_id",  attachmentId)
                    ));
                }
            }
        });
    }
	
    /**
     * DELETE FROM
     *     system_form_attachment
     * WHERE
     *     attachment_id = ?
     * AND
     *     form_name = ?
     * AND
     *     form_id = ?
     * ;
     */
    @Multiline
    private final static String SQL_DELETE_ATTACHMENT= "X";
    
    /**
     * 移除附件
     */
    public void delete(String targetFrom, Object targetId, Long attachmentId) throws Exception {
        if (attachmentId == null || StringUtils.isBlank(targetFrom) 
                || targetId == null || StringUtils.isBlank(targetId.toString())) {
            return;
        }
        getDao().executeUpdate(SQL_DELETE_ATTACHMENT, new Object[] {attachmentId, targetFrom, targetId});
    }  
    
    /**
     * SELECT
     *     a.*
     * FROM
     *     system_common_attachement a
     * LEFT JOIN
     *     system_form_attachement f ON f.attachement_id = a.id
     * WHERE
     *     a.id = ?
     */
    @Multiline
    private final static String SQL_QUERY_FORM_ATTACHMENTS_BY_ID = "X";
    
    private SimpleAttachmentPath get(long attachementId, String targetFrom, Object targetId)
            throws Exception {
        List<Object> sqlArgs = new ArrayList<>();
        StringBuilder sqlStmt = new StringBuilder(SQL_QUERY_FORM_ATTACHMENTS_BY_ID);
        if (StringUtils.isNotBlank(targetFrom) ) {
            sqlArgs.add(targetFrom);
            sqlStmt.append(" AND a.type = ?");
            
        }
        if (targetId != null && StringUtils.isNotBlank(targetId.toString()) && !"0".equals(targetId.toString())) {
            sqlArgs.add(targetId);
            sqlStmt.append(" AND f.form_id = ?");
        }
        sqlArgs.add(0, attachementId);
        SimpleAttachmentPath attchement;
        if ((attchement = getDao().queryAsObject(SimpleAttachmentPath.class, sqlStmt.toString(),
                sqlArgs.toArray())) == null) {
            throw new PageNotFoundException();
        }
        return attchement;
    }
    
    /**
     * 附件下载
     */
    public void download(long attachementId, HttpServletRequest req, HttpServletResponse resp) throws Exception {
        SimpleAttachmentPath attchment = get(attachementId, null, null);
        attchment.setContentType("application/octet-stream");
        download(attchment, req, resp);
    }
    
    /**
     * 附件预览
     */
    public void preview(long attachmentId,
                        HttpServletRequest req, HttpServletResponse resp) throws Exception {
        SimpleAttachmentPath attchment = get(attachmentId, null, null);
        if (attchment.getSize() > CommonUtil.parseLong(ContextUtil.
                getConfigTrimed("system.attachment.preview.maxsize"), 1024 * 5000L)) {
            throw new PreviewTooLargeException();
        }
        if (StringUtils.isBlank(attchment.getContentType())) {
            throw new PreviewContentNotAllownedException();
        }
        String[] allowMimeTypes;
        if ((allowMimeTypes = StringUtils.split(
                ContextUtil.getConfigTrimed("system.attachment.preview.allowns"),
                ",", StringUtils.STR_LOWER|StringUtils.STR_NONBLANK|StringUtils.STR_TRIMED
            )).length <= 0) {
            allowMimeTypes = new String[] {"text/*", "image/*"};
        }
        boolean allowed = false;
        for (String mimeType : allowMimeTypes) {
            if (mimeType.equalsIgnoreCase(attchment.getContentType()) || (mimeType.endsWith("/*") && StringUtils
                    .startsWithIgnoreCase(attchment.getContentType(), mimeType.substring(0, mimeType.length() - 1)))) {
                allowed = false;
                break;
            }
        }
        if (!allowed) {
            throw new PreviewContentNotAllownedException();
        }
        download(attchment, req, resp);
    }
    
    private void download(SimpleAttachmentPath attchement, HttpServletRequest req, HttpServletResponse resp) throws Exception {
        String fileName = attchement.getName();
        String userAgent = StringUtils.trimToEmpty(req.getHeader("User-Agent")).toUpperCase();
        // IE/Edge浏览器
        if (userAgent.contains("MSIE") || (userAgent.contains("GECKO") && userAgent.contains("RV:11"))) {
            fileName = URLEncoder.encode(fileName, "UTF-8");
        }
        // 其他浏览器
        else {
            fileName = new String(fileName.getBytes("UTF-8"), "iso-8859-1");  
        }
        resp.reset();
        resp.setContentType(attchement.getContentType());
        resp.setHeader("Content-Disposition", "attachment;filename=" + fileName);
        CommonSftpService.getDefault().download(attchement.getPath(), resp.getOutputStream());
    }
    
    /**
     * SELECT
     *     a.*,
     *     f.form_id,
     *     f.form_name
     * FROM
     *     system_form_attachement f,
     *     system_common_attachement a
     * WHERE
     *     f.attachement_id = a.id
     * AND
     *     f.form_name = ?
     * AND
     *     a.field = ?
     * AND
     *     f.form_id in (%s)
     *
     * ;
     */
    @Multiline
    private final static String SQL_QUERY_FORM_ATTACHEMENTS_BY_FORM = "X";
    
    /**
     * 批量检索指定流程表单的关联字段附件
     * 
     * @param targetForm
     * @param targetField
     * @param targetIds
     * @return
     * @throws Exception
     */
    public List<SimpleAttachmentItem> queryByTargetFormFeild(String targetForm, String targetField,
            Object... targetIds) throws Exception {
        return queryByTargetFormFeild(SimpleAttachmentItem.class, targetForm, targetField, targetIds);
    }
    
    public <T extends SimpleAttachmentItem> List<T> queryByTargetFormFeild(Class<T> clazz, String targetForm,
            String targetField, Object... targetIds) throws Exception {
        if (StringUtils.isBlank(targetForm) || StringUtils.isBlank(targetField) || targetIds == null
                || targetIds.length <= 0) {
            return Collections.emptyList();
        }
        
        return getDao().queryAsList(clazz,
                String.format(SQL_QUERY_FORM_ATTACHEMENTS_BY_FORM, StringUtils.join("?", targetIds.length, ",")),
                ArrayUtils.addAll(new Object[] { targetForm, targetField }, targetIds));
    }
    
    /**
     * DELETE f.* FROM
     *     system_form_attachment f,
     *     system_common_attachment a
     * WHERE
     *     f.attachment_id = a.id
     * AND
     *     f.form_name = ?
     * AND
     *     f.form_id = ?
     * AND
     *     a.field = ?
     * ;
     */
    @Multiline
    private final static String SQL_CLEAR_FORM_ATTACHMENTS_BY_FORM = "X";
    /**
     * 清通用流程表单指定字段的附件
     * @param targetForm   表单的名称
     * @param targetId     表单的编号
     * @param targetField  表单的字段
     * @return
     * @throws Exception
     */
    public void cleanByTargetFormField(String targetForm, Object targetId, String targetField) throws Exception {
        if (StringUtils.isBlank(targetForm) || StringUtils.isBlank(targetField) || targetId == null
                || StringUtils.isBlank(targetId.toString())) {
            return;
        }
        getDao().executeUpdate(SQL_CLEAR_FORM_ATTACHMENTS_BY_FORM, new Object[] { targetForm, targetId, targetField });
    }
    
    /**
     * SELECT DISTINCT
     *     t.*
     * FROM
     *     system_common_attachment t
     * WHERE
     *     t.id in (%s)
     * ORDER BY
     *     t.id DESC
     * ;
     */
    @Multiline
    private final static String SQL_QUERY_ATTACHMENTS_BY_IDS = "X";
    
    public List<SimpleAttachmentItem> queryByIds(Long... ids) throws Exception {
        if (ids == null || (ids = ConvertUtil.asNonNullUniqueLongArray(ids)).length <= 0) {
            return Collections.emptyList();
        }
        return getDao().queryAsList(SimpleAttachmentItem.class,
                String.format(SQL_QUERY_ATTACHMENTS_BY_IDS, StringUtils.join("?", ids.length, ",")),
                ids);
    }
    
    public List<SimpleAttachmentItem> queryByIds(Collection<Long> ids) throws Exception {
        if (ids == null || ids.size() <= 0) {
            return Collections.emptyList();
        }
        return queryByIds(ids.toArray(new Long[0]));
    }
}
