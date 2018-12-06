# Agile RMI
This is a Java library useful to create network applications, without socket programming, by
simply defining the business interfaces of the objects that you want to export on the network.

The fundamental specifics and goals of the library:
- It is full usable in Android and similar embedded applications;
- The possibility to export any object remotely, without the requirement to implement a explicitly marked remote interface, as we must do in Java RMI (Applicable to systems that were not thought to be remote);
- Supports the Remote marker interface, too, similarly to the Remote interface in Java RMI;
- Tends to execute all the communication tasks for two machines on a single TCP connection (when possible, for example, when the client does not explicitly create multiple RMI connections). This characteristic is very useful because it limits the number of used tcp ports, maintaining good performaces during the communication between two machines, and it can be very desired on low power devices, on smartphones and on very busy server devices.
- RMI configuration is separated from the client application logic that uses the stubs of remote objects;
- The backing RMI system is very easy to configure directly through code;
- It gives possibility to use custom socket factories to configure the RMI system for the use of other communication layers, such as authentication, compression, cryptography and so on, by implementing the SocketFactory and  the ServerSocketFactory abstract classes of the standard JDK;
- Possibility to use custom input and output stream implementations, through a FilterFactory implementation, without providing new socket factory implementations, when possible;
- Supports the full remote reference exchange, also when remote references are sent into serializable objects. The remote objects on the local machine are automatically referenced as remote objects when they are sent on the RMI object output streams;
- It provides a full distributed garbage collection system to avoid memory leaks on the machines that expose remote objects.

AgileRMI want not offer code mobility, because it is intended to be suitable also for systems that requires very strict security constraints, such as Android devices, but it is surely a very very good base for weak and strog code mobility systems, for distributed file systems, and so on.

## References
See documentation at: https://sv-giampa.github.io/AgileRMI/

See test project at: https://github.com/sv-giampa/AgileRMI/tree/master/test/AgileRMI%20-%20Test

See example projects at: https://github.com/sv-giampa/AgileRMI/tree/master/examples
