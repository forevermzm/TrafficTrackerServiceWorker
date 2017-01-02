package akka.pojo;

import com.google.common.base.MoreObjects;
import pojo.json.SrcDestPair;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class SnapshotResult {
    private final List<SrcDestPair> addressPairs;

    public SnapshotResult(List<SrcDestPair> addressPairs) {
        this.addressPairs = checkNotNull(addressPairs);
    }

    public List<SrcDestPair> getAddressPairs() {
        return addressPairs;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(SnapshotResult.class)
                .add("AddressPairs", addressPairs)
                .toString();
    }
}
