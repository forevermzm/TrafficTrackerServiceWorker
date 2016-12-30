package akka.pojo;

import pojo.json.SrcDestPair;

import java.util.List;

public class CalculateWorkResult {
    private final List<SrcDestPair> addressPairs;

    public CalculateWorkResult(List<SrcDestPair> addressPairs) {
        this.addressPairs = addressPairs;
    }

    public List<SrcDestPair> getAddressPairs() {
        return addressPairs;
    }
}
