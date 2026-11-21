package vn.intrachat.application;

import java.util.concurrent.CompletionStage;

/**
 * Asynchronous human approval for every inbound TCP candidate.
 * No production default approval. Test adapters can return scripted decisions.
 */
@FunctionalInterface
public interface InboundApprovalPort {
    enum Decision { ACCEPT, REJECT }
    CompletionStage<Decision> requestApproval(InboundRequest request);
}
