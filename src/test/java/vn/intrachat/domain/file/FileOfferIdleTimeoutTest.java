package vn.intrachat.domain.file;
import vn.intrachat.application.file.FileDeadlinePolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FileOfferIdleTimeoutTest {
 @Test void offerAndIdleBoundedNotAutoResume(){
   var time=new FileDeadlinePolicy(1000,30000,20000,600000);
   assertFalse(time.offerExpired(30999));
   assertTrue(time.offerExpired(31000));
   assertFalse(time.idleExpired(1050,21049));
   assertTrue(time.idleExpired(1050,21050));
   assertFalse(time.overallExpired(600999));
   assertTrue(time.overallExpired(601000));
 }
 @Test void rejectsNegativeClockAndInfiniteTimeout(){
   assertThrows(IllegalArgumentException.class,()->new FileDeadlinePolicy(0,0,10,100));
   assertThrows(IllegalArgumentException.class,()->new FileDeadlinePolicy(0,10,-1,100));
   assertThrows(IllegalArgumentException.class,()->new FileDeadlinePolicy(-1,10,10,100));
 }
}