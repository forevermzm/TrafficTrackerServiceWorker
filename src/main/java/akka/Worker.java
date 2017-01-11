package akka;

import akka.actor.UntypedActor;
import akka.pojo.CalculateWork;
import akka.pojo.CalculateWorkResult;
import akka.pojo.SnapshotResult;
import aws.DynamoFactory;
import aws.S3Factory;
import dao.MapsDAO;
import dao.SourceDestinationTableDAO;
import dao.TrafficStatusDAO;
import google.GeoContextFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pojo.dynamo.SourceDestinationPair;
import pojo.json.GoogleTravelMode;
import pojo.json.ImmutableTrafficStatusDocument;
import pojo.json.SrcDestPair;
import pojo.json.TrafficStatusDocument;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class Worker extends UntypedActor {
    private static final Logger LOG = LogManager.getFormatterLogger();

    private static final int SCAN_LIMIT = 100;

    private static final int UPDATE_INTERVAL = 600;
    private static final int PROCESS_SIZE = 8;

    private SourceDestinationTableDAO tableDao = new SourceDestinationTableDAO(DynamoFactory.createDDBMapper());

    private MapsDAO mapDao = new MapsDAO(GeoContextFactory.createApiContext());

    private TrafficStatusDAO trafficStatusDAO = new TrafficStatusDAO(S3Factory.createS3Client(),
            S3Factory.createJsonMapper());

    @Override
    public void onReceive(Object message) throws Throwable {
        if (Objects.equals(message, Master.SNAPSHOT_WORK)) {
            List<SrcDestPair> pairs = doSnapshotWork();
            getSender().tell(new SnapshotResult(pairs), getSelf());
        } else if (message instanceof CalculateWork) {
            CalculateWork work = (CalculateWork) message;
            CalculateWorkResult workResult = doCalculateWork(work);
            LOG.info("Job is finished by: " + getSelf() + " with work done: " + workResult);
            getSender().tell(workResult, getSelf());
        } else {
            unhandled(message);
        }
    }

    private List<SrcDestPair> doSnapshotWork() {
        Instant now = Instant.now();
        LOG.info("Received snapshot work at: " + now);
        return tableDao.scan(SCAN_LIMIT).stream()
                .filter(s -> s.getLastUpdateTime().isBefore(now.minusSeconds(UPDATE_INTERVAL)))
                .limit(PROCESS_SIZE)
                .map(s -> s.getPair())
                .collect(Collectors.toList());
    }

    private CalculateWorkResult doCalculateWork(CalculateWork work) {
        LOG.info("Received calculation work with size: " + work.getAddressPairs().size());

        List<SrcDestPair> finishedPairs = new ArrayList<>();
        for (SrcDestPair pair : work.getAddressPairs()) {
            try {
                doCalculateWork(pair);
                finishedPairs.add(pair);
            } catch (Exception e) {
                LOG.error("Caught exception during calculating for: " + pair + ". Will retry later.", e);
            }
        }

        return new CalculateWorkResult(finishedPairs);
    }

    private void doCalculateWork(SrcDestPair pair) {
        Instant now = Instant.now();
        Instant tooOld = now.minus(30, ChronoUnit.DAYS);
        Map<GoogleTravelMode, Duration> trafficStatus = mapDao
                .getGoogleDirection(pair.getSrcAddress().getFormattedAddress(),
                        pair.getDestAddress().getFormattedAddress(),
                        now);
        Map<GoogleTravelMode, Duration> reversedTrafficStatus = mapDao
                .getGoogleDirection(pair.getDestAddress().getFormattedAddress(),
                        pair.getSrcAddress().getFormattedAddress(),
                        now);

        SourceDestinationPair ddbPair = tableDao.read(pair.getId());

        ImmutableTrafficStatusDocument.Builder trafficStatusBuilder = TrafficStatusDocument.builder();
        Map<GoogleTravelMode, List<TrafficStatusDocument.TimeDurationPair>> statusMap =
                new HashMap<>();
        Map<GoogleTravelMode, List<TrafficStatusDocument.TimeDurationPair>> reversedStatusMap =
                new HashMap<>();

        trafficStatusBuilder.withSrcAddress(pair.getSrcAddress().getFormattedAddress());
        trafficStatusBuilder.withDestAddress(pair.getDestAddress().getFormattedAddress());
        trafficStatusBuilder.withLastUpdatedTime(now);

        // Has previous process results.
        if (ddbPair.getLastUpdateTime().isAfter(Instant.EPOCH)) {
            TrafficStatusDocument previous = trafficStatusDAO.read(pair.getPath());
            previous.getTrafficStatuses().entrySet()
                    .forEach(e -> statusMap.put(e.getKey(), e.getValue()
                            .stream()
                            .filter(l -> l.getTime()
                                    .isAfter(tooOld))
                            .collect(Collectors.toList())));
            previous.getReversedTrafficStatuses().entrySet()
                    .forEach(e -> reversedStatusMap.put(e.getKey(), e.getValue()
                            .stream()
                            .filter(l -> l.getTime()
                                    .isAfter(tooOld))
                            .collect(Collectors.toList())));
            trafficStatusBuilder.withUpdateCounter(previous.getUpdateCounter() + 1);
        } else {
            trafficStatusBuilder.withUpdateCounter(1);
            for (GoogleTravelMode travelMode: GoogleTravelMode.values()) {
                statusMap.put(travelMode, new ArrayList<>());
                reversedStatusMap.put(travelMode, new ArrayList<>());
            }
        }

        for (Map.Entry<GoogleTravelMode, Duration> status: trafficStatus.entrySet()) {
            List<TrafficStatusDocument.TimeDurationPair> pairList = statusMap.get(status.getKey());
            pairList.add(TrafficStatusDocument.TimeDurationPair.builder()
                    .withTime(now)
                    .withDuration(status.getValue())
                    .build());
            trafficStatusBuilder.putTrafficStatuses(status.getKey(), pairList);
        }

        for (Map.Entry<GoogleTravelMode, Duration> status: reversedTrafficStatus.entrySet()) {
            List<TrafficStatusDocument.TimeDurationPair> pairList = reversedStatusMap.get(status.getKey());
            pairList.add(TrafficStatusDocument.TimeDurationPair.builder()
                    .withTime(now)
                    .withDuration(status.getValue())
                    .build());
            trafficStatusBuilder.putTrafficStatuses(status.getKey(), pairList);
        }

        trafficStatusDAO.save(pair.getPath(), trafficStatusBuilder.build());

        // Update ddb table.
        ddbPair.setLastUpdateTime(now);
        tableDao.save(ddbPair);
    }
}
