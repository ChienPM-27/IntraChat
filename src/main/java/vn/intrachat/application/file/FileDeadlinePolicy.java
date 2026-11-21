package vn.intrachat.application.file;

/** Pure finite monotonic timeout predicates. Values are reviewable targets, not a scheduler. */
public final class FileDeadlinePolicy {
    private final long beganAt;
    private final long offerLimit;
    private final long idleLimit;
    private final long overallLimit;
    public FileDeadlinePolicy(long beganAtMillis,long offerTimeoutMillis,long idleTimeoutMillis,
                              long overallTimeoutMillis) {
        if(beganAtMillis<0 || offerTimeoutMillis<=0 || idleTimeoutMillis<=0 ||
           overallTimeoutMillis<=0 || offerTimeoutMillis>overallTimeoutMillis ||
           idleTimeoutMillis>overallTimeoutMillis)
            throw new IllegalArgumentException("Invalid finite timeout policy");
        beganAt=beganAtMillis;
        offerLimit=offerTimeoutMillis;
        idleLimit=idleTimeoutMillis;
        overallLimit=overallTimeoutMillis;
    }
    private static boolean exceeds(long start,long now,long limit) {
        return now>=start && now-start>=limit;
    }
    public boolean offerExpired(long now){return exceeds(beganAt,now,offerLimit);}
    public boolean idleExpired(long lastProgressAtMillis,long now){
        return lastProgressAtMillis>=beganAt && exceeds(lastProgressAtMillis,now,idleLimit);
    }
    public boolean overallExpired(long now){return exceeds(beganAt,now,overallLimit);}
}
