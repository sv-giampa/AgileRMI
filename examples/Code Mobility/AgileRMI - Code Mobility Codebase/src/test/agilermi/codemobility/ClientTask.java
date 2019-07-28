package test.agilermi.codemobility;

public class ClientTask implements Task {
	private static final long serialVersionUID = -6438351157610821359L;

	public int x;
	public int y;

	public ClientTask(int x) {
		this.x = x;
	}

	@Override
	public void compute() {
		y = x * x + 1000;
		System.out.println("ClientTask.compute() has been called!");
	}

	@Override
	protected void finalize() throws Throwable {
		System.out.println("ClientTask finalized!");
		super.finalize();
	}

}
