package com.carddemo.interest.trace;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * COBOL-to-Java traceability marker.
 *
 * <p>Every Java type/method that reimplements COBOL behaviour carries one or more {@code @Trace}
 * annotations pointing at the originating source: program or copybook, paragraph (for programs)
 * and line range. Line numbers refer to the files under {@code app/cbl} and {@code app/cpy}.
 *
 * <pre>
 * &#64;Trace(program = "CBACT04C", paragraph = "1300-COMPUTE-INTEREST", lines = "462-470")
 * &#64;Trace(copybook = "CVTRA01Y", lines = "16-22")
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR, ElementType.RECORD_COMPONENT})
@Repeatable(Traces.class)
public @interface Trace {

    /** COBOL program name, e.g. {@code CBACT04C}. Empty when tracing a copybook. */
    String program() default "";

    /** COBOL copybook name, e.g. {@code CVTRA01Y}. Empty when tracing a program paragraph. */
    String copybook() default "";

    /** COBOL paragraph name, e.g. {@code 1300-COMPUTE-INTEREST}. */
    String paragraph() default "";

    /** Line range in the COBOL source, e.g. {@code "462-470"} or a single line {@code "352"}. */
    String lines() default "";

    /** Optional free-text note on the mapping (e.g. rounding semantics). */
    String note() default "";
}
