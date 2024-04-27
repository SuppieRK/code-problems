package io.github.suppierk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MoneyTransferProblemTest {
  // Do multiple iterations to avoid making assumptions based on cold JVM launch
  static final int TEST_SUITE_ITERATIONS = 10;

  static final int NUMBER_OF_ACCOUNTS = 1_000;

  // Balance thread contention with operations through these variables
  static final int NUMBER_OF_THREADS = 1_000;
  static final int NUMBER_OF_OPERATIONS_PER_THREAD = 10_000;

  static final int BALANCE_UPPER_BOUND = 100_000;
  static final int TRANSFER_UPPER_BOUND = 100;

  @ParameterizedTest
  @MethodSource("arguments")
  void transferTest(
      List<MoneyTransferProblem.AbstractAccount> accounts,
      TrieFunction<
              MoneyTransferProblem.AbstractAccount,
              MoneyTransferProblem.AbstractAccount,
              Integer,
              Boolean>
          transferResultSupplier) {
    final List<Integer> balanceSnapshot =
        accounts.stream().map(MoneyTransferProblem.AbstractAccount::getBalance).toList();

    System.out.printf(
        "Testing %s account type transfers%n", accounts.get(0).getClass().getSimpleName());

    final DoubleAdder totalAverageTime = new DoubleAdder();

    for (int iteration = 0; iteration < TEST_SUITE_ITERATIONS; iteration++) {
      // Counters
      final long expectedSum =
          accounts.stream().mapToLong(MoneyTransferProblem.AbstractAccount::getBalance).sum();
      final LongAdder operations = new LongAdder();
      final LongAdder successfulOperations = new LongAdder();
      final LongAdder totalTime = new LongAdder();

      // Defining single thread workload
      Runnable task =
          () -> {
            for (int i = 0; i < NUMBER_OF_OPERATIONS_PER_THREAD; i++) {
              final int amount = ThreadLocalRandom.current().nextInt(0, TRANSFER_UPPER_BOUND);
              final MoneyTransferProblem.AbstractAccount from =
                  accounts.get(ThreadLocalRandom.current().nextInt(0, NUMBER_OF_ACCOUNTS));
              final MoneyTransferProblem.AbstractAccount to =
                  accounts.get(ThreadLocalRandom.current().nextInt(0, NUMBER_OF_ACCOUNTS));

              long start = System.nanoTime();
              boolean success = transferResultSupplier.apply(from, to, amount);
              totalTime.add(System.nanoTime() - start);

              if (success) {
                successfulOperations.increment();
              }

              operations.increment();
            }
          };

      // Launching all tasks simultaneously
      final AtomicReference<ExecutorService> executor = new AtomicReference<>();
      try {
        executor.set(Executors.newFixedThreadPool(NUMBER_OF_THREADS));

        CompletableFuture.allOf(
                IntStream.range(0, NUMBER_OF_THREADS)
                    .mapToObj(value -> CompletableFuture.runAsync(task, executor.get()))
                    .toArray(CompletableFuture[]::new))
            .join();
      } finally {
        if (executor.get() != null) {
          executor.get().shutdownNow();
        }
      }

      // Gathering statistics
      final long totalOperations = operations.sum();
      final long totalSuccessfulOperations = successfulOperations.sum();

      final double averageOperationTimeNs = (double) totalTime.sum() / totalOperations;
      final double successfulOperationPercentage =
          (double) totalSuccessfulOperations / totalOperations;

      totalAverageTime.add(averageOperationTimeNs);

      // Assertion
      final long actualSum =
          accounts.stream().mapToLong(MoneyTransferProblem.AbstractAccount::getBalance).sum();
      assertEquals(
          expectedSum,
          actualSum,
          "Total balance of all accounts after %s transfers must remain intact"
              .formatted(totalOperations));

      boolean atLeastOneAccountBalanceChanged = false;
      for (int i = 0; i < balanceSnapshot.size(); i++) {
        if (balanceSnapshot.get(i) != accounts.get(i).getBalance()) {
          atLeastOneAccountBalanceChanged = true;
          break;
        }
      }
      assertTrue(atLeastOneAccountBalanceChanged, "Transfers must have changed account balances");

      // Stats printout
      System.out.printf(
          "Iteration %02d |> Average execution time %,.3f ns after %,d transfers with %,d (~%.0f%%) successful transfers%n",
          iteration + 1,
          averageOperationTimeNs,
          totalOperations,
          totalSuccessfulOperations,
          successfulOperationPercentage * 100);
    }

    System.out.printf(
        "Test summary |> Average execution time %,.3f ns%n%n",
        totalAverageTime.sum() / TEST_SUITE_ITERATIONS);
  }

  /**
   * @return a Stream of Arguments for the transferTest method.
   */
  static Stream<Arguments> arguments() {
    return Stream.of(
        Arguments.of(
            Named.of(
                "For synchronized accounts",
                IntStream.range(0, NUMBER_OF_ACCOUNTS)
                    .mapToObj(
                        value ->
                            new MoneyTransferProblem.SynchronizedAccount(
                                value, ThreadLocalRandom.current().nextInt(0, BALANCE_UPPER_BOUND)))
                    .toList()),
            Named.of(
                "transfer must pass tests",
                (TrieFunction<
                        MoneyTransferProblem.SynchronizedAccount,
                        MoneyTransferProblem.SynchronizedAccount,
                        Integer,
                        Boolean>)
                    MoneyTransferProblem::synchronizedTransfer)),
        Arguments.of(
            Named.of(
                "For ReentrantLock locked accounts without fair sync policy",
                IntStream.range(0, NUMBER_OF_ACCOUNTS)
                    .mapToObj(
                        value ->
                            new MoneyTransferProblem.ReentrantLockedAccount(
                                value, ThreadLocalRandom.current().nextInt(0, BALANCE_UPPER_BOUND)))
                    .toList()),
            Named.of(
                "transfer must pass tests",
                (TrieFunction<
                        MoneyTransferProblem.ReentrantLockedAccount,
                        MoneyTransferProblem.ReentrantLockedAccount,
                        Integer,
                        Boolean>)
                    MoneyTransferProblem::lockedTransfer)),
        Arguments.of(
            Named.of(
                "For ReentrantReadWriteLock locked accounts without fair sync policy",
                IntStream.range(0, NUMBER_OF_ACCOUNTS)
                    .mapToObj(
                        value ->
                            new MoneyTransferProblem.ReadWriteLockedAccount(
                                value, ThreadLocalRandom.current().nextInt(0, BALANCE_UPPER_BOUND)))
                    .toList()),
            Named.of(
                "transfer must pass tests",
                (TrieFunction<
                        MoneyTransferProblem.ReadWriteLockedAccount,
                        MoneyTransferProblem.ReadWriteLockedAccount,
                        Integer,
                        Boolean>)
                    MoneyTransferProblem::lockedTransfer)),
        Arguments.of(
            Named.of(
                "For atomic accounts",
                IntStream.range(0, NUMBER_OF_ACCOUNTS)
                    .mapToObj(
                        value ->
                            new MoneyTransferProblem.AtomicAccount(
                                value, ThreadLocalRandom.current().nextInt(0, BALANCE_UPPER_BOUND)))
                    .toList()),
            Named.of(
                "transfer must pass tests",
                (TrieFunction<
                        MoneyTransferProblem.AtomicAccount,
                        MoneyTransferProblem.AtomicAccount,
                        Integer,
                        Boolean>)
                    MoneyTransferProblem::atomicTransfer)));
  }

  /**
   * Represents a function that accepts three arguments and produces a result. This is the
   * three-arity specialization of {@link Function}.
   *
   * <p>This is a <a href="package-summary.html">functional interface</a> whose functional method is
   * {@link #apply(Object, Object, Object)}.
   *
   * @param <T> the type of the first argument to the function
   * @param <U> the type of the second argument to the function
   * @param <V> the type of the third argument to the function
   * @param <R> the type of the result
   * @see Function
   * @see BiFunction
   */
  @FunctionalInterface
  interface TrieFunction<T, U, V, R> {
    /**
     * Applies this function to the given arguments.
     *
     * @param t the first function argument
     * @param u the second function argument
     * @param v the third function argument
     * @return the function result
     */
    R apply(T t, U u, V v);
  }
}
