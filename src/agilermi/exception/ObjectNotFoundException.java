package agilermi.exception;

public class ObjectNotFoundException extends RemoteException {

	private static final long serialVersionUID = 5721096680781352174L;

	public ObjectNotFoundException(String objectId) {
		super(objectId);
	}
}
