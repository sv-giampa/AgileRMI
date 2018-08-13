package coarsermi.test.service;

public interface TestIF {
	int test(int x);
	
	int test(Integer x);
	
	int test(int x, int y);
	
	void test2(double x);
	
	TestIF test3();
	
	void testThrow();
	
	void testObserver(TestObserver o);
	
	void undefinedOnServerSide();
}
