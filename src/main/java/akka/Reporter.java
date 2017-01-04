package akka;

import akka.actor.Terminated;
import akka.actor.UntypedActor;
import akka.japi.Procedure;
import akka.pojo.Report;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Reporter extends UntypedActor {

    private final static Logger LOG = LogManager.getFormatterLogger();

    @Override
    public void onReceive(Object message) throws Throwable {
        if (message instanceof Report) {
            Report report = (Report) message;
            LOG.info("Work finished with report: " + report);

//            getContext().become(shuttingDown);
//
//            LOG.info("Terminated reporter: " + getSelf());
        } else {
            unhandled(message);
        }
    }

    Procedure<Object> shuttingDown = message -> {
        if (message.equals("job")) {
            getSender().tell("service unavailable, shutting down", getSelf());
        } else if (message instanceof Terminated) {
            getContext().stop(getSelf());
        }
    };
}
