package agilermi.test.service;

/**
 * Service business interface
 * 
 * @author Salvatore Giampa'
 *
 */
public interface TestIF {

	int test(Integer x);

	int test(int x);

	int add(int x, int y);

	void voidReturn(double x);

	TestIF remoteRef();

	void testThrow();

	void testObserver(ObserverContainer o);

}
