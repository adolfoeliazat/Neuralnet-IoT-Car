package com.zuehlke.carrera.javapilot.akka;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import com.zuehlke.carrera.relayapi.messages.PenaltyMessage;
import com.zuehlke.carrera.relayapi.messages.RaceStartMessage;
import com.zuehlke.carrera.relayapi.messages.SensorEvent;
import com.zuehlke.carrera.relayapi.messages.*;
import com.zuehlke.carrera.timeseries.FloatingHistory;
import org.apache.commons.lang.StringUtils;
import java.util.ArrayList;
import java.util.List;

/**
 *  this logic node increases the power level by 10 units per 0.5 second until it receives a penalty
 *  then reduces by ten units.
 */
public class PowerUpUntilPenalty extends UntypedActor {

  private enum Combo {
    STRAIGHT, RIGHT, LEFT
  };

  //determining the sequence of the track

  ArrayList<Combo> sequence = new ArrayList<>();
  ArrayList<Combo> finalSeq = new ArrayList<>();
  ArrayList<Combo> time = new ArrayList<>();
  ArrayList<Combo> power = new ArrayList<>();

  int count = 0; //count of how many turns
  int cap = 10000; // cap of how many total turns in array
  int position = 0; //current position of turn that the car is at RIGHT NOW

  String turn; // declares the turn String
  int turnTemp;
  int previousTemp = 1;

    private final ActorRef kobayashi;

    private double currentPower = 0;

    private long lastIncrease = 0;

    private int maxPower = 180; // Max for this phase;

    private boolean probing = true;

    private FloatingHistory gyrozHistory = new FloatingHistory(8);

    /**
     * @param pilotActor The central pilot actor
     * @param duration the period between two increases
     * @return the actor props
     */
    public static Props props( ActorRef pilotActor, int duration ) {
        return Props.create(
                PowerUpUntilPenalty.class, () -> new PowerUpUntilPenalty(pilotActor, duration ));
    }
    private final int duration;

    public PowerUpUntilPenalty(ActorRef pilotActor, int duration) {
        lastIncrease = System.currentTimeMillis();
        this.kobayashi = pilotActor;
        this.duration = duration;
        //sequence.add(Combo.STRAIGHT); DON'T NEED THIS!

    }

    public boolean isPrefix(List<Combo> arr, List<Combo> prefix) {
      int maxLength =  Math.min(arr.size(),prefix.size());
      for (int i = 0; i < maxLength; ++i) {
        if (prefix.get(i) != arr.get(i)) {
          return false;
        }
      }
      return true;
    }

    public boolean matchesWholeArray(List<Combo> arr, List<Combo> prefix) {
      if (arr.size() == 0) {
        return true;
      } else if (isPrefix(arr, prefix)) {
        return matchesWholeArray(arr.subList(prefix.size(), arr.size()), prefix);
      } else {
        return false;
      }
    }

    public int findGreaterPattern(List<Combo> array, List<Combo> previousPattern) {
      Combo fst = previousPattern.get(0);
      int newPatternEnd = 0;
      for (int i = previousPattern.size(); i < array.size(); ++i) {
        if (array.get(i) == fst) {
          newPatternEnd = i;
          break;
        }
      }
      return newPatternEnd;
    }
        //recognize pattern
    public List<Combo> RecognizePattern(List<Combo> array, List<Combo> subarray) {

        if (matchesWholeArray(array, subarray)) {
          return subarray;
        } else {
          return RecognizePattern(array, array.subList(0, findGreaterPattern(array, subarray)));
        }
    }



    @Override
    public void onReceive(Object message) throws Exception {

        if ( message instanceof SensorEvent ) {
            handleSensorEvent((SensorEvent) message);

        } else if ( message instanceof PenaltyMessage) {
            handlePenaltyMessage ();

        } else if ( message instanceof RaceStartMessage) {
            handleRaceStart();

          } else if(message instanceof RoundTimeMessage) {
            handleRoundTime((RoundTimeMessage) message);

        } else {
            unhandled(message);
        }
    }

    private void handleRoundTime( RoundTimeMessage message) {
      cap = count;
      //sequence = new String[cap]; //this is how many turns there are
      position = 0;

      System.out.println(sequence);
    }

    private void handleRaceStart() {
        currentPower = 0;
        lastIncrease = 0;
        maxPower = 180; // Max for this phase;
        probing = true;
        gyrozHistory = new FloatingHistory(8);
    }

    private void handlePenaltyMessage() {
        currentPower -= 10;
        kobayashi.tell(new PowerAction((int)currentPower), getSelf());
        probing = false;
    }

    /**
     * Strategy: increase quickly when standing still to overcome haptic friction
     * then increase slowly. Probing phase will be ended by the first penalty
     * @param message the sensor event coming in
     */
    private void handleSensorEvent(SensorEvent message) {

        double gyrz = gyrozHistory.shift(message.getG()[2]);

        // determines if the car is turning left or right
        if (gyrz <= 10 && gyrz >= -10) {
          turn = "start up";
        } else if (gyrz <= 500 && gyrz >= -500) {
          turn = "straight";
          turnTemp = 1;
          if (turnTemp != previousTemp) {
          sequence.add(Combo.STRAIGHT);
          }

        } else if (gyrz > 500) {
          turn = "turn right";
          turnTemp = 2;
          if (turnTemp != previousTemp) {
          sequence.add(Combo.RIGHT);
          }


        } else if (gyrz < -1000) {
          turn = "turn left";
          turnTemp = 3;
          if (turnTemp != previousTemp) {
          sequence.add(Combo.LEFT);
          }

        } else {
          turn = "noise";
        }

        previousTemp = turnTemp;

        // if (cap > count) {  // if the number of cap is not yet met then add one to count
        //   count += 1;
        // }

         //show ((int)gyrz);
         System.out.println(turn);
         System.out.println(sequence);

        if (probing) {
            if (iAmStillStanding()) {
                increase(0.5);
            } else if (message.getTimeStamp() > lastIncrease + duration) {
                lastIncrease = message.getTimeStamp();
                increase(10);
            }
        }

        position += 1;
        kobayashi.tell(new PowerAction((int)currentPower), getSelf());
    }

    private int increase ( double val ) {
        currentPower = Math.min ( currentPower + val, maxPower );
        return (int)currentPower;
    }

    private boolean iAmStillStanding() {
        return gyrozHistory.currentStDev() < 3;
    }

    private void show(int gyr2) {
        int scale = 120 * (gyr2 - (-10000) ) / 20000;
        System.out.println(StringUtils.repeat(" ", scale) + gyr2);
    }


}
