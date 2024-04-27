package io.github.suppierk;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Not a problem really - just an example of what this concurrency primitive can achieve.
 *
 * <p>To showcase the class, imagine that we have a meeting to attend, and we are waiting for all
 * attendees to arrive.
 *
 * <p>Attendees arrive at different times and the meeting cannot start until everyone is here.
 *
 * @see <a href="https://www.baeldung.com/java-cyclic-barrier">Baeldung article</a> for more details
 *     on CyclicBarrier
 */
public final class CyclicBarrierProblem {
  private CyclicBarrierProblem() {
    // No instance
  }

  /**
   * The ImportantMeeting class represents a scenario where attendees are waiting for all
   * participants to arrive before starting a meeting.
   *
   * <p>It uses the CyclicBarrier class from the Java Concurrency API to achieve this
   * synchronization.
   */
  public static class ImportantMeeting {
    private final CyclicBarrier meetingBarrier;

    /**
     * CyclicBarrier showcase.
     *
     * @param numberOfAttendees the number of attendees expected for the meeting
     */
    public ImportantMeeting(int numberOfAttendees, Runnable meetingAction) {
      this.meetingBarrier = new CyclicBarrier(numberOfAttendees, meetingAction);
    }

    /**
     * @see CyclicBarrier#await()
     */
    public int await() throws InterruptedException, BrokenBarrierException {
      return meetingBarrier.await();
    }

    /**
     * @see CyclicBarrier#await(long, TimeUnit)
     */
    public int await(long timeout, TimeUnit unit)
        throws InterruptedException, BrokenBarrierException, TimeoutException {
      return meetingBarrier.await(timeout, unit);
    }

    /**
     * @see CyclicBarrier#reset()
     */
    public void reset() {
      meetingBarrier.reset();
    }
  }
}
