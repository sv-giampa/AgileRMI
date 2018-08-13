package coarsermi;

/**
 * Defines the keys to index the stubs in an {@link ObjectPeer}
 * @author Salvatore Giampa'
 *
 */
class StubKey {
	private String objectId;
	private Class<?> stubInterface;

	public StubKey(String objectId, Class<?> stubInterface) {
		this.objectId = objectId;
		this.stubInterface = stubInterface;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((objectId == null) ? 0 : objectId.hashCode());
		result = prime * result + ((stubInterface == null) ? 0 : stubInterface.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		StubKey other = (StubKey) obj;
		if (objectId == null) {
			if (other.objectId != null)
				return false;
		} else if (!objectId.equals(other.objectId))
			return false;
		if (stubInterface == null) {
			if (other.stubInterface != null)
				return false;
		} else if (!stubInterface.equals(other.stubInterface))
			return false;
		return true;
	}

}