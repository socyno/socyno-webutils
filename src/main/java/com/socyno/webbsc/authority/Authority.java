package com.socyno.webbsc.authority;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Authority {
    /**
     * 授权范围
     */
    String value() ;
    
    /**
     * 提取授权标的所需的参数（即绑定方法的参数，从0开始）。
     * 仅针对需要进行授权标的验证的授权范围生效，否则无任何意义。
     */
    int paramIndex() default -1;
    /**
     * 针对需要进行授权标的验证的预定义授权范围，授权标的的解析器。
     */
    Class<? extends AuthorityScopeIdParser> parser() default AuthorityScopeIdNoopParser.class;
    /**
     * 特定授权开放检查，当此处预定以类（的check方法）返回 true，则不做系统授权校验，用户被允许执行该事件
     */
    Class<? extends AuthoritySpecialChecker> checker() default AuthoritySpecialNoopChecker.class;
    /**
     * 特定授权拒绝检查，当此处预定以类（的check方法）返回 true，则不做系统授权校验，用户被拒绝执行该事件。
     * 如果与 checker 同时使用，其优先级更高。
     */
    Class<? extends AuthoritySpecialRejecter> rejecter() default AuthoritySpecialNoopRejecter.class;
    /**
     * <pre>
     * 针对需要进行授权标的验证的预定义授权范围，授权标的的解析器（支持多个标的）。
     * 默认场景下，事件执行人必须同时拥有授权范围内多个标的的授权，才被允许执行该事件。
     * 但可配合 multipleChoiceEnabled 使用，当 multipleChoiceEnabled 被设置为 true 时，
     * 只需要拥有授权范围内任一标的的授权即可执行，此时只有等到多个授权标的都被覆盖到后
     * （即可能需要多个用户均执行了流程实列中的该事件，才能达到流程实列包含的授权范围），
     * 流程实例才会流转到下一个状态，否则始终保持状态的不变。
     * 
     * 通常的适用场景为：一个流程实例涉及到授权范围的多个标的，且需要在多个标的均由相
     * 关角色完成审批或操作后，才将实例流转至下一状态。
     * </pre>
     */
    Class<? extends AuthorityScopeIdMultipleParser> multipleParser() default AuthorityScopeIdNoopMultipleParser.class;
    
    /**
     * <pre>
     * 在 multipleChoiceEnabled 开启时，当对应的流程事件每次被执行时，都会记录下每次执行
     * 所覆盖到的授权标的数据，因此当流程实列出现回退时，可根据需要决定是否清除以及清除哪
     * 些事件上记录的数据。
     * 当返回值为 null 或空数组，则清除所有的数据，否则只清除返回事件的数据。
     * </pre>
     */
    Class<? extends AuthorityScopeIdMultipleCleaner> multipleCleaner() default AuthorityScopeIdNoopMultipleCleaner.class;
    
    /**
     * 请参考 multipleParser 的解释
     */
    boolean multipleChoiceEnabled() default false;
}
