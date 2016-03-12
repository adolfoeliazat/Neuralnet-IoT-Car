# AkkaStarterKit
This is the Starter Kit for all participants who want to develop their solution based on a Java-Spring-boot-Akka architecture.

If you want to use a different technology, this starter kit still provides you with a race track simulator that allows you to

 - verify your network protocol implementation.
 - train your algorithm.

## Architecture
The starter kit is a very simple [Spring Boot] (http://projects.spring.io/spring-boot/) application with a main class
called ```PilotApplication```. This class parses the command line parameters and sets up the connection to the 
[rabbitmq] (https://www.rabbitmq.com/download.html) message broker, if ```-p rabbit``` is provided in the command line. 

When Spring Boot boots up, it scans the class path for classes annotated with ```@Component``` or ```@Service``` annotation.
The two main ```@Service```s in the starter kit are ```PilotService``` and ```SimulatorService```.
 
### The ```PilotService```
The ```PilotService```'s responsibility is to start the Akka actor system and the central Actor ```JavaPilotActor```.
See [Akka documentation] (http://doc.akka.io/docs/akka/2.4.2/intro/what-is-akka.html) for more information about Akka. 
The ```PilotService``` is also responsible for notifying the competition system that it is alive once a second.

    @Scheduled(fixedRate = 1000)
    public void announce() {
        pilotActor.tell(new EndpointAnnouncement(endPointUrl), ActorRef.noSender());
    }
  

### The ```JavaPilotActor```
The ```JavaPilotActor``` is the central actor that communicates to the outside world and forwards any incoming message
from the Rabbitmq connection or the RESTful interface to the other actors that actually do the hard work, 
as they learn and provide the intelligent decisions

### Your Starting Point: ```PowerUpUntilPenalty``` 
This Actor is the very starting point of all intelligent behaviour. From here you can accumulate knowledge, try and optimize strategies, spawn other actors that will do the hard analysis work in parallel, while this actor turns all its attention on the street.

### See what's going on
If you want to understand what your pilot is actually doing you better make sure he tells you everything. I.e., the pilot
should log almost everything. To make it easy for you to understand the actual context, we made the starterkit spit out the
most significant Gyro-z sensor readings to the console. Below you see the pilot crushing in to a right-left-right combination
at high speed. The little tick (value 3059) at the end of the first right curve stems from losing grip. Ain't that cool?

![The starterkit's console output][consoleout]

### Replaying previous races
The starterkit comes with one truly useful feature: It will record all races. The recordings will be stored in a "data" subdirectory
from where your starterkit is being run. In that data subdirectory you'll find subdirectories with 8-digit time stamps to be read as "ddHHmmss".
You can replay the data in e.g. a directory named 11032349 by simply pointing your browser at the REST URL of your pilot: localhost:8081/api/replay/11032349.
With this you can perform a training run and then improve your algorithm until it understands that data.

## Prerequisites
The following software components need be installed for the starter kit to work.

  - java jdk 8
  - JAVA_HOME environment variable points to the Java installation directory
  - maven 3 installed
  - git installed
  - rabbitmq installed (for remote connections) [rabbitmq - download page] (https://www.rabbitmq.com/download.html)

## Installation

To install the starter kit, do the following:

    $ git clone https://github.com/FastAndFurious/AkkaStarterKit
    $ cd AkkaStarterKit
    $ mvn clean install
    $ java -jar target/fnf.starterkit-1.0-SNAPSHOT.jar  <options>

Alternatively you can start the starter kit application with the provided scripts ```run-pilot-only.sh``` and ```run-simulator-only```.

When building against the snapshots of clientapi and simulib, make sure you build those prior to the above so you have them in your local maven cache. To do just that, do the following

    $ git clone https://github.com/FastAndFurious/fnf.clientapi
    $ cd fnf.clientapi
    $ mvn clean install
    $ git clone https://github.com/FastAndFurious/fnf.simulib
    $ cd fnf.simulib
    $ mvn clean install

This starts a web application on your computer. Point your browser to http://localhost:8081 to access the simulator.

![Starter Kit's Simulator][simulator]

  **command line options for the executable jar file:** 
- **no options**

  With no options at all, the simulator and the pilot code will both start up and talk in-memory with each other.
  The default strategy for the given pilot is to increase power until she receives the first speed penalty, then       decrease power with every subsequent penalty, until no penalties are caused anymore. Of course, this is not exactly   a winning strategy, but it should provide you an easy starting point for your own development.

- ```--server.port=<port> ```  
a port name of your choice. Useful, if the default value of 8081 is used elsewhere.

- ```-p rabbit```

    As of now, we only support the rabbitmq protocol for connections from the pilot. For that you need to have a         running rabbitmq server installed on your local machine, or somewhere else, in which case you need to supply
    --javapilot.rabbitUrl=<rabbitmq host> as a runtime parameter

- ```-f [ simulator | pilot ] -p rabbit```
  <Only when -p rabbit is also provided.

  Starts the executable as "simulator" only, or "pilot" only, resp. Be sure to use --server.port option to use a       different   port for one of the processes in case you start both a pilot and a simulator on the same machine.
  Now you have a standalone simulator that you could also use with any other technology, as long as the pilot knows    how to talk to a rabbit queue
  
## Configuration parameters
The starter kit uses the [spring boot configuration concept][springbootconfig]. You'll find the configuration parameters of your starterkit in a file called application.yml in src/main/resources. If that file is copied to where the application actually 
runs - the "root" of the project, if you will - then the latter one's parameters will overwrite the ones from the classpath. 
  
## Connecting to the physical racetrack
The physical racetrack is represented by light barriers, motor units, and - of course - the car itself, the latter using Bluetooth whilest the Arduinos prefer the 
famous [XBee protocol][xbee] (Series 1, to be precise). 
All the radio signals are received by a central (truly central, it's in the middle of the track scene) laptop called "console". The reason is the limited reach of the bluetooth shield
that's built into the racing cars. The console sends everything it gets to a relay server via Websocket. The relay server makes sure that only those participants talk
to each other that are supposed to. That mean's it always knows which of the pilots is actually allowed to control the car. Eventually, the relay server posts the
incoming messages into a queue on the Rabbitmq server. And your pilot should be subscribed to its dedicated queues. The IP address of that central RabbitMQ server will
be provided by the race managemnt team or you may find it in the [competition information][compinfo]


[simulator]: ./images/simulator.png
[consoleout]: ./images/console-out.png

[springbootconfig]: https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html
[xbee]: https://learn.sparkfun.com/tutorials/exploring-xbees-and-xctu
[compinfo]: https://github.com/FastAndFurious/Documentation/blob/master/details/AdministrativeDetails.md
