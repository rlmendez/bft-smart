# How to configure BFT-SMaRt replicas #
The parameters to configure BFT-SMaRt replicas are stored in two files: hosts.config and system.config.
To comment out a configuration parameter in those files, the line must start with the character #.

## hosts.config ##

The address and port of all replicas must be specified in this file.
The number or replicas must be the same as defined in the property `system.servers.num` of system.config.

## system.config ##

In this file there are several options to configure the replication protocol, number or replicas, timeouts and others. These parameters are described below:

| **Parameter** | **Description** | **Values** | **Default value** |
|:--------------|:----------------|:-----------|:------------------|
| `system.communicatin.useSenderThread` | Specify if the communication system should use a thread to send data | true or false | true              |
| `system.servers.num` | Number of servers in the system | integer    | 4                 |
| `system.servers.f` | Maximum number of faulty replicas supported | integer    | 1                 |
| `system.totalordermulticast.timeout` | Time that a replica waits for the propose of a request. If the request is not proposed by the leader within the interval defined, the replica invokes the leader change protocol | integer (milliseconds) | 10000             |
| `system.totalordermulticast.highMark` | Maximum ahead-of-time message not discarded. When a replica is delayed it stores the consensus in an out-of-context queue.\n If the number of consensus to be processed is above the value defined in this parameter, the replicas discard the messages and invoke the state transfer protocol | integer    | 10000             |
| `system.totalordermulticast.maxtachsize` | Maximun number of messages to be included in a single propose message | integer    | 400               |
| `system.total.ordermulticast.nonces` | Number of nonces (for non-determinism actions) generated | integer    | 0                 |
| `system.communication.inQueueSize` | Number of messages that can be stored in the receive queue of the communication system | integer    | 100000            |
| `system.communication.outQueueSize` | Number of messages taht can be stored in the send queue of each replica | integer    | 100000            |
| `system.communication.useSignatures` | Used to define if clients should use signatures for MAC vectors | 0 or 1     | 0                 |
| `system.communication.useMACs` | Used for communication between the replicas. It defines if replicas should use authentication channels among them | 0 or 1     | 0                 |
| `system.totalordermulticast.state_transfer` | Activate the state transfer protocol | true or false | true              |
| system.totalordermulticast.checkpoint\_period | Period at which the replica asks the state from the application and clear the log. Used to record a checkpoint and bound the size of the log. This number is the count of consensuns messages processed | integer    | 50                |
| `system.totalordermulticast.revival_highMark` | Maximum ahead-of-time message not discarded when the replica is still on EID 0 (after which the state transfer is triggered). In practice this is used for a replica to verify if it crashed and was revived | integer    | 10                |
| `system.initial.view` | Replicas IDs for the initial view, separated by comma. The number of replicas in this parameter should be equal to the one specified in `system.servers.num` | integer,   | 0,1,2,3           |
| `system.ttp.id` | The ID of the trusted third party (TTP). The TTP is used to add and remove replicas to the system | integer    | 7002              |
| `system.bft`  | This sets if the system will function in Byzantine or crash-only mode. Set to "true" to support Byzantine faults. | boolean    | true              |

## Public keys ##

If you need to generate public/private keys for replicas or clients, you can use the following command to generate files for public and private keys:

`./smartrun.sh bftsmart.tom.util.RSAKeyPairGenerator <id of the replica/client>`

Keys are stored in the config/keys folder. The command above creates key pairs both for clients and replicas. Currently such keys need to be manually distributed.