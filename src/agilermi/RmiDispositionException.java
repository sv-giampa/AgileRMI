package agilermi;

/**
 * Thrown when an {@link RmiHandler} instance has been disposed before or during
 * the invocation. This can be sent to the attached failure observers when the
 * {@link RmiHandler#dispose()} method has been called
 * 
 * @author Salvatore Giampa'
 *
 */
public class RmiDispositionException extends RuntimeException {
	private static final long serialVersionUID = 3064594603835597427L;

	public RmiDispositionException() {
		super("The ObjectPeer has been disposed");
	}
}
