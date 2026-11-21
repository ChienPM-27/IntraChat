package vn.intrachat.domain;

/** Future-state vocabulary, not used to claim delivery or reads in M0. */
public enum MessageDeliveryState {
    QUEUED, SENT, DELIVERED, READ, FAILED
}
