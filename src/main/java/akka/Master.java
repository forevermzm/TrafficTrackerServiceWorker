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
import java.util.stream.Collectors;

public class Master extends UntypedActor {
    public static final String START_WORK = "START_WORK";

    public static final String SNAPSHOT_WORK = "SNAPSHOT";

    private static final Logger LOG = LogManager.getFormatterLogger();

    private static final int BATCH_SIZE = 5;

    private final ActorRef reporter;

    private ImmutableReport.Builder reportBuilder;

    private Router router;

    private Instant masterStartTime;

    private Instant stepStartTime;

    private Set<SrcDestPair> finishedPairs;

    private SnapshotResult snapshotResult;

    private List<ActorRef> jobWorkers;

    public Master(int workers, ActorRef reporter) {
        this.reporter = reporter;

        jobWorkers = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            ActorRef r = getContext().actorOf(Props.create(Worker.class));
            jobWorkers.add(r);
        }

        jobWorkers.stream().forEach(getContext()::watch);
        List<Routee> routees = jobWorkers.stream()
                .map(a -> new ActorRefRoutee(a))
                .collect(Collectors.toList());

        router = new Router(new RoundRobinRoutingLogic(), routees);
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
            masterStartTime = Instant.now();
            stepStartTime = Instant.now();
            LOG.info("Starting master at: " + stepStartTime);
            scheduleSnapshot();
        } else if (message instanceof SnapshotResult) {
            Instant snapshotFinished = Instant.now();
            LOG.info("Snapshot job finished at: " + snapshotFinished + " with snapshot: " + message);
            reportBuilder.putStepDurations(SNAPSHOT_WORK, Duration.between(stepStartTime, snapshotFinished));
            snapshotResult = (SnapshotResult) message;
            if (noWorkNeeded(snapshotResult)) {
                LOG.info("No work is needed for this round.");
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
                reportBuilder.addAllCalculatedPairs(finishedPairs);
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
        finishedPairs = new HashSet<>();
        for (List<SrcDestPair> pairs : Lists.partition(snapshot.getAddressPairs(), BATCH_SIZE)) {
            router.route(new CalculateWork(pairs), getSelf());
        }
        getContext().setReceiveTimeout(scala.concurrent.duration.Duration.create("150 second"));
    }

    private void reportAndShutdown() {
        reportBuilder.putStepDurations("TOTAL", Duration.between(masterStartTime, Instant.now()));

        reporter.tell(reportBuilder.build(), getSelf());

//        for (ActorRef worker: jobWorkers) {
//            getContext().stop(worker);
//        }
//
//        getContext().stop(getSelf());
    }

    private boolean noWorkNeeded(SnapshotResult snapshot) {
        return snapshot.getAddressPairs()
                .size() == 0;
    }

    private void scheduleSnapshot() {
        reportBuilder = Report.builder();
        router.route(SNAPSHOT_WORK, getSelf());
        getContext().setReceiveTimeout(scala.concurrent.duration.Duration.create("30 second"));
    }
}
