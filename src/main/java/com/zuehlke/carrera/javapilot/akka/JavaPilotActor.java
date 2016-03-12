package com.zuehlke.carrera.javapilot.akka;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import com.zuehlke.carrera.javapilot.config.PilotProperties;
import com.zuehlke.carrera.javapilot.io.StartReplayCommand;
import com.zuehlke.carrera.javapilot.io.StopReplayCommand;
import com.zuehlke.carrera.javapilot.services.EndpointAnnouncement;
import com.zuehlke.carrera.javapilot.services.PilotToRelayConnection;
import com.zuehlke.carrera.relayapi.messages.*;
import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Central actor responsible for driving the car. All data gets here and all decisions are finally made here.
 */
public class JavaPilotActor extends UntypedActor {

    private final Logger LOGGER = LoggerFactory.getLogger(JavaPilotActor.class);
    private final PilotProperties properties;

    private ActorRef strategy;
    private ActorRef recorder;
    private boolean replaying;

    private PilotToRelayConnection relayConnection;

    public JavaPilotActor(PilotProperties properties ) {

        this.properties = properties;
        strategy = getContext().actorOf(PowerUpUntilPenalty.props(getSelf(), 1500));
        recorder = getContext().actorOf(RaceRecorderActor.props(getSelf()));
    }


    public static Props props ( PilotProperties properties) {
        return Props.create(new Creator<JavaPilotActor>() {
            private static final long serialVersionUID = 1L;

            @Override
            public JavaPilotActor create() throws Exception {
                return new JavaPilotActor( properties );
            }
        });
    }

    private void record ( Object message ) {
        if ( recorder != null ) {
            recorder.forward(message, getContext());
        }
    }

    @Override
    public void onReceive(Object message) throws Exception {

        try {

            if (message instanceof StartReplayCommand ) {
                if ( ! replaying ) {
                    recorder = getContext().actorOf(RaceRecorderActor.props(getSelf()));
                    recorder.forward(message, getContext());
                    replaying = true;
                }
            } else if ( message instanceof StopReplayCommand ) {
                replaying = false;

            } else if (message instanceof RaceStartMessage) {
                record(message);
                handleRaceStart();

            } else if (message instanceof RaceStopMessage) {
                record(message);
                handleRaceStop();

            } else if (message instanceof SensorEvent) {
                record(message);
                handleSensorEvent((SensorEvent) message);

            } else if (message instanceof VelocityMessage) {
                record(message);
                handleVelocityMessage((VelocityMessage) message);

            } else if (message instanceof PilotToRelayConnection) {
                this.relayConnection = (PilotToRelayConnection) message;

            } else if (message instanceof EndpointAnnouncement) {
                handleEndpointAnnouncement((EndpointAnnouncement) message);

            } else if (message instanceof PowerAction) {
                handlePowerAction(((PowerAction) message).getPowerValue());

            } else if (message instanceof PenaltyMessage ) {
                record(message);
                handlePenaltyMessage ((PenaltyMessage) message );

            } else if ( message instanceof RoundTimeMessage ) {
                handleRoundTime((RoundTimeMessage) message);

            } else if (message instanceof String) {

                // simply ignore this if there is no connection.
                if ("ENSURE_CONNECTION".equals(message)) {
                    if (relayConnection != null) {
                        relayConnection.ensureConnection();
                    }
                }
            } else {
                unhandled(message);
            }
        } catch ( Exception e ) {
            LOGGER.error("Caught exception: " + e.getMessage());
            e.printStackTrace();
        }

    }

    private void handleRoundTime(RoundTimeMessage message) {
        LOGGER.info ( "Round Time in ms: " + message.getRoundDuration());
    }

    private void handlePenaltyMessage(PenaltyMessage message) {
        strategy.forward(message, getContext());
    }

    /**
     * Action request from the processing topology
     * @param powerValue the new power value to be requested on the track
     */
    private void handlePowerAction(int powerValue) {

        long now = System.currentTimeMillis();

        record(new PowerControl(powerValue, "starterkit", "tikretrats", now));

        if (!replaying) {
            relayConnection.send(new PowerControl(powerValue, properties.getName(),
                    properties.getAccessCode(), now));
        }
    }

    private void handleEndpointAnnouncement(EndpointAnnouncement message) {
        if ( relayConnection != null ) {
            relayConnection.announce(message.getUrl());
        }
    }

    private void handleVelocityMessage(VelocityMessage message) {
        if ( message.getVelocity() == -999 ) {
            handleSample(message);
        } else {
            strategy.forward(message, getContext());
        }
    }

    private void handleSensorEvent(SensorEvent message) {
        if ( isSample ( message ) ) {
            handleSample(message);
        } else {
            strategy.forward(message, getContext());
        }
    }

    private boolean isSample(SensorEvent message) {
        return (( message.getM()[0] == 111.0f)
                && ( message.getM()[1] == 112.0f )
                && ( message.getM()[2] == 113.0f ));
    }

    /**
     * log the receipt and answer with a Speedcontrol of 0;
     * @param message the sample event
     */
    private void handleSample(SensorEvent message) {
        LOGGER.info("received sample SensorEvent: " + message.toString());
        long now = System.currentTimeMillis();
        relayConnection.send (new PowerControl(0, properties.getName(), properties.getAccessCode(), now));
    }

    /**
     * log the receipt and answer with a Speedcontrol of 0;
     * @param message the sample velocity
     */
    private void handleSample(VelocityMessage message) {
        LOGGER.info("received sample velocity message: " + message.toString());
        long now = System.currentTimeMillis();
        relayConnection.send (new PowerControl(0, properties.getName(), properties.getAccessCode(), now));
    }


    private void handleRaceStop() {
        LOGGER.info("received race stop");
    }

    private void handleRaceStart() {
        strategy = getContext().actorOf(PowerUpUntilPenalty.props(getSelf(), 1500));
        long now = System.currentTimeMillis();
        LOGGER.info("received race start at " + new LocalDateTime(now).toString());
    }
}
