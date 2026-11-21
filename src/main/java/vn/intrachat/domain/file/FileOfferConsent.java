package vn.intrachat.domain.file;

import java.util.Objects;

/** In-memory per-offer consent. Approved Downloads root is a separate permission. */
public final class FileOfferConsent {
    private enum Choice { PENDING, ACCEPTED, REJECTED }
    private final FileTransferId offer;
    private final long expiresAtMillis;
    private Choice choice=Choice.PENDING;

    public FileOfferConsent(FileTransferId offer, long offeredAtMillis, long expiresAtMillis) {
        this.offer=Objects.requireNonNull(offer,"offer");
        if (expiresAtMillis <= offeredAtMillis) throw new IllegalArgumentException("Expiry must be later");
        this.expiresAtMillis=expiresAtMillis;
    }
    private void check(FileTransferId id, long now) {
        if (!offer.equals(id)) throw new SecurityException("Wrong offer");
        if (choice != Choice.PENDING) throw new IllegalStateException("Offer already decided");
        if (now >= expiresAtMillis) throw new IllegalStateException("Offer expired");
    }
    public synchronized void accept(FileTransferId id, boolean downloadsRootApproved, long now) {
        check(id,now);
        if (!downloadsRootApproved) throw new IllegalStateException("User has not approved Downloads root");
        choice=Choice.ACCEPTED;
    }
    public synchronized void reject(FileTransferId id, long now) {
        check(id,now);
        choice=Choice.REJECTED;
    }
    public synchronized boolean isAccepted(){return choice==Choice.ACCEPTED;}
}
