package io.appservice.core.statemachine.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface StateThread {
    int [] states();
    int onSuccessState() default -1;
    int onErrorState () default -1;
    int onAbortState () default -1;
}
