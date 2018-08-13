package coarsermi.test.service;

public class TestImpl implements TestIF {

	@Override
	public int test(Integer x) {
		return 8*x;
	}
	
	@Override
	public int test(int x) {
		return 3*x;
	}

	@Override
	public int test(int x, int y) {
		return x+y;
	}

	@Override
	public void test2(double x) {
		System.out.println(x*x);

	}

	@Override
	public TestIF test3() {
		System.out.println("test returns itself");
		return this;
	}

	@Override
	public void testThrow() {
		throw new RuntimeException("test exception");
	}
	
	@Override
	public void testObserver(TestObserver o) {
		o.update(this);
	}

}
