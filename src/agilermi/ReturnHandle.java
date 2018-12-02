package agilermi;

class ReturnHandle implements Handle {
	private static final long serialVersionUID = 6674503222830749941L;
	public long invocationId;
	public Class<?> returnClass;
	public Object returnValue;
	public Throwable thrownException;

	public ReturnHandle() {
	}

	public ReturnHandle(long invocationId, Class<?> returnClass, Object returnValue, Throwable thrownException) {
		this.invocationId = invocationId;
		this.returnClass = returnClass;
		this.returnValue = returnValue;
		this.thrownException = thrownException;
	}

}
