package com.zuehlke.carrera.javapilot.io;

import com.zuehlke.carrera.relayapi.messages.*;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class RaceRecorderTest {

    @Before
    public void createTestDataDirectory() throws Exception{
        File testDataDirectory = new File("testdata");
        if (!testDataDirectory.mkdir()) {
            throw new RuntimeException("Could not create " + testDataDirectory.getAbsolutePath());
        }
    }
    @After
    public void removeTestDataDirectory() throws IOException {
        File testDataDirectory = new File("testdata");
        FileUtils.forceDelete(testDataDirectory);
    }

    @Test
    public void testRecordAndReplayStart() {
        RaceRecorderPlayer recorder = new RaceRecorderPlayer("testdata");

        String tag = recorder.record(new RaceStartMessage("sim02", "training", "kobayashi", System.currentTimeMillis(), "description", true));
        recorder.close();

        recorder = new RaceRecorderPlayer("testdata");

        Stream<Object> objectStream = recorder.replay(tag);
        try {
            objectStream.forEach(o -> Assert.assertEquals(o.getClass(), RaceStartMessage.class));
        } catch (EndOfStreamException e) {
            // ignore: We use this to finish the otherwise infinite stream;
        }
    }

    @Test
    public void testRecordAndReplayEvents() {
        RaceRecorderPlayer recorder = new RaceRecorderPlayer("testdata");
        String tag = recorder.record(new RaceStartMessage("sim02", "training", "kobayashi", System.currentTimeMillis(), "description", true));

        for (int i = 0; i < 5; i++) {
            SensorEvent event = createEvent(i, i);
            recorder.record(event);
        }

        recorder.close();
        Stream<Object> objectStream = recorder.replay(tag);
        try {
            objectStream.forEach(System.out::println);
        } catch (EndOfStreamException eose) {
            // ignore: We use this to finish the otherwise infinite stream;
        }
    }

    @Test
    public void testMerge() {
        RaceRecorderPlayer recorder = new RaceRecorderPlayer("testdata");
        String tag = recorder.record(new RaceStartMessage("sim02", "training", "kobayashi", System.currentTimeMillis(), "description", true));
        recorder.record(createEvent(10, 10));
        recorder.record(createEvent(20, 20));
        recorder.record(new PowerControl(15, "koba", "pwd", 15));
        recorder.record(new PowerControl(25, "koba", "pwd", 25));
        recorder.record(new VelocityMessage("sim02", 21, 21, "1FDB"));
        recorder.record(new VelocityMessage("sim02", 22, 22, "1FDB"));
        recorder.record(new PenaltyMessage("sim02", "1FDB", 23, 20, 2000), 23L);
        recorder.record(new PenaltyMessage("sim02", "1FDB", 26, 20, 2000), 26L);

        recorder.close();
        Stream<Object> objectStream = recorder.replay(tag);
        List<Object> objectList = new ArrayList<>();
        try {
            objectStream.forEach(o -> {
                System.out.println(o);
                objectList.add(o);
            });
        } catch (EndOfStreamException eose) {
            System.out.println();
            // ignore: We use this to end the otherwise infinite stream;
        }
        Assert.assertEquals(objectList.get(0).getClass(), RaceStartMessage.class);
        Assert.assertEquals(objectList.get(1).getClass(), SensorEvent.class);
        Assert.assertEquals(objectList.get(2).getClass(), PowerControl.class);
        Assert.assertEquals(objectList.get(3).getClass(), SensorEvent.class);
        Assert.assertEquals(objectList.get(4).getClass(), VelocityMessage.class);
        Assert.assertEquals(objectList.get(5).getClass(), VelocityMessage.class);
        Assert.assertEquals(objectList.get(6).getClass(), PenaltyMessage.class);
        Assert.assertEquals(objectList.get(7).getClass(), PowerControl.class);
        Assert.assertEquals(objectList.get(8).getClass(), PenaltyMessage.class);
    }


    private SensorEvent createEvent(int g2, long t) {
        return new SensorEvent("track", new int[]{1, 2, 4}, new int[]{0, 0, g2}, new int[]{6, 7, 8}, t);
    }

}
