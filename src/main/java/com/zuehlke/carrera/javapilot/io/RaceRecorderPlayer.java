package com.zuehlke.carrera.javapilot.io;

import com.rabbitmq.tools.json.JSONWriter;
import com.zuehlke.carrera.api.seralize.JacksonSerializer;
import com.zuehlke.carrera.relayapi.messages.*;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class RaceRecorderPlayer {

    private static final Logger logger = LoggerFactory.getLogger(RaceRecorderPlayer.class);

    private static final String START="start";
    private static final String EVENTS="events";
    private static final String VELOCITIES="velocities";
    private static final String PENALTIES="penalties";
    private static final String POWER="power";

    private boolean recording = false;

    private final File dataDirectory;
    private final Map<String, FileWriter> writers = new HashMap<>();
    private final List<String> fileNames = Arrays.asList(START, EVENTS, VELOCITIES, PENALTIES, POWER);

    /**
     * create a recorder to write a single race to the given directory. Create it if it doesn't exist yet.
     * @param dataDirectory the directory to create the race directory in. The race directory is named
     *                      like the current time in the format "ddHHmmss" and contains all data types in
     *                      files named "velocities", "events", "penalties", "power", "metadata"
     */
    public RaceRecorderPlayer(String dataDirectory) {

        this.dataDirectory = new File (dataDirectory);

        if ( this.dataDirectory.exists() &&! this.dataDirectory.isDirectory()) {
            throw new RuntimeException(dataDirectory + " exists, but is no directory." );
        }

        if ( this.dataDirectory.mkdir()) {
            logger.info ( this.dataDirectory.getName() + " created.");
        }
    }

    public String record(RaceStartMessage raceStartMessage) {

        String tag = createAllFiles();
        recording = true;

        tryWrite(raceStartMessage, writers.get(START));
        return tag;
    }

    public void record(SensorEvent event) {
        tryWrite( event, writers.get(EVENTS));
    }

    public void record(PowerControl event) {
        tryWrite( event, writers.get(POWER));
    }

    public void record(VelocityMessage event) {
        tryWrite( event, writers.get(VELOCITIES));
    }

    public void record(PenaltyMessage event) {
        record (event, System.currentTimeMillis());
    }

    public void record(PenaltyMessage event, Long timestamp ) {
        TimedPenaltyMessage timedEvent = new TimedPenaltyMessage(event, timestamp);
        tryWrite( timedEvent, writers.get(PENALTIES));
    }


    private String createAllFiles() {
        String now = new DateTime().toString("ddHHmmss");

        File raceDirectory = new File(dataDirectory, now);

        if (!raceDirectory.mkdir()) {
            throw new RuntimeException("Race directory " + raceDirectory.getAbsolutePath() + " already exists");
        }

        for (String fileName : fileNames) {
            try {
                FileWriter writer = new FileWriter(new File(raceDirectory, fileName));
                writers.put(fileName, writer);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return now;
    }

    private void tryWrite ( Object object, FileWriter writer) {
        if ( ! recording ) return;
        try {
            String json = new JSONWriter().write(object);
            writer.write(json+System.lineSeparator());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {

        recording = false;
        for ( FileWriter writer : writers.values() ) {
            try {
                writer.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * replay the race that started at the given tag
     * @param tag of the instance the race start message arrived here.
     * @return a stream of all events
     */
    public Stream<Object> replay(String tag) {

        try {
            Map<Class<?>, BufferedReader> readers = constructReaderMap(tag);
            return Stream.generate(mergingDataSupplier(readers));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<Class<?>, BufferedReader> constructReaderMap(String timestamp) throws IOException {
        Map<Class<?>, BufferedReader> readers = new HashMap<>();

        Path start = dataDirectory.toPath().resolve(timestamp).resolve(START);
        readers.put ( RaceStartMessage.class, Files.newBufferedReader(start));

        Path events = dataDirectory.toPath().resolve(timestamp).resolve(EVENTS);
        readers.put ( SensorEvent.class, Files.newBufferedReader(events));

        Path power = dataDirectory.toPath().resolve(timestamp).resolve(POWER);
        readers.put ( PowerControl.class, Files.newBufferedReader(power));

        Path velocities = dataDirectory.toPath().resolve(timestamp).resolve(VELOCITIES);
        readers.put ( VelocityMessage.class, Files.newBufferedReader(velocities));

        Path penalties = dataDirectory.toPath().resolve(timestamp).resolve(PENALTIES);
        readers.put ( TimedPenaltyMessage.class, Files.newBufferedReader(penalties));
        return readers;
    }

    public Supplier<Object> getSupplier ( String tag ) throws IOException {
        return mergingDataSupplier( constructReaderMap(tag));
    }

    /**
     * supplier that merges the data from the various files and produces the most recent record from any of the streams
     * @param readers the readers to merge the data from
     * @return a supplier to create an infinite stream from
     */
    public Supplier<Object> mergingDataSupplier(final Map<Class<?>, BufferedReader> readers ) {
        return new Supplier<Object>() {

            private Map<Class<?>, Object> nextObjects = null;
            private JacksonSerializer serializer = new JacksonSerializer();

            @Override
            public Object get() {
                if ( nextObjects == null ) {
                    init ();
                    RaceStartMessage start = (RaceStartMessage) nextObjects.get(RaceStartMessage.class);
                    nextObjects.put(RaceStartMessage.class, null );
                    return start;
                }
                return findAndReplaceNext ( nextObjects, readers );
            }

            private Object findAndReplaceNext(Map<Class<?>, Object> nextObjects, Map<Class<?>, BufferedReader> readers) throws EndOfStreamException {
                Object nextObject = findNextObject(nextObjects);
                if (nextObject == null) {
                    for (Reader r : readers.values()) {
                        try {
                            r.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    throw new EndOfStreamException();
                }
                try {
                    String encoded = readers.get(nextObject.getClass()).readLine();
                    if ( encoded == null ) {
                        nextObjects.put(nextObject.getClass(), null );
                    } else {
                        Object nextInRow = serializer.deserialize(encoded, nextObject.getClass());
                        nextObjects.put(nextObject.getClass(), nextInRow);
                    }
                    // special case penalty message
                    if ( nextObject instanceof TimedPenaltyMessage) {
                        return ((TimedPenaltyMessage)nextObject).getOriginal();
                    }
                    return nextObject;
                } catch ( IOException e ) {
                    throw new RuntimeException( e );
                }
            }

            private void init () {
                nextObjects = new HashMap<>();
                readers.entrySet().stream().forEach((e)->{
                    try {
                        String nextLine = e.getValue().readLine();
                        if ( nextLine == null ) {
                            nextObjects.put(e.getKey(), null );
                        } else {
                            nextObjects.put(e.getKey(), serializer.deserialize(nextLine, e.getKey()));
                        }
                    } catch (IOException e1) {
                        throw new RuntimeException(e1);
                    }
                });
            }
        };

    }

    public static Object findNextObject ( Map<Class<?>, Object> objects ) throws EndOfStreamException {
        List<ObjectWithTimestamp<?>> allObjectsWithTimestamps = new ArrayList<>();
        for (Map.Entry<Class<?>, Object> entry : objects.entrySet() ) {
            if ( entry.getValue() == null ) continue;

            if ( entry.getKey() == SensorEvent.class ) {
                allObjectsWithTimestamps.add(new SensorWT(entry.getValue()));
            } else if ( entry.getKey() == PowerControl.class ) {
                allObjectsWithTimestamps.add(new PowerWT(entry.getValue()));
            } else if ( entry.getKey() == VelocityMessage.class ) {
                allObjectsWithTimestamps.add(new VelocityWT(entry.getValue()));
            } else if ( entry.getKey() == TimedPenaltyMessage.class ) {
                allObjectsWithTimestamps.add(new PenaltyWT(entry.getValue()));
            } else if ( entry.getKey() == RaceStartMessage.class ) {
                allObjectsWithTimestamps.add(new StartWT(entry.getValue()));
            }
        }
        allObjectsWithTimestamps.sort((l,r)->(int)(l.timestamp()-r.timestamp()));
        if ( allObjectsWithTimestamps.size() == 0 ) {
            return null;
        }
        return allObjectsWithTimestamps.get(0).object;
    }


    private static abstract class ObjectWithTimestamp<T> {
        T object;
        abstract Long timestamp();

        public ObjectWithTimestamp(Object object) {
            this.object = (T) object;
        }
    }

    private static class StartWT extends ObjectWithTimestamp<RaceStartMessage> {
        @Override
        Long timestamp() {
            return object.getTimestamp();
        }

        public StartWT(Object object) {
            super(object);
        }

    }

    private static class SensorWT extends ObjectWithTimestamp<SensorEvent> {
        public SensorWT(Object object) {
            super(object);
        }
        Long timestamp () {
            return object.getTimeStamp();
        }
    }

    private static class PowerWT extends ObjectWithTimestamp<PowerControl> {
        Long timestamp() {
            return object.getTimeStamp();
        }

        public PowerWT(Object object) {
            super(object);
        }

    }

    private static class VelocityWT extends ObjectWithTimestamp<VelocityMessage> {
        @Override
        Long timestamp() {
            return object.getTimeStamp();
        }

        public VelocityWT(Object object) {
            super(object);
        }

    }

    private static class PenaltyWT extends ObjectWithTimestamp<TimedPenaltyMessage> {
        @Override
        Long timestamp() {
            return object.getTimestamp();
        }

        public PenaltyWT(Object object) {
            super(object);
        }

    }



}
