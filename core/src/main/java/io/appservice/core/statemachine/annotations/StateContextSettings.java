package io.appservice.core.statemachine.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface StateContextSettings {
    int initial() default 0;
    int recover() default -1;
    int crash() default -1;

    boolean store () default true;
}
