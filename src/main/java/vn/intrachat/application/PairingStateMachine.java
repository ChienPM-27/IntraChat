package vn.intrachat.application;

import java.util.Objects;
import java.util.UUID;

/** Pairing state guard. Remote signals must be authenticated and transcript-checked by caller. */
public final class PairingStateMachine {
    private final UUID requestId;
    private boolean localConfirmed;
    private boolean remoteConfirmed;
    private boolean localReady;
    private boolean remoteReady;
    private boolean rejected;
    public PairingStateMachine(UUID requestId){this.requestId=Objects.requireNonNull(requestId);}
    public synchronized UUID requestId(){return requestId;}
    private void requireOpen(){if(rejected)throw new IllegalStateException("Pairing rejected");}
    public synchronized void acceptLocalFingerprint(){requireOpen();localConfirmed=true;}
    public synchronized void acceptRemoteConfirmation(){requireOpen();remoteConfirmed=true;}
    public synchronized void localReady(){
        requireOpen();
        if(!localConfirmed||!remoteConfirmed)throw new IllegalStateException("Both confirmations required");
        localReady=true;
    }
    public synchronized void remoteReady(){
        requireOpen();
        if(!localConfirmed||!remoteConfirmed)throw new IllegalStateException("Both confirmations required");
        remoteReady=true;
    }
    public synchronized boolean maySendChat(){
        return !rejected&&localConfirmed&&remoteConfirmed&&localReady&&remoteReady;
    }
    public synchronized void reject(){
        rejected=true;localConfirmed=false;remoteConfirmed=false;localReady=false;remoteReady=false;
    }
}
