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
 * expiration of a remote pointer. <br>
 * <br>
 * Each method which encounter an RMI fault and suppress it will return the
 * default value for its return type. The default values are <code>0</code> for
 * all numerical primitive (such as int) and non-primitive (such as
 * {@link Integer}) types, <code>false</code> for the boolean type and
 * <code>null</code> for object types.
 * 
 * @author Salvatore Giampa'
 *
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface RMISuppressFaults {

}
