package coarsersi;

/**
 * Thrown when an ObjectPeer has been disposed before or during the invocation.
 * This can be sent to the attached failure observers when the
 * {@link ObjectPeer#dispose()} method has been called
 * 
 * @author Salvatore Giampa'
 *
 */
public class PeerDispositionException extends RuntimeException {
	private static final long serialVersionUID = 3064594603835597427L;

	public PeerDispositionException() {
		super("The ObjectPeer has been disposed");
	}
}
