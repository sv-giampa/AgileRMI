package agilermi.configuration.annotation;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import agilermi.exception.RemoteException;

/**
 * When a remote interface method is annotated with this annotation, it will
 * replace any thrown {@link RemoteException} with the {@link #value()} of this
 * interface.
 * 
 * @author Salvatore Giampa'
 *
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface RMIRemoteExceptionAlternative {
	/**
	 * The exception class that must replace any {@link RemoteException} thrown on
	 * the stub side.
	 * 
	 * @return the class descriptor of the exception
	 */
	Class<? extends Exception> value() default IllegalStateException.class;

}
