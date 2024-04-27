package io.github.suppierk;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CyclicBarrierProblemTest {
  static final int ATTENDEES_UPPER_BOUND = 6;

  static final int ATTENDEE_TRAVEL_TIME_UPPER_BOUND_MS = 1_000;

  int numberOfAttendees;
  AtomicBoolean meetingConcluded;
  CyclicBarrierProblem.ImportantMeeting meeting;

  @BeforeEach
  void setUp() {
    // How many people we expect on the meeting
    numberOfAttendees =
        ThreadLocalRandom.current().nextInt(ATTENDEES_UPPER_BOUND / 2, ATTENDEES_UPPER_BOUND);

    // We use this variable to see if the meeting took place within a reasonable amount of time
    meetingConcluded = new AtomicBoolean(false);

    // Create a meeting to attend
    meeting =
        new CyclicBarrierProblem.ImportantMeeting(
            numberOfAttendees,
            () -> {
              // When the barrier is tripped - all attendees arrived to the meeting
              meetingConcluded.set(true);
              System.out.println("Meeting concluded!");
            });
  }

  @Test
  void meetingTest() {
    // Create our attendees
    final AtomicReference<ExecutorService> executorService = new AtomicReference<>(null);
    try {
      executorService.set(Executors.newFixedThreadPool(numberOfAttendees));

      CompletableFuture.allOf(
              IntStream.range(0, numberOfAttendees)
                  .mapToObj(
                      value ->
                          CompletableFuture.runAsync(
                              () -> {
                                try {
                                  int travelTimeMs =
                                      ThreadLocalRandom.current()
                                          .nextInt(0, ATTENDEE_TRAVEL_TIME_UPPER_BOUND_MS);

                                  System.out.printf(
                                      "Attendee #%s is travelling to the meeting, estimated to arrive in %d ms%n",
                                      value, travelTimeMs);

                                  Thread.sleep(travelTimeMs);

                                  // Attendee now must wait until others will arrive
                                  int arrivalIndex = meeting.await();

                                  System.out.printf(
                                      "Attendee #%s has arrived %s to the meeting%n",
                                      value, arrivalIndex);
                                } catch (InterruptedException e) {
                                  Thread.currentThread().interrupt();
                                  throw new IllegalStateException("Attendee crashed a car", e);
                                } catch (BrokenBarrierException e) {
                                  throw new IllegalStateException(
                                      "Meeting did not concluded, some attendee broke the car or left early or meeting was cancelled",
                                      e);
                                }
                              },
                              executorService.get()))
                  .toArray(CompletableFuture[]::new))
          .join();
    } finally {
      if (executorService.get() != null) {
        executorService.get().shutdownNow();
      }
    }

    // Let's see if the meeting took place!
    Awaitility.await()
        .atMost(Duration.ofSeconds(3))
        .untilAsserted(
            () ->
                assertTrue(meetingConcluded.get(), "We expected to meet and make a decision! :("));
  }

  @Test
  void meetingTest_attendeeOverslept() {
    Runnable oversleptAttendee =
        () -> {
          try {
            int travelTimeMs =
                ThreadLocalRandom.current().nextInt(0, ATTENDEE_TRAVEL_TIME_UPPER_BOUND_MS);

            System.out.printf(
                "Overslept attendee is travelling to the meeting, estimated to arrive in %d ms%n",
                travelTimeMs);

            Thread.sleep(travelTimeMs);

            // Attendee now must wait until others will arrive
            int arrivalIndex =
                meeting.await(3 * ATTENDEE_TRAVEL_TIME_UPPER_BOUND_MS, TimeUnit.MILLISECONDS);

            System.out.printf("Overslept attendee has arrived %s to the meeting%n", arrivalIndex);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Attendee crashed a car", e);
          } catch (BrokenBarrierException e) {
            throw new IllegalStateException(
                "Meeting did not concluded, some attendee broke the car or left early or meeting was cancelled",
                e);
          } catch (TimeoutException e) {
            throw new IllegalStateException(
                "Meeting did not concluded, we missed all other attendees", e);
          }
        };

    // Now let's see what will happen if there was one attendee who overslept
    try {
      CompletableFuture.runAsync(oversleptAttendee).join();
      fail("Overslept attendee has to leave the meeting because no one else arrived");
    } catch (CompletionException ce) {
      assertEquals(
          TimeoutException.class,
          ce.getCause().getCause().getClass(),
          "Root cause must be TimeoutException");
      System.out.println(ce.getCause().getMessage());
    }
  }

  @Test
  void meetingTest_meetingCancelled() {
    Runnable attendee =
        () -> {
          try {
            int travelTimeMs =
                ThreadLocalRandom.current().nextInt(0, ATTENDEE_TRAVEL_TIME_UPPER_BOUND_MS);

            System.out.printf(
                "Attendee is travelling to the meeting, estimated to arrive in %d ms%n",
                travelTimeMs);

            Thread.sleep(travelTimeMs);

            // Attendee now must wait until others will arrive
            int arrivalIndex =
                meeting.await(3 * ATTENDEE_TRAVEL_TIME_UPPER_BOUND_MS, TimeUnit.MILLISECONDS);

            System.out.printf("Attendee has arrived %s to the meeting%n", arrivalIndex);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Attendee crashed a car", e);
          } catch (BrokenBarrierException e) {
            throw new IllegalStateException(
                "Meeting did not concluded, some attendee broke the car or left early or meeting was cancelled",
                e);
          } catch (TimeoutException e) {
            throw new IllegalStateException(
                "Meeting did not concluded, we missed all other attendees", e);
          }
        };

    Runnable cancellation =
        () -> {
          try {
            Thread.sleep(ATTENDEE_TRAVEL_TIME_UPPER_BOUND_MS);
            meeting.reset();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to cancel the meeting", e);
          }
        };

    // Now let's see what will happen if there was one attendee who overslept
    try {
      CompletableFuture.allOf(
              CompletableFuture.runAsync(attendee), CompletableFuture.runAsync(cancellation))
          .join();
      fail("Attendee has to leave the meeting because the meeting was cancelled");
    } catch (CompletionException ce) {
      assertEquals(
          BrokenBarrierException.class,
          ce.getCause().getCause().getClass(),
          "Root cause must be BrokenBarrierException");
      System.out.println(ce.getCause().getMessage());
    }
  }
}
