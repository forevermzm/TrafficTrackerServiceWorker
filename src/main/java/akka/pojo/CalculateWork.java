package akka.pojo;

import pojo.json.SrcDestPair;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class CalculateWork {
    private final List<SrcDestPair> addressPairs;

    public CalculateWork(List<SrcDestPair> addressPairs) {
        this.addressPairs = checkNotNull(addressPairs);
    }

    public List<SrcDestPair> getAddressPairs() {
        return addressPairs;
    }
}
