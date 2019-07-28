# AgileRMI Example Application
As is usually done in RMI projects, the application is divided in three projects:
- The Commons project: collects all the interfaces and the classes that are shared between client and server;
- The Server project: implements the common interfaces and provide an implementation of the business services;
- The Client project: creates the stubs to contact the remote objects and uses them.

To see and explore this example application, imports all these three Eclipse projects provided in this folder.
They are linked through build paths.
Then start the server first, and the client later.
