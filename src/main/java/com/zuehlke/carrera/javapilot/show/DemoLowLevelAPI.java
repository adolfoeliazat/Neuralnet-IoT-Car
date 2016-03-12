package com.zuehlke.carrera.javapilot.show;

import com.rabbitmq.client.*;
import com.zuehlke.carrera.api.channel.PilotToRelayChannelNames;
import com.zuehlke.carrera.api.client.PublishException;
import com.zuehlke.carrera.api.seralize.JacksonSerializer;
import com.zuehlke.carrera.relayapi.messages.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demonstrates the usage of the low level Rabbit API for the FnF challenge
 */
public class DemoLowLevelAPI {

    // This is assuming that rabbitmq-server is running on this localhost
    private static final String rabbitUri = "amqp://localhost";

    // This name has to be provided with all messages, so that the relay knows who you are.
    private static final String PILOT_NAME = "testpilot";

    // This should be the access code you've been provided by race management
    private static final String ACCESS_CODE = "SECRET";

    private static final int NUMBER_OF_MESSAGES_EXPECTED = 8;

    private Connection connection;
    private PilotToRelayChannelNames channelNames = new PilotToRelayChannelNames(PILOT_NAME);

    private static final Logger logger = LoggerFactory.getLogger("DEMOS");

    private Map<String, Channel> channels = new HashMap<>();
    private AtomicInteger numberOfMessagesReceived = new AtomicInteger(0);

    public static void main ( String [] args ) {

        new DemoLowLevelAPI().demo();
    }

    private void demo() {

        connectToRabbit_or_exit( rabbitUri );

        createChannelsInBothDirections();

        registerConsumers();

        publishMessages();

        while ( ! all_messages_received() ) {
            waitASecond();
        }

        shutdown();
    }


    private void connectToRabbit_or_exit ( String uri ) {
        try {
            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setUri(uri);
            connection = connectionFactory.newConnection();
            connection.addShutdownListener(cause -> {
                logger.info("Connection was shut down. Reason: {}", cause.getReason().protocolMethodName());
            });
        } catch (Exception e) {
            logger.error ( "Couldn't connect to URL: {}", rabbitUri);
            e.printStackTrace();
            System.exit(-1);
        }
    }

    /**
     * creates channels from the pilot to the relay and the other way round
     */
    private void createChannelsInBothDirections() {

        // From the pilot to the relay (and then to racetrack)
        createQueue( "pilot lifesigns", channelNames.announce());
        createQueue( "power controls", channelNames.powerControl());

        // from the racetrack (via relay) to the pilot
        createQueue( "sensor values", channelNames.sensor());
        createQueue( "velocity", channelNames.velocity());
        createQueue( "penalty", channelNames.penalty());
        createQueue( "start", channelNames.raceStart());
        createQueue( "stop", channelNames.raceStop());
        createQueue( "round passed", channelNames.roundPassed());

    }

    /**
     * publish one message to each queue
     */
    private void publishMessages() {

        // from the pilot
        publish(channelNames.announce(), announcement(PILOT_NAME));
        publish(channelNames.powerControl(), powerControl(PILOT_NAME));

        // from the racetrack/relay
        publish(channelNames.raceStart(), raceStart(PILOT_NAME));
        publish(channelNames.raceStop(), raceStop(PILOT_NAME));
        publish(channelNames.sensor(), sensor());
        publish(channelNames.roundPassed(), roundPassed(PILOT_NAME));
        publish(channelNames.velocity(), velocity());
        publish(channelNames.penalty(), penalty());
    }



    private void createQueue(String messageDescription, String channelName ) {
        logger.info("creating channel for {}: {}", messageDescription, channelName);
        Channel channel = createQueue(channelName);
        channels.put(channelName, channel );
    }

    /**
     * register the consumers that will receive messages from the pilot.
     * This could be the relay or the simulator.
     */
    private void registerConsumers() {

        // Life signs
        registerChannel("relay", channelNames.announce());

        // Power Control
        registerChannel("relay", channelNames.powerControl());

        // Race Start Messages
        registerChannel("pilot", channelNames.raceStart());

        // Race Stop Messages
        registerChannel("pilot", channelNames.raceStop());

        // Sensor values
        registerChannel("pilot", channelNames.sensor());

        // round times
        registerChannel("pilot", channelNames.roundPassed());

        // velocity
        registerChannel("pilot", channelNames.velocity());

        // penalty
        registerChannel("pilot", channelNames.penalty());

    }

    private void registerChannel ( String function, String channelName ) {
        try {
            logger.info("registring as {} for {}", function, channelName);
            Channel channel = channels.get(channelName);
            channel.basicConsume(channelName, createConsumer(channel, function));
        } catch ( Exception e ) {
            logger.error("Couldn't register opposite listeners");
            System.exit(-1);
        }
    }



    public void publish(String channelName, String message) {
        logger.info("Publishing {} to channel: {}", message, channelName);
        try {
            Channel channel = channels.get(channelName);
            AMQP.BasicProperties publishProperties = createPublishProperties();
            channel.basicPublish("", channelName, publishProperties, message.getBytes());
        } catch (IOException e) {
            throw new PublishException("Could not publish message", e);
        }
    }



    private AMQP.BasicProperties createPublishProperties() {
        return new AMQP.BasicProperties.Builder().expiration("0").build();
    }

    private void waitASecond() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            logger.error("Interrupted during sleep.");
        }
    }


    /**
     * returns a simple consumer that
     * @param channel the channel to listen to
     * @return a consumer to act upon messages coming through that channel
     */
    private DefaultConsumer createConsumer(Channel channel, String function) {

        return new DefaultConsumer(channel ) {

            @Override
            public void handleDelivery(String consumerTag,
                                       Envelope envelope,
                                       AMQP.BasicProperties properties,
                                       byte[] body) {

                logger.info ( "As {}: received message {}", function, new String(body));
                numberOfMessagesReceived.incrementAndGet();
            }
        };
    }


    private boolean all_messages_received() {
        return numberOfMessagesReceived.get() == NUMBER_OF_MESSAGES_EXPECTED;
    }

    /**
     * creates a queue with the given name.
     * Here, every channel has exactly one queue, and for every queue we have a distinct channel.
     * And every queue is made for only one message type.
     * @param channelName the name of the queue*
     * @return the new channel
     */
    private Channel createQueue(String channelName ) {
        try {
            Channel channel = connection.createChannel();
            boolean durable = true;
            boolean exclusive = false;
            boolean autoDelete = false;
            channel.queueDeclare(channelName, durable, exclusive, autoDelete, null);
            return channel;
        } catch ( Exception e ) {
            logger.error ( "Couldn't create channel: {}", channelName);
            e.printStackTrace();
            System.exit(-1);
            return null;
        }
    }



    private String announcement(String pilotName) {
        // The URL is not used at the moment, thus truly optional.
        PilotLifeSign message = new PilotLifeSign(pilotName, ACCESS_CODE, "some.url.com", System.currentTimeMillis());
        return new JacksonSerializer().serialize(message);
    }

    private String powerControl(String pilotName) {
        PowerControl powerControl = new PowerControl(100, pilotName, ACCESS_CODE, System.currentTimeMillis());
        return new JacksonSerializer().serialize( powerControl);
    }

    private String raceStart (String pilotName ) {
        // None of the race start message's fields actually matter to the pilot. The fields are used for internal purposes.
        RaceStartMessage message = new RaceStartMessage("TheTrack", "test", pilotName, System.currentTimeMillis(), "test", false);
        return new JacksonSerializer().serialize(message);
    }

    private String raceStop ( String pilotName ) {
        RaceStopMessage message = new RaceStopMessage("TheTrack", pilotName, System.currentTimeMillis(), "test");
        return new JacksonSerializer().serialize(message);
    }

    private String penalty () {
        PenaltyMessage message = new PenaltyMessage("TheTrack", "0000", 200.0, 200.0, 2000 );
        return new JacksonSerializer().serialize(message);
    }

    private String velocity () {
        VelocityMessage message = new VelocityMessage("TheTrack", System.currentTimeMillis(), 200.0, "0000");
        return new JacksonSerializer().serialize(message);
    }

    private String sensor () {
        int [] acc = new int[] { 23, 234, 3454 };
        int [] mag = new int[] { 123, -7834, 354 };
        int [] gyr = new int[] { -25, 234, -3454 };
        SensorEvent message = new SensorEvent("Track", acc, gyr, mag, System.currentTimeMillis());
        return new JacksonSerializer().serialize(message);
    }

    private String roundPassed ( String pilotName ) {
        RoundTimeMessage message = new RoundTimeMessage("TheTrack", pilotName, System.currentTimeMillis(), 5678 );
        return new JacksonSerializer().serialize(message);
    }

    private void shutdown() {
        try {
            for (Channel channel : channels.values()) {
                channel.close();
            }
            connection.close();
        } catch ( Exception e ) {
            logger.error("Failed to close channels and the connection");
        }
    }


}
