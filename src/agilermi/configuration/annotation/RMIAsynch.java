package agilermi.configuration.annotation;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Setup a remote method to be asynchronous, that is only request of invocation
 * is sent without waiting for response. <br>
 * <br>
 * This annotation has effect on methods with void return type only.<br>
 * <br>
 * Any exception thrown by the remote asynchronous method cannot be thrown on
 * the caller.
 * 
 * @author Salvatore Giampa'
 *
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface RMIAsynch {

}
