package akka.pojo;

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
}
