package model;

/**
 * UI status is evidence-driven. SENT is only a local TCP write; DELIVERED
 * requires an application ACK from the bound TCP peer and SEEN requires a receipt emitted
 * after real foreground display. UNCERTAIN is not a definitive failure.
 */
public enum DeliveryState {
    SENT("Đã gửi",1),
    DELIVERED("Đã nhận",3),
    SEEN("Đã xem",4),
    SENDING("Đang gửi",0),
    UNCERTAIN("Chưa rõ",2),
    FAILED("Thất bại",2);

    private final String label;
    private final int evidence;
    DeliveryState(String label,int evidence){this.label=label;this.evidence=evidence;}
    public String label(){return label;}
    public DeliveryState advanceTo(DeliveryState update){
        if(update==null)throw new NullPointerException("delivery state");
        return update.evidence>evidence?update:this;
    }
}
