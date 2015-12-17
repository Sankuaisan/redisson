package org.redisson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.redisson.core.RSemaphore;

public class RedissonSemaphoreTest extends BaseConcurrentTest {

    @Test
    public void testBlockingAcquire() throws InterruptedException {
        RSemaphore s = redisson.getSemaphore("test");
        s.setPermits(1);
        s.acquire();

        Thread t = new Thread() {
            @Override
            public void run() {
                RSemaphore s = redisson.getSemaphore("test");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                s.release();
            }
        };

        t.start();

        assertThat(s.availablePermits()).isEqualTo(0);
        s.acquire();
        assertThat(s.tryAcquire()).isFalse();
        assertThat(s.availablePermits()).isEqualTo(0);
    }

    @Test
    public void testBlockingNAcquire() throws InterruptedException {
        RSemaphore s = redisson.getSemaphore("test");
        s.setPermits(5);
        s.acquire(2);

        Thread t = new Thread() {
            @Override
            public void run() {
                RSemaphore s = redisson.getSemaphore("test");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                s.release();
            }
        };

        assertThat(s.availablePermits()).isEqualTo(3);
        t.start();

        s.acquire(4);
        assertThat(s.availablePermits()).isEqualTo(0);
    }

    @Test
    public void testTryNAcquire() throws InterruptedException {
        RSemaphore s = redisson.getSemaphore("test");
        s.setPermits(5);
        assertThat(s.tryAcquire(2)).isTrue();

        Thread t = new Thread() {
            @Override
            public void run() {
                RSemaphore s = redisson.getSemaphore("test");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                s.release();
            }
        };

        assertThat(s.tryAcquire(4)).isFalse();

        t.start();
        t.join(1);

        long startTime = System.currentTimeMillis();
        assertThat(s.tryAcquire(4, 1, TimeUnit.SECONDS)).isTrue();
        assertThat(System.currentTimeMillis() - startTime).isBetween(900L, 1020L);
        assertThat(s.availablePermits()).isEqualTo(0);
    }

    @Test
    public void testReleaseWithoutPermits() {
        RSemaphore s = redisson.getSemaphore("test");
        s.release();

        assertThat(s.availablePermits()).isEqualTo(1);
    }

    @Test
    public void testDrainPermits() throws InterruptedException {
        RSemaphore s = redisson.getSemaphore("test");
        s.setPermits(10);
        s.acquire(3);

        assertThat(s.drainPermits()).isEqualTo(7);
        assertThat(s.availablePermits()).isEqualTo(0);
    }

    @Test
    public void testReleaseAcquire() throws InterruptedException {
        RSemaphore s = redisson.getSemaphore("test");
        s.setPermits(10);
        s.acquire();
        assertThat(s.availablePermits()).isEqualTo(9);
        s.release();
        assertThat(s.availablePermits()).isEqualTo(10);
        s.acquire(5);
        assertThat(s.availablePermits()).isEqualTo(5);
        s.release(5);
        assertThat(s.availablePermits()).isEqualTo(10);
    }


    @Test
    public void testConcurrency_SingleInstance() throws InterruptedException {
        final AtomicInteger lockedCounter = new AtomicInteger();

        RSemaphore s = redisson.getSemaphore("test");
        s.setPermits(1);

        int iterations = 15;
        testSingleInstanceConcurrency(iterations, new RedissonRunnable() {
            @Override
            public void run(RedissonClient redisson) {
                RSemaphore s = redisson.getSemaphore("test");
                try {
                    s.acquire();
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                int value = lockedCounter.get();
                lockedCounter.set(value + 1);
                s.release();
            }
        });

        assertThat(lockedCounter.get()).isEqualTo(iterations);
    }

    @Test
    public void testConcurrencyLoop_MultiInstance() throws InterruptedException {
        final int iterations = 100;
        final AtomicInteger lockedCounter = new AtomicInteger();

        RSemaphore s = redisson.getSemaphore("test");
        s.setPermits(1);

        testMultiInstanceConcurrency(16, new RedissonRunnable() {
            @Override
            public void run(RedissonClient redisson) {
                for (int i = 0; i < iterations; i++) {
                    try {
                        redisson.getSemaphore("test").acquire();
                    } catch (InterruptedException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    int value = lockedCounter.get();
                    lockedCounter.set(value + 1);
                    redisson.getSemaphore("test").release();
                }
            }
        });

        assertThat(lockedCounter.get()).isEqualTo(16 * iterations);
    }

    @Test
    public void testConcurrency_MultiInstance_1_permits() throws InterruptedException {
        int iterations = 100;
        final AtomicInteger lockedCounter = new AtomicInteger();

        RSemaphore s = redisson.getSemaphore("test");
        s.setPermits(1);

        testMultiInstanceConcurrency(iterations, new RedissonRunnable() {
            @Override
            public void run(RedissonClient redisson) {
                RSemaphore s = redisson.getSemaphore("test");
                try {
                    s.acquire();
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                int value = lockedCounter.get();
                lockedCounter.set(value + 1);
                s.release();
            }
        });

        assertThat(lockedCounter.get()).isEqualTo(iterations);
    }

    @Test
    public void testConcurrency_MultiInstance_10_permits() throws InterruptedException {
        int iterations = 100;
        final AtomicInteger lockedCounter = new AtomicInteger();

        RSemaphore s = redisson.getSemaphore("test");
        s.setPermits(10);

        final CyclicBarrier barrier = new CyclicBarrier(10);
        testMultiInstanceConcurrency(iterations, new RedissonRunnable() {
            @Override
            public void run(RedissonClient redisson) {
                RSemaphore s = redisson.getSemaphore("test");
                try {
                    s.acquire();

                    barrier.await();

                    assertThat(s.availablePermits()).isEqualTo(0);
                    assertThat(s.tryAcquire()).isFalse();

                    Thread.sleep(50);

                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (BrokenBarrierException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                int value = lockedCounter.get();
                lockedCounter.set(value + 1);
                s.release();
            }
        });

        assertThat(lockedCounter.get()).isLessThan(iterations);
    }

}
