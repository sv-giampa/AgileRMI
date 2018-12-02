# Agile RMI
This is a Java library useful to create network applications, without socket programming, by
simply defining the business interfaces of the objects that you want to export on the network.

This library does not strictly implements the Java RMI specifications.

The fundamental advantages of this library are the following:
- It is full usable in Android and in similar embedded applications.
- The possibility to export any object remotely, without the requirement to implement a explicitly marked remote interface, as we must do in Java RMI (Applicable to systems that are not thought to be remote);
- Supports the RemoteObject marker interface, similarly to the Remote interface in Java RMI, also;
- Tends to execute all the communication tasks for two machines on a single TCP connection (where possible);
- RMI configuration is separated from the client application logic that uses stubs;
- The backing RMI system is very easy to configure directly through code;
- Possibility to use non standard socket factories to implement an RMI system based on other communication layer, to dispose of authentication, compression, cryptography and so on, by implementing the SocketFactory and  theServerSocketFactory interfaces of the standard JDK.

The important disadvantage of this library is that no code mobility is offered, at the moment.

## References
See documentation at: https://sv-giampa.github.io/CoarseRMI/

See test project at: https://github.com/sv-giampa/CoarseRMI/tree/master/test/CoarseRMI%20-%20Test

See example projects at: https://github.com/sv-giampa/CoarseRMI/tree/master/examples
