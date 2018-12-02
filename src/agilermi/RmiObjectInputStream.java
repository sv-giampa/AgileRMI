package agilermi;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

/**
 * This class extends the standard {@link ObjectInputStream} to give an object
 * context to the deserializing {@link RemoteInvocationHandler} instances.
 * 
 * @author Salvatore Giampa'
 *
 */
class RmiObjectInputStream extends ObjectInputStream {
	private String remoteAddress;
	private int remotePort;
	private RmiRegistry rmiRegistry;

	public RmiObjectInputStream(InputStream inputStream, RmiRegistry rmiRegistry, String remoteAddress, int remotePort)
			throws IOException {
		super(inputStream);
		this.rmiRegistry = rmiRegistry;
		this.remoteAddress = remoteAddress;
		this.remotePort = remotePort;
		this.enableResolveObject(true);
	}

	public RmiRegistry getObjectContext() {
		return rmiRegistry;
	}

	public String getRemoteAddress() {
		return remoteAddress;
	}

	public int getRemotePort() {
		return remotePort;
	}

	@Override
	protected Object resolveObject(Object obj) throws IOException {

		if (Proxy.isProxyClass(obj.getClass())) {
			InvocationHandler ih = Proxy.getInvocationHandler(obj);

//			if (ih instanceof RemoteInvocationHandler) {
//				RemoteInvocationHandler sih = (RemoteInvocationHandler) ih;
//				Class<?>[] interfaces = obj.getClass().getInterfaces();
//				InetSocketAddress isa = sih.handler.getInetSocketAddress();
//				Object found = rmiRegistry.getStub(isa.getHostString(), isa.getPort(), sih.objectId, interfaces);
//				if (found != null)
//					obj = found;
//			 }

			if (ih instanceof ReferenceInvocationHandler) {

				ReferenceInvocationHandler lih = (ReferenceInvocationHandler) ih;
				Class<?>[] interfaces = obj.getClass().getInterfaces();
				Object found = rmiRegistry.getStub(remoteAddress, remotePort, lih.getObjectId(), interfaces);
				if (found != null)
					obj = found;
			}
		}

		return obj;
	}

}
