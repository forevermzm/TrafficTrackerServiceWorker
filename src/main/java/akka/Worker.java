package akka;

import akka.actor.UntypedActor;
import akka.pojo.CalculateWork;
import akka.pojo.CalculateWorkResult;
import akka.pojo.SnapshotResult;
import dao.SourceDestinationTableDAO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import pojo.json.GoogleAddress;
import pojo.json.SrcDestPair;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Worker extends UntypedActor {
    private final static Logger LOG = LogManager.getFormatterLogger();

    @Autowired
    private SourceDestinationTableDAO dao;

    @Override
    public void onReceive(Object message) throws Throwable {
        if (Objects.equals(message, Master.SNAPSHOT_WORK)) {
            Thread.sleep(2000);
            List<SrcDestPair> pairs = doSnapshotWork();
            getSender().tell(new SnapshotResult(pairs), getSelf());
        } else if (message instanceof CalculateWork) {
            CalculateWork work = (CalculateWork) message;
            Thread.sleep(1000);
            LOG.info("Job is finished by: " + getSelf());
            getSender().tell(new CalculateWorkResult(work.getAddressPairs()), getSelf());
        } else {
            unhandled(message);
        }
    }

    private List<SrcDestPair> doSnapshotWork() {
        List<SrcDestPair> pairs = new ArrayList<>();
        for (int i = 0; i < 15; i ++) {
            pairs.add(SrcDestPair.builder()
                .withSrcAddress(GoogleAddress.builder()
                        .withPlaceId("id-src-" + i)
                        .withFormattedAddress("address-src-" + i)
                        .build())
                .withDestAddress(GoogleAddress.builder()
                        .withPlaceId("id-dest-" + i)
                        .withFormattedAddress("address-dest" + i)
                        .build())
            .build());
        }
        return pairs;
    }
}
