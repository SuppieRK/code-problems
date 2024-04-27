package io.github.suppierk;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The MoneyTransferProblem class represents a problem of transferring money between two accounts.
 *
 * <p>It demonstrates the possible issues that can arise when multiple threads try to update the
 * same account balance concurrently.
 *
 * <p>In order to solve this problem, you need to implement a mechanism to ensure that the balance
 * update is atomic and thread-safe:
 *
 * <ul>
 *   <li>One approach is to use a synchronization mechanism such as the synchronized keyword or a
 *       lock object to enforce exclusive access to the shared resource.
 *   <li>Another approach is to use optimistic locking, where each thread reads the account balance
 *       and determines if a conflict occurred during the update process. If a conflict is detected,
 *       the thread can retry the operation or take appropriate action to resolve the conflict.
 * </ul>
 */
public final class MoneyTransferProblem {
  private MoneyTransferProblem() {
    // No instance
  }

  /**
   * This class serves as a base class for specific account types and cannot be instantiated
   * directly.
   *
   * <p>Subclasses of AbstractAccount should provide additional functionality specific to their
   * account type.
   */
  public abstract static class AbstractAccount {
    private final int id;

    protected AbstractAccount(int id) {
      this.id = id;
    }

    public int getId() {
      return id;
    }

    public abstract int getBalance();

    public abstract void setBalance(int balance);
  }

  /**
   * Implementations of this interface should provide the necessary mechanisms for locking and
   * unlocking the account.
   */
  public abstract static class LockedAccount extends AbstractAccount {
    protected LockedAccount(int id) {
      super(id);
    }

    public abstract boolean tryLock(long time, TimeUnit unit) throws InterruptedException;

    public abstract void unlock();
  }

  /**
   * This class extends the AbstractAccount class and provides synchronized methods to get and set
   * the balance.
   *
   * <p>Multiple threads accessing the balance of this class will be synchronized, ensuring atomic
   * and thread-safe operations.
   */
  public static class SynchronizedAccount extends AbstractAccount {
    private int balance;

    public SynchronizedAccount(int id, int initialBalance) {
      super(id);
      this.balance = initialBalance;
    }

    @Override
    public int getBalance() {
      synchronized (this) {
        return balance;
      }
    }

    @Override
    public void setBalance(int balance) {
      synchronized (this) {
        this.balance = balance;
      }
    }
  }

  /**
   * This class extends the AbstractAccount class and provides methods to get and set the balance
   * supported by ReentrantLock.
   *
   * <p>Multiple threads accessing the balance of this class will be synchronized via lock, ensuring
   * atomic and thread-safe operations.
   */
  public static class ReentrantLockedAccount extends LockedAccount {
    private final ReentrantLock lock;
    private int balance;

    public ReentrantLockedAccount(int id, int initialBalance) {
      super(id);
      this.lock = new ReentrantLock();
      this.balance = initialBalance;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
      return lock.tryLock(time, unit);
    }

    @Override
    public void unlock() {
      lock.unlock();
    }

    @Override
    public int getBalance() {
      lock.lock();
      try {
        return balance;
      } finally {
        lock.unlock();
      }
    }

    @Override
    public void setBalance(int balance) {
      lock.lock();
      try {
        this.balance = balance;
      } finally {
        lock.unlock();
      }
    }
  }

  /**
   * This class extends the AbstractAccount class and provides methods to get and set the balance
   * supported by ReentrantReadWriteLock.
   *
   * <p>Multiple threads accessing the balance of this class will be synchronized via lock, ensuring
   * atomic and thread-safe operations.
   */
  public static class ReadWriteLockedAccount extends LockedAccount {
    private final ReentrantReadWriteLock rwLock;
    private int balance;

    public ReadWriteLockedAccount(int id, int initialBalance) {
      super(id);
      this.rwLock = new ReentrantReadWriteLock();
      this.balance = initialBalance;
    }

    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
      return rwLock.writeLock().tryLock(time, unit);
    }

    @Override
    public void unlock() {
      rwLock.writeLock().unlock();
    }

    @Override
    public int getBalance() {
      rwLock.readLock().lock();
      try {
        return balance;
      } finally {
        rwLock.readLock().unlock();
      }
    }

    @Override
    public void setBalance(int balance) {
      rwLock.writeLock().lock();
      try {
        this.balance = balance;
      } finally {
        rwLock.writeLock().unlock();
      }
    }
  }

  /**
   * This class extends the AbstractAccount class and provides methods to get and set the balance
   * supported by AtomicInteger.
   *
   * <p>Multiple threads accessing the balance of this class will be atomic and thread-safe
   * operations because of the AtomicInteger guarantees.
   */
  public static class AtomicAccount extends AbstractAccount {
    private final AtomicInteger balance;

    public AtomicAccount(int id, int initialBalance) {
      super(id);
      this.balance = new AtomicInteger(initialBalance);
    }

    public AtomicInteger getAtomicBalance() {
      return balance;
    }

    @Override
    public int getBalance() {
      return getAtomicBalance().get();
    }

    @Override
    public void setBalance(int balance) {
      getAtomicBalance().set(balance);
    }
  }

  /**
   * Transfers a specified amount from one account to another account.
   *
   * <p>This method acquires object monitors to perform synchronization internally.
   *
   * @param from the source account from which the transfer will be made
   * @param to the target account to which the transfer will be made
   * @param amount the amount to be transferred
   * @return {@code true} if the transfer was successful, {@code false} otherwise
   */
  public static boolean synchronizedTransfer(
      final SynchronizedAccount from, final SynchronizedAccount to, int amount) {
    if (amount <= 0 || from.getId() == to.getId()) {
      return false;
    }

    // It is important to acquire locks always in the same order
    SynchronizedAccount first = from;
    SynchronizedAccount second = to;
    if (from.getId() < to.getId()) {
      SynchronizedAccount tmp = first;
      first = second;
      second = tmp;
    }

    // Creating synchronized blocks using method arguments generally is not recommended
    // This may lead to a number of unforeseen issues, because it uses object intrinsic lock.
    // A slightly better option would be to use / expose some private final Object for locking.
    // However, that option is also not completely fail-safe.
    synchronized (first) {
      synchronized (second) {
        if (from.getBalance() < amount) {
          return false;
        }

        from.setBalance(from.getBalance() - amount);
        to.setBalance(to.getBalance() + amount);
        return true;
      }
    }
  }

  /**
   * Transfers a specified amount from one account to another account.
   *
   * <p>This method is synchronized to ensure thread-safe and atomic operations during the transfer.
   *
   * @param from the source account from which the transfer will be made
   * @param to the target account to which the transfer will be made
   * @param amount the amount to be transferred
   * @return {@code true} if the transfer was successful, {@code false} otherwise
   */
  public static boolean lockedTransfer(
      final LockedAccount from, final LockedAccount to, int amount) {
    if (amount <= 0 || from.getId() == to.getId() || from.getBalance() < amount) {
      return false;
    }

    // It is important to acquire locks always in the same order
    LockedAccount first = from;
    LockedAccount second = to;
    if (from.getId() < to.getId()) {
      LockedAccount tmp = first;
      first = second;
      second = tmp;
    }

    // Here we are repeatedly trying to acquire locks from both accounts.
    // Once both locks are acquired, we perform the transfer.
    //
    // This option is slightly better than the synchronized with more granularity.
    // However, there are also several issues with this approach:
    // 1. Account class exposes more methods that we might like it to.
    // 2. Still not fail-safe in terms of locks manipulations.
    boolean firstAcquired = false;
    boolean secondAcquired = false;
    while (!firstAcquired && !secondAcquired) {
      try {
        firstAcquired = first.tryLock(1, TimeUnit.MILLISECONDS);
        secondAcquired = second.tryLock(1, TimeUnit.MILLISECONDS);

        if (firstAcquired && secondAcquired) {
          from.setBalance(from.getBalance() - amount);
          to.setBalance(to.getBalance() + amount);
          break;
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      } finally {
        if (firstAcquired) {
          first.unlock();
          firstAcquired = false;
        }

        if (secondAcquired) {
          second.unlock();
          secondAcquired = false;
        }
      }
    }

    return true;
  }

  /**
   * Transfers a specified amount from one account to another account.
   *
   * <p>This method is synchronized to ensure thread-safe and atomic operations during the transfer.
   *
   * @param from the source account from which the transfer will be made
   * @param to the target account to which the transfer will be made
   * @param amount the amount to be transferred
   * @return {@code true} if the transfer was successful, {@code false} otherwise
   */
  public static boolean atomicTransfer(
      final AtomicAccount from, final AtomicAccount to, int amount) {
    if (amount <= 0 || from.getId() == to.getId()) {
      return false;
    }

    // Here we are using Compare-And-Swap feature to perform the transfer.
    // 1. Invariant `from.getBalance() < amount` is only applied to one account.
    // This means that if it does not hold, we do not have to touch the second account.
    // 2. Once money was withdrawn, we have to commit to deposit them to another account.
    //
    // Given that, first we attempt to withdraw money from the first account using CAS.
    // If succeeded - we attempt to add money to the second account.
    int fromCurrent;
    int fromDesired;
    do {
      fromCurrent = from.getAtomicBalance().get();

      if (fromCurrent < amount) {
        return false;
      }

      fromDesired = fromCurrent - amount;
    } while (!from.getAtomicBalance().compareAndSet(fromCurrent, fromDesired));

    int toCurrent;
    int toDesired;
    do {
      toCurrent = to.getAtomicBalance().get();
      toDesired = toCurrent + amount;
    } while (!to.getAtomicBalance().compareAndSet(toCurrent, toDesired));

    return true;
  }
}
