package agilermi;

class InvocationHandle implements Handle {
	private static final long serialVersionUID = 992296041709440752L;

	private static long nextInvocationId = 0;

	public long id;
	public String objectId;
	public String method;
	public Object[] parameters;
	public Class<?>[] parameterTypes;

	public transient Object returnValue;
	public transient Class<?> returnClass;
	public transient Throwable thrownException;
	public transient boolean returned = false;

//	public InvocationHandle() {
//		id = nextInvocationId++;
//	}

	public InvocationHandle(long id, String objectId, String method, Class<?>[] parameterTypes, Object[] parameters) {
		this.id = id;
		this.objectId = objectId;
		this.method = method;
		this.parameterTypes = parameterTypes;
		this.parameters = parameters;
	}

	public InvocationHandle(String objectId, String method, Class<?>[] parameterTypes, Object[] parameters) {
		id = nextInvocationId++;
		this.objectId = objectId;
		this.method = method;
		this.parameterTypes = parameterTypes;
		this.parameters = parameters;
	}
}
