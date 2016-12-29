package main;

import akka.Master;
import akka.Reporter;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pojo.MasterWork;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class TrafficStatusSystem {
    private static final Logger LOG = LogManager.getFormatterLogger();

    private static final int INITIAL_DELAY = 0;
    private static final int SCHEDULE_INTERVAL = 3;

    private final int numWorkers;

    private final ScheduledExecutorService scheduler;

    public TrafficStatusSystem(int numWorkers, ScheduledExecutorService scheduler) {
        checkArgument(numWorkers > 0);
        this.numWorkers = numWorkers;
        this.scheduler = checkNotNull(scheduler);
    }

    public void start() {
        scheduler.scheduleAtFixedRate(() -> doWork(), INITIAL_DELAY, SCHEDULE_INTERVAL, TimeUnit.SECONDS);
    }

    private void doWork() {
        try {
            LOG.info("Starting traffic status system.");

            ActorSystem system = ActorSystem.create("TrafficStatusSystem");

            final ActorRef reporter = system.actorOf(Props.create(Reporter.class), "reporter");

            ActorRef master = system.actorOf(Master.props(numWorkers, reporter));

            master.tell(new MasterWork(), reporter);
        } catch (Exception e) {
            // Don't rethrow the exception.
            LOG.error("Caught exception: ", e);
        } catch (Throwable t) {
            LOG.error("Caught throwable: ", t);
        }
    }
}
