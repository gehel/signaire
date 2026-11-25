package fr.ledcom.signaire.extractor;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import net.jcip.annotations.ThreadSafe;

/** Threads named after their work ("extraction-city-1"), daemons: they never keep the JVM running. */
@ThreadSafe
final class NamedThreads implements ThreadFactory {

    private final String prefix;
    private final AtomicInteger count = new AtomicInteger();

    NamedThreads(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable work) {
        Thread thread = new Thread(work, prefix + "-" + count.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }
}
