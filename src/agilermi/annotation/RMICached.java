package agilermi.annotation;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Setup a remote method to cache its return value for the specified amount of
 * time.<br>
 * <br>
 * Only methods with no parameters can be cached.
 * 
 * @author Salvatore Giampa'
 *
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface RMICached {
	/**
	 * Specifies the amount of time after that the cached result become invalid.
	 * After this timeout, a future call will be shipped to the remote machine.
	 * 
	 * @return a time value in milliseconds
	 */
	int timeout() default 1000;
}
