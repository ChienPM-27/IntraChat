package vn.intrachat.domain;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChatGroupTest {
    private final UUID owner=UUID.randomUUID();
    @Test void createUniqueGroupsAndRevision(){
        ChatGroup first=new ChatGroup(UUID.randomUUID(),"ABC",owner,List.of(owner),0);
        ChatGroup second=new ChatGroup(UUID.randomUUID(),"ABC",owner,List.of(owner),0);
        assertNotEquals(first.groupId(),second.groupId());
        UUID other=UUID.randomUUID();
        ChatGroup expanded=first.withMembers(List.of(owner,other));
        assertEquals(1,expanded.revision());
        assertTrue(expanded.contains(other));
        assertFalse(first.contains(other));
    }
    @Test void rejectInvalidNameDuplicateOwnerAndCapacity(){
        UUID id=UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,()->new ChatGroup(id,"  ",owner,List.of(owner),0));
        assertThrows(IllegalArgumentException.class,()->new ChatGroup(id,"ABC",owner,List.of(owner,owner),0));
        assertThrows(IllegalArgumentException.class,()->new ChatGroup(id,"ABC",owner,List.of(UUID.randomUUID()),0));
        assertThrows(IllegalArgumentException.class,()->new ChatGroup(id,"ABC",owner,
            List.of(owner,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID()),0));
    }
}
