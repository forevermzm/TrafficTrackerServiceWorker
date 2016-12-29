package akka;

import akka.actor.UntypedActor;
import akka.pojo.WorkerWork;
import akka.pojo.WorkerWorkResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Worker extends UntypedActor {
    private final static Logger LOG = LogManager.getFormatterLogger();

    @Override
    public void onReceive(Object message) throws Throwable {
        if (message instanceof WorkerWork) {
            Thread.sleep(1000);
            LOG.info("Job is finished by: " + getSelf());
            getSender().tell(new WorkerWorkResult(), getSelf());
        } else {
            unhandled(message);
        }
    }
}
