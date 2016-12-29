package akka.pojo;

import org.immutables.value.Value;
import pojo.json.ImmutableStyle;

import java.time.Duration;

@ImmutableStyle
@Value.Immutable
public abstract class Report {
    public abstract Duration getDuration();

    public abstract int getSuccessCount();

    public static ImmutableReport.Builder builder() {
        return ImmutableReport.builder();
    }
}
