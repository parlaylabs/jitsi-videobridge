# Highfive

The Jitsi Videobridge (often referred to as JVB or bridge) is a service that relays
media streams to interested call participants.

See the [Intro Section](#Intro) for background information about the Jitsi Videobridge.
To run, just do:

```sh
./run
```

The run script will automatically set up a ~/.sip-communicator/sip-communicator.properties file with the default local run settings.

# Running with disco

Newer cloud-dev versions as of Apr 4 should auto-start the disco mixer along with the Jitsi videobridge.

If you are running JVB manually on cloud-dev, you can use the instructions in the next section.

## Running on cloud-dev

cloud-dev instances as of 16.09 will autospawn the jitsi-videobridge on bootup of the cloud-dev.

If you wish to run your own bridge:
```
# Stop the existing bridge
systemctl stop jitsi-videobridge

# run your own bridge from the jitsi-videobridge directory
./run
# or, to run your own bridge with disco support,
./run-with-disco
```

## Debugging with IntelliJ

0. Open the jitsi-videobridge directory as a project in IntelliJ
1. Edit Run/Debug configurations
2. Select '+', Application, select main class `org.jitsi.videobridge.Main`
3. Program arguments: `--apis=rest`
4. Run/Debug as normal
5. If you want to debug libjitsi, be sure to follow instructions in fatline-libjitsi for adding
libjitsi as a module within the jitsi-videobridge IntelliJ workspace.

## Integration tests

There are integrations tests for the REST API.  The tests are run via running the test runner in the /tests directory:

```sh
python videobridge_test_runner.py
```

The test files themselves are in the /tests/test_files directory.  These tests will spin up a docker container that runs the bridge on a dynamic port.  The name of the docker image should be "dev-videobridge" (if something else, the image name should be changed in the test runner).  A dockerfile to build this image is located in the /resources/dev-docker directory

<hr>
```Upstream Jitsi Videobridge documentation begins here```

# Intro

Jitsi Videobridge is an XMPP server component that allows for multiuser video
communication. Unlike the expensive dedicated hardware videobridges, Jitsi
Videobridge does not mix the video channels into a composite video stream, but
only relays the received video channels to all call participants. Therefore,
while it does need to run on a server with good network bandwidth, CPU
horsepower is not that critical for performance.

You can find documentation in the doc/ directory in the source tree.

# Running it

You can download binary packages for Debian/Ubuntu:
* [stable](https://download.jitsi.org/stable/) ([instructions](https://jitsi.org/downloads/ubuntu-debian-installations-instructions/))
* [testing](https://download.jitsi.org/testing/) ([instructions](https://jitsi.org/downloads/ubuntu-debian-installations-instructions-for-testing/))
* [nightly](https://download.jitsi.org/unstable/) ([instructions](https://jitsi.org/downloads/ubuntu-debian-installations-instructions-nightly/))

Maven assembly binaries:
* [assemblies](https://download.jitsi.org/jitsi-videobridge/)

Or you can clone the Git repo and run the JVB from source using maven.

```sh
HOST="Your XMPP server hostname/IP address goes here."
DOMAIN="The JVB component name goes here."
PORT="the component port of your XMPP server goes here."
SECRET="The secret or password for the JVB component."
JVB_HOME="The path to your JVB clone."
ENV_NAME="Set this to specify an environment to enable kafka logging (stagingrtc, highfive-video) and use it during run command to set the java.util.logging.config.file."

mvn clean install exec:java -Dexec.args="--host=$HOST --domain=$DOMAIN --port=$PORT --secret=$SECRET" -Djava.library.path=$JVB_HOME/lib/native/linux-64 -Djava.util.logging.config.file=$JVB_HOME/lib/$ENV_NAME.properties -Dnet.java.sip.communicator.SC_HOME_DIR_NAME=.jitsi-videobridge
```

Configuring Highfive logging(Kafka) and stats

Highfive logging and stats can be configured using properties file and pass the file path as a system property (java.util.logging.config.file)
Sample properties files can be found in lib/ directory for local, stagingrtc, highfive-video environments. If there no file specified
JVB will gracefully stop logging to kafka and writing timeseries to cassandra.

Following are the Highfive introduced new properties with sample values

```
org.jitsi.videobridge.logging.HighfiveLoggingHandler.kafka.host.port= 54.84.144.132:9092
org.jitsi.videobridge.logging.HighfiveLoggingHandler.kafka.topic.prefix= stagingrtc

org.jitsi.videobridge.stats.HighfiveStatsTransport.hfserver=https://start.stagingrtc.fatline.io:8082
```
