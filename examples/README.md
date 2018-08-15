# CoarseRMI Example Application
As is usually done in Java RMI, the application is divided in three projects:
- The Commons project: collects all the interfaces that are shared between client and server;
- The Server project: implements the Commons' interfaces and provide an implementation of the business services;
- The Client project: creates the stubs of the remote objects and uses them.

## Projects notes
To see and explore this example application, imports all the three eclipse projects provided in this folder.
They are linked through build paths.
Then start the server first, and the client later.
