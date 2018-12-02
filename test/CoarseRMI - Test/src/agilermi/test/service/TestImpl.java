package agilermi.test.service;

/**
 * Service business implementation
 * 
 * @author Salvatore Giampa'
 *
 */
public class TestImpl implements TestIF {

	@Override
	public int test(Integer x) {
		return 8 * x;
	}

	@Override
	public int test(int x) {
		return 3 * x;
	}

	@Override
	public int add(int x, int y) {
		return x + y;
	}

	@Override
	public void voidReturn(double x) {
		System.out.println(x * x);

	}

	@Override
	public TestIF remoteRef() {
		System.out.println("test returns itself");
		return this;
	}

	@Override
	public void testThrow() {
		throw new RuntimeException("test exception");
	}

	@Override
	public void testObserver(ObserverContainer o) {
		o.getObserver().update(this);
	}

}
