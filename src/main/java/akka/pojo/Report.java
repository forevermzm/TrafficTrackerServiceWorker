package akka.pojo;

import org.immutables.value.Value;
import pojo.json.ImmutableStyle;
import pojo.json.SrcDestPair;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@ImmutableStyle
@Value.Immutable
public abstract class Report {
    public abstract Map<String,Duration> getStepDurations();

    public abstract List<SrcDestPair> getCalculatedPairs();

    @Value.Default
    public boolean getTimedout() {
        return false;
    }

    public static ImmutableReport.Builder builder() {
        return ImmutableReport.builder();
    }
}
