package com.zuehlke.carrera.javapilot.services;

import akka.actor.ActorRef;
import com.zuehlke.carrera.connection.TowardsPilotsConnection;
import com.zuehlke.carrera.relayapi.messages.*;
import com.zuehlke.carrera.simulator.config.SimulatorProperties;
import com.zuehlke.carrera.simulator.model.PilotInterface;
import com.zuehlke.carrera.simulator.model.RaceTrackSimulatorSystem;
import com.zuehlke.carrera.simulator.model.akka.communication.NewsInterface;
import com.zuehlke.carrera.simulator.model.akka.communication.StompNewsInterface;
import com.zuehlke.carrera.simulator.model.racetrack.TrackDesign;
import com.zuehlke.carrera.simulator.model.racetrack.TrackInfo;
import com.zuehlke.carrera.simulator.model.racetrack.TrackSection;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.List;

/**
 * Manages the racetrack simulator instance.
 */
@Service
@EnableScheduling
public class SimulatorService {

    private static final Logger LOG = LoggerFactory.getLogger(SimulatorService.class);

    private RaceTrackSimulatorSystem raceTrackSimulatorSystem;

    private final SimulatorProperties settings;

    private final SimpMessagingTemplate simpMessagingTemplate;

    private final RacetrackToPilotConnector pilotInterface;
    private TowardsPilotsConnection pilotConnection;

    @Autowired
    public SimulatorService ( SimulatorProperties settings,
                            SimpMessagingTemplate simpMessagingTemplate ){
        this.settings = settings;
        this.pilotInterface =  new RacetrackToPilotConnector ();
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    @PostConstruct
    public void init () {

        raceTrackSimulatorSystem = new RaceTrackSimulatorSystem(
                settings.getName(),
                pilotInterface,
                new StompNewsInterface(simpMessagingTemplate),
                new NormalDistribution(settings.getTickPeriod(), settings.getSigma()),
                settings);

        raceTrackSimulatorSystem.ensureConnection(settings.getRabbitUrl());

    }

    public void registerPilot(ActorRef pilot ) {
        pilotInterface.registerPilot(pilot);
    }

    @PreDestroy
    public void shutDownActorSystem () {
        raceTrackSimulatorSystem.shutdown();
    }

    public RaceTrackSimulatorSystem getSystem() {
        return raceTrackSimulatorSystem;
    }

    public void startClock(){
        raceTrackSimulatorSystem.startClock();
    }

    public void stopClock() {
        raceTrackSimulatorSystem.stopClock();
    }

    /**
     * @return the race-track model, which can be used to draw the virtual race-track.
     */
    public TrackInfo getTrackInfo() {
        TrackDesign design = raceTrackSimulatorSystem.getTrackDesign();
        List < TrackSection> sections = design.getTrackData();
        String trackId = settings.getName();
        return new TrackInfo( sections, trackId, design.getBoundarywidth(),
                design.getBoudaryHeight(), design.getInitialAnchor() );
    }

    public void fireRaceStartEvent ( RaceStartMessage message) {
        message.setTrackId("Starterkit's Simulator");
        LOG.info("received race start message");
        if (raceTrackSimulatorSystem != null) {
            raceTrackSimulatorSystem.startRace(message);
        }
        if ( pilotConnection == null ) {
            pilotInterface.send(message); // single process mode
        } else {
            pilotConnection.sendRaceStart(message); // separate processes mode
        }
    }

    public void fireRaceStopEvent ( RaceStopMessage message) {
        LOG.info("received race stop message");
        if (raceTrackSimulatorSystem != null) {
            raceTrackSimulatorSystem.stopRace(message);
        }
        if ( pilotConnection == null ) {
            pilotInterface.send(message); // single process mode
        } else {
            pilotConnection.sendRaceStop(message); // separate processes mode
        }
    }

    public void powerup( int delta ) {
        raceTrackSimulatorSystem.powerup(delta);
    }
    public void powerdown( int delta ) {
        raceTrackSimulatorSystem.powerdown(delta);
    }

    public void reset() {
        raceTrackSimulatorSystem.reset();
    }

    public TrackInfo selectDesign(String trackDesign) {

        // will return the trackdesign and discard it, since we need the complete info.
        raceTrackSimulatorSystem.selectDesign(trackDesign);

        return getTrackInfo();
    }

    public void setPilotConnection(TowardsPilotsConnection pilotConnection) {
        this.pilotConnection = pilotConnection;
    }

}
