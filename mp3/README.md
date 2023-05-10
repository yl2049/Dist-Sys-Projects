# Group50 MP3


## Instructions for Building and Running the Code
We have already packaged server.java and client.java into server.jar and client.jar and converted them to compiled executables as server and client, stored in test/src.

### Java Version: OpenJDK 19

### Package an application into a Jar (Optional)
If you use IntelliJ IDEA, follow the instructions below and package server.java and client.java into two jar files. 

    https://www.jetbrains.com/help/idea/compiling-applications.html#package_into_jar

### Convert a Jar to Complied Executable (Optional)
Run
```
cd test/src
./convert.sh client.jar client
./convert.sh server.jar server
```
### Run the Program
Upload server, client, and config_file to corresponding VMs.

Run
```
cd test/src
./server [server_name] [config_file]
./client [client_name] [config_file]
```
