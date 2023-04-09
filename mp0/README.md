# Distributed Systems Project 0

Event Logging

## Connect

Replace [NetID] with your net id.

Server id range: 01 to 10.

```bash
ssh [netID]@sp23-cs425-50[serverId].cs.illinois.edu
```

## Usage

### Configure JDK version to JDK 19:
Run:
```bash
sudo update-alternatives --config 'java'
```
Then select JDK 19 (or the latest version).


### On the central logger:
To get the logger's IP address, run:
```bash
ifconfig
```

The logger will create a txt file (\[nodeName\]_timestamps.txt) to write data for every connected node.

To start the logger, run:
```bash
cd mp0/code
java ThreadedEchoServer [port]
```

To write the output of all client nodes to a single file, use:
```bash
java ThreadedEchoServer [port] > [fileName]
```


### On the client VM:
To start the client, run
```bash
cd mp0/code
python3 -u ../generator.py [hz] | java Client [nodeID] [loggerIP] [port]
```
