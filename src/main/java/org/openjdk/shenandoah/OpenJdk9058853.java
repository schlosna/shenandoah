package org.openjdk.shenandoah;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.dsl.Disruptor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class OpenJdk9058853 {
    private OpenJdk9058853() {}

    public static void main(String[] args) throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(1, new NonDaemonThreadFactory("publisher-%d"));
        repro(new NonDaemonThreadFactory("event-"), executorService);
    }

    public static void repro(ThreadFactory threadFactory, ExecutorService publisherExecutor)
            throws ExecutionException, InterruptedException {
        final long numEvents = 10000L;
        publisherExecutor.submit(() -> {
            long counter = 0;
            while (true) {
                Disruptor<SomeEnum> disruptor =
                        new Disruptor<>(() -> SomeEnum.INSTANCE, 1024, threadFactory);
                disruptor.handleEventsWith(new EventHandler<SomeEnum>() {

                    private AtomicLong counter = new AtomicLong();
                    private final List<SomeEnum> pending = new ArrayList<>(1024);

                    @Override
                    public void onEvent(SomeEnum event, long sequence, boolean endOfBatch) {
                        pending.add(event);
                        counter.getAndIncrement();
                        if (endOfBatch) {
                            flush();
                        }
                    }

                    private void flush() {
                        pending.clear();
                        if (counter.compareAndSet(numEvents, 0)) {
                            System.out.printf("Consumed %d events%n", numEvents);
                        }
                    }
                });
                disruptor.start();

                disruptor.shutdown();
                counter++;
                if (counter == numEvents) {
                    counter = 0;
                    System.out.printf("Started %d events%n", numEvents);
                }
            }
        }).get();
    }

    private static class NonDaemonThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger();

        NonDaemonThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        }
    }

    enum SomeEnum {
        INSTANCE
    }
}
