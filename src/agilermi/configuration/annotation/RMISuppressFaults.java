package agilermi.configuration.annotation;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import agilermi.exception.RemoteException;

/**
 * This annotation can be applied to a remote method of a remote interface to
 * suppress RMI faults and exceptions. If a remote method is annotated with this
 * annotation, it should not be declared to throw {@link RemoteException}. Be
 * careful using this annotation, it changes the semantics of remote method and
 * it does not allow the application to notice any stub fault, such as the
 * expiration of a remote pointer.
 * 
 * @author Salvatore Giampa'
 *
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface RMISuppressFaults {

}
