package com.zuehlke.carrera.javapilot.services;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.zuehlke.carrera.javapilot.akka.JavaPilotActor;
import com.zuehlke.carrera.javapilot.config.PilotProperties;
import com.zuehlke.carrera.javapilot.io.StartReplayCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;

/**
 * Manages the carrera pilot instance.
 */
@Service
@EnableScheduling
public class PilotService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PilotService.class);

    private final ActorSystem system;
    private final ActorRef pilotActor;
    private final String endPointUrl;

    @Autowired
    public PilotService(PilotProperties settings, EndpointService endpointService,
                        SimulatorService simulatorService ){
        this.endPointUrl = endpointService.getHttpEndpoint();
        system = ActorSystem.create(normalize(settings.getName()));
        pilotActor = system.actorOf(JavaPilotActor.props(settings));

        // Simulator learns about the pilot
        simulatorService.registerPilot(pilotActor);

        // Pilot learns about the simulator
        pilotActor.tell(new PilotToRaceTrackConnector(simulatorService.getSystem()), ActorRef.noSender());
    }

    public static String normalize ( String name ) {

        return name.replaceAll("[ +'#|&%$!\"*/()@]", "");
    }

    @Scheduled(fixedRate = 1000)
    public void announce() {
        pilotActor.tell(new EndpointAnnouncement(endPointUrl), ActorRef.noSender());
    }

    @PreDestroy
    public void shutdown () {
        LOGGER.info("Shutting down the actor system.");
        system.shutdown();

    }

    public ActorRef getPilotActor() {
        return pilotActor;
    }

    public void replay(String tag) {
        pilotActor.tell ( new StartReplayCommand(tag), ActorRef.noSender());
    }
}
