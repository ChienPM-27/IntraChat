package vn.intrachat.domain;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Stable ordering-independent UUID derived ONLY from two device UUIDs. */
public final class P2PConversation {
    private P2PConversation(){}
    public static UUID idFor(UUID a,UUID b){
        if(a==null||b==null||a.equals(b))throw new IllegalArgumentException("Distinct peer IDs required");
        String x=a.toString(),y=b.toString();
        return UUID.nameUUIDFromBytes(("intrachat:dm:"+ (x.compareTo(y)<0?x+":"+y:y+":"+x))
            .getBytes(StandardCharsets.US_ASCII));
    }
}
