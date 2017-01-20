package main;

import akka.Master;
import akka.Reporter;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class TrafficStatusSystem {
    private static final Logger LOG = LogManager.getFormatterLogger();

    private static final int INITIAL_DELAY = 0;
    private static final int SCHEDULE_INTERVAL = 1;

    private final int numWorkers;

    private final ScheduledExecutorService scheduler;

    private final ActorSystem actorSystem;
    private final ActorRef reporter;
    private final ActorRef master;

    private final Random random;

    public TrafficStatusSystem(int numWorkers, ScheduledExecutorService scheduler) {
        checkArgument(numWorkers > 0);
        this.numWorkers = numWorkers;
        this.scheduler = checkNotNull(scheduler);

        this.actorSystem = ActorSystem.create("TrafficStatusSystem");
        this.reporter = actorSystem.actorOf(Props.create(Reporter.class), "reporter");
        master = actorSystem.actorOf(Master.props(numWorkers, reporter));
        random = new Random();
    }

    public void start() {
        LOG.info("Starting traffic status system.");

        scheduler.scheduleAtFixedRate(() -> doWork(), INITIAL_DELAY, SCHEDULE_INTERVAL, TimeUnit.MINUTES);
    }

    private void doWork() {
        try {
            // Add a jitter within 10 seconds.
            Thread.sleep(random.nextInt(10000));

            master.tell(Master.START_WORK, reporter);
        } catch (Exception e) {
            // Don't rethrow the exception.
            LOG.error("Caught exception: ", e);
        } catch (Throwable t) {
            // Something bad happened. Log it and rethrow.
            LOG.error("Caught throwable: ", t);
        }
    }
}
