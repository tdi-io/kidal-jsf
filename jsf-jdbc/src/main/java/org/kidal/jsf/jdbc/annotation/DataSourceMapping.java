package org.kidal.jsf.jdbc.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created at 2020-08-06 17:20:16
 *
 * @author kidal
 * @since 0.1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataSourceMapping {
  /**
   * 数据源分组ID.
   */
  String value() default "";

  /**
   * 是否使用只读数据源.
   */
  boolean readOnly() default true;
}
