# CoarseRMI
A simple Java library that implements a coarse Remote Method Invocation.

This is a Java library useful to create network applications, without socket programming, by
simply defining the business interfaces of the objects that you want to export on the network.

This library does not strictly implements the Java RMI specifications.

The fundamental advantages of this library are the following:
- The possibility to export remotely any object respect to a specific interface, without the requisite to implement a explicitly marked remote interface, as in Java RMI;
- All the communication tasks for two machines, are executed over a single TCP connection;
- Interests of RMI configuration are separeted from the application domain that uses client stubs;
- The backing RMI system is very easy to configure directly through code.

The important disadvantage of this library is that no code mobility is offered, at the moment.

## References
See documentation at: https://sv-giampa.github.io/CoarseRMI/

See test project at: https://github.com/sv-giampa/CoarseRMI/tree/master/test/CoarseRMI%20-%20Test

See example projects at: https://github.com/sv-giampa/CoarseRMI/tree/master/examples
