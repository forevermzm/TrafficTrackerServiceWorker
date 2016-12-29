package akka;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
import akka.japi.Creator;
import akka.pojo.MasterWork;
import akka.pojo.Report;
import akka.pojo.WorkerWork;
import akka.pojo.WorkerWorkResult;
import akka.routing.ActorRefRoutee;
import akka.routing.RoundRobinRoutingLogic;
import akka.routing.Routee;
import akka.routing.Router;
import dao.SourceDestinationTableDAO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Master extends UntypedActor {
    private final static Logger LOG = LogManager.getFormatterLogger();

    private final int workers;

    private final AtomicInteger finished = new AtomicInteger(0);

    private final ActorRef reporter;

    @Autowired
    private SourceDestinationTableDAO dao;

    private Router router;

    private Instant startTime;

    public Master(int workers, ActorRef reporter) {
        this.workers = workers;
        this.reporter = reporter;

        List<Routee> routees = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            ActorRef r = getContext().actorOf(Props.create(Worker.class));
            getContext().watch(r);
            routees.add(new ActorRefRoutee(r));
        }

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
        if (message instanceof MasterWork) {
            startTime = Instant.now();
            for (int i = 0; i < workers; i ++) {
                router.route(new WorkerWork(), getSelf());
            }
        } else if (message instanceof WorkerWorkResult) {
            LOG.info("Received work done: " + message + " for: " + finished.incrementAndGet());
            if (finished.get() == workers) {
                Duration duration = Duration.between(startTime, Instant.now());
                Report report = Report.builder()
                        .withDuration(duration)
                        .withSuccessCount(finished.get())
                        .build();

                reporter.tell(report, getSelf());

                getContext().stop(getSelf());
            }
        } else if (message instanceof Terminated) {
            router = router.removeRoutee(((Terminated) message).actor());
            ActorRef r = getContext().actorOf(Props.create(Worker.class));
            getContext().watch(r);
            router = router.addRoutee(new ActorRefRoutee(r));
        } else {
            unhandled(message);
        }
    }
}
