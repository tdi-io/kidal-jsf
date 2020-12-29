package com.openjdl.jsf.webflux.socket.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created at 2020-12-25 15:11:23
 *
 * @author zink
 * @since 2.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface SocketPayloadTypeDef {
  long type();

  Class<?> bodyType();
}
