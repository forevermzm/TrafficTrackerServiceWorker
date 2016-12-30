package akka;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import akka.pojo.CalculateWork;
import akka.pojo.CalculateWorkResult;
import akka.pojo.ImmutableReport;
import akka.pojo.Report;
import akka.pojo.SnapshotResult;
import akka.routing.ActorRefRoutee;
import akka.routing.RoundRobinRoutingLogic;
import akka.routing.Routee;
import akka.routing.Router;
import com.google.common.collect.Lists;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pojo.json.SrcDestPair;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class Master extends UntypedActor {
    public static final String START_WORK = "START_WORK";

    public static final String SNAPSHOT_WORK = "SNAPSHOT";

    private static final Logger LOG = LogManager.getFormatterLogger();

    private static final int BATCH_SIZE = 5;

    private final ActorRef reporter;

    private final ImmutableReport.Builder reportBuilder;

    private Router router;

    private final Instant masterStartTime;

    private Instant stepStartTime;

    private Set<SrcDestPair> finishedPairs;

    private SnapshotResult snapshotResult;

    private List<ActorRef> jobWorkers;

    public Master(int workers, ActorRef reporter) {
        this.reporter = reporter;

        List<Routee> routees = new ArrayList<>();
        jobWorkers = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            ActorRef r = getContext().actorOf(Props.create(Worker.class));
            jobWorkers.add(r);
            getContext().watch(r);
            routees.add(new ActorRefRoutee(r));
        }

        masterStartTime = Instant.now();
        router = new Router(new RoundRobinRoutingLogic(), routees);
        reportBuilder = Report.builder();
        finishedPairs = new HashSet<>();
    }

    public static Props props(final int workers, final ActorRef reporter) {
        return Props.create(new Creator<Master>() {
            private static final long serialVersionUID = 1L;

            @Override
            public Master create() throws Exception {
                return new Master(workers, reporter);
            }
        });
    }

    @Override
    public void onReceive(Object message) throws Throwable {
        if (Objects.equals(message, START_WORK)) {
            stepStartTime = Instant.now();
            LOG.info("Starting master at: " + stepStartTime);
            scheduleSnapshot();
        } else if (message instanceof SnapshotResult) {
            Instant snapshotFinished = Instant.now();
            LOG.info("Snapshot job finished at: " + snapshotFinished);
            reportBuilder.putStepDurations(SNAPSHOT_WORK, Duration.between(stepStartTime, snapshotFinished));
            snapshotResult = (SnapshotResult) message;
            if (noWorkNeeded(snapshotResult)) {
                reportAndShutdown();
            }
            stepStartTime = Instant.now();
            scheduleCalculateWork(snapshotResult);
        } else if (message instanceof CalculateWorkResult) {
            CalculateWorkResult workResult = (CalculateWorkResult) message;
            finishedPairs.addAll(workResult.getAddressPairs());
            if (finishedPairs.size() == snapshotResult.getAddressPairs().size()) {
                Instant calculateFinished = Instant.now();
                LOG.info("Calculate job finished at: " + calculateFinished);
                reportBuilder.putStepDurations("CALCULATE", Duration.between(stepStartTime, calculateFinished));
                reportAndShutdown();
            }
        } else if (message instanceof ReceiveTimeout) {
            reportBuilder.withTimedout(true);
            reportAndShutdown();
        } else if (message instanceof Terminated) {
            router = router.removeRoutee(((Terminated) message).actor());
            ActorRef r = getContext().actorOf(Props.create(Worker.class));
            getContext().watch(r);
            router = router.addRoutee(new ActorRefRoutee(r));
        } else {
            unhandled(message);
        }
    }

    private void scheduleCalculateWork(SnapshotResult snapshot) {
        for (List<SrcDestPair> pairs : Lists.partition(snapshot.getAddressPairs(), BATCH_SIZE)) {
            router.route(new CalculateWork(pairs), getSelf());
        }
        getContext().setReceiveTimeout(scala.concurrent.duration.Duration.create("150 second"));
    }

    private void reportAndShutdown() {
        reportBuilder.putStepDurations("TOTAL", Duration.between(masterStartTime, Instant.now()));

        reporter.tell(reportBuilder.build(), getSelf());

        for (ActorRef worker : jobWorkers) {
            getContext().stop(worker);
        }

        getContext().stop(getSelf());
    }

    private boolean noWorkNeeded(SnapshotResult snapshot) {
        return snapshot.getAddressPairs()
                .size() == 0;
    }

    private void scheduleSnapshot() {
        router.route(SNAPSHOT_WORK, getSelf());
        getContext().setReceiveTimeout(scala.concurrent.duration.Duration.create("30 second"));
    }
}
