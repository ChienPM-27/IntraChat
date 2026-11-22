package vn.intrachat.security;

import vn.intrachat.domain.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

/**
 * Immutable canonical, domain-separated signed bytes for Group V1 ROSTER and GRANT.
 * This is only a byte encoder; it is NOT authorization of a network peer.
 */
public final class GroupCanonicalBytes {
    private GroupCanonicalBytes(){}
    public static byte[] roster(GroupRoster roster){
        Objects.requireNonNull(roster,"roster");
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(512);
            DataOutputStream out=new DataOutputStream(bytes);
            out.write("INTRACHAT_GROUP_ROSTER_V1".getBytes(StandardCharsets.US_ASCII));out.writeByte(0);
            uuid(out,roster.identity().groupId());
            uuid(out,roster.identity().ownerDeviceId());
            out.write(roster.identity().ownerSpkiSha256());
            out.writeLong(roster.revision());
            out.write(roster.previousRosterHash());
            byte[] title=roster.identity().title().getBytes(StandardCharsets.UTF_8);
            out.writeShort(title.length);out.write(title);
            out.writeByte(roster.members().size());
            for(GroupMember member:roster.members()){
                uuid(out,member.deviceId());
                out.write(member.spkiSha256());
                out.writeByte(member.role()==GroupMember.Role.OWNER?1:2);
            }
            uuid(out,roster.commitEventId());
            out.writeLong(roster.committedAt().toEpochMilli());
            out.flush();
            return bytes.toByteArray();
        }catch(IOException e){throw new IllegalStateException("In-memory canonical bytes failed",e);}
    }
    public static byte[] grant(GroupGrant grant){
        Objects.requireNonNull(grant,"grant");
        try{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream(256);
            DataOutputStream out=new DataOutputStream(bytes);
            out.write("INTRACHAT_GROUP_GRANT_V1".getBytes(StandardCharsets.US_ASCII));out.writeByte(0);
            uuid(out,grant.groupId());uuid(out,grant.messageId());uuid(out,grant.ownerDeviceId());
            out.write(grant.ownerSpkiSha256());out.writeLong(grant.revision());out.write(grant.rosterHash());
            uuid(out,grant.senderId());out.writeByte(grant.recipientIds().size());
            for(UUID id:grant.recipientIds())uuid(out,id);
            out.writeLong(grant.issuedAt().toEpochMilli());
            out.writeLong(grant.expiresAt().toEpochMilli());
            out.write(grant.nonce());
            out.flush();return bytes.toByteArray();
        }catch(IOException e){throw new IllegalStateException("In-memory canonical grant failed",e);}
    }
    /** Strict inverse for the bounded signed roster metadata file; signature checked by caller. */
    public static GroupRoster parseRoster(byte[] input)throws IOException{
        if(input==null||input.length<120||input.length>4096)
            throw new IOException("Invalid canonical roster length");
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(input))){
            byte[] prefix="INTRACHAT_GROUP_ROSTER_V1".getBytes(StandardCharsets.US_ASCII);
            byte[] got=new byte[prefix.length];
            in.readFully(got);
            if(!Arrays.equals(got,prefix)||in.readUnsignedByte()!=0)
                throw new IOException("Invalid Group roster signing domain");
            UUID groupId=readUuid(in),ownerId=readUuid(in);
            byte[] ownerPin=new byte[32];in.readFully(ownerPin);
            long rev=in.readLong();
            byte[] prev=new byte[32];in.readFully(prev);
            int titleLen=in.readUnsignedShort();
            if(titleLen<1||titleLen>240)throw new IOException("Invalid group title bytes");
            byte[] titleBytes=new byte[titleLen];in.readFully(titleBytes);
            String title=StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(titleBytes)).toString();
            int count=in.readUnsignedByte();
            if(count<2||count>GroupRoster.MAX_MEMBERS)throw new IOException("Invalid group roster size");
            List<GroupMember> members=new ArrayList<>(count);
            for(int i=0;i<count;i++){
                UUID id=readUuid(in);
                byte[] pin=new byte[32];in.readFully(pin);
                int role=in.readUnsignedByte();
                if(role!=1&&role!=2)throw new IOException("Invalid group role");
                members.add(new GroupMember(id,pin,role==1?GroupMember.Role.OWNER:GroupMember.Role.MEMBER));
            }
            UUID eventId=readUuid(in);
            java.time.Instant committed=java.time.Instant.ofEpochMilli(in.readLong());
            if(in.available()!=0)throw new IOException("Trailing roster data");
            GroupRoster roster=new GroupRoster(new GroupIdentity(groupId,title,ownerId,ownerPin),
                rev,prev,members,eventId,committed);
            if(!Arrays.equals(input,roster(roster)))
                throw new IOException("Noncanonical Group roster encoding");
            return roster;
        }catch(IllegalArgumentException|java.nio.charset.CharacterCodingException e){
            throw new IOException("Invalid Group roster data",e);
        }
    }
    /**
     * Strict inverse for an untrusted signed GRANT_V1 component. The caller MUST
     * verify the ECDSA owner signature and authenticated TLS session separately.
     */
    public static GroupGrant parseGrant(byte[] input)throws IOException{
        if(input==null||input.length<170||input.length>4096)
            throw new IOException("Invalid canonical Group grant length");
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(input))){
            byte[] prefix="INTRACHAT_GROUP_GRANT_V1".getBytes(StandardCharsets.US_ASCII);
            byte[] got=new byte[prefix.length];in.readFully(got);
            if(!Arrays.equals(prefix,got)||in.readUnsignedByte()!=0)
                throw new IOException("Invalid Group grant signing domain");
            UUID group=readUuid(in),message=readUuid(in),owner=readUuid(in);
            byte[] ownerSpki=new byte[32];in.readFully(ownerSpki);
            long rev=in.readLong();
            byte[] rosterHash=new byte[32];in.readFully(rosterHash);
            UUID sender=readUuid(in);
            int count=in.readUnsignedByte();
            if(count<1||count>4)throw new IOException("Invalid Group grant recipient count");
            List<UUID> recipientIds=new ArrayList<>();
            for(int i=0;i<count;i++)recipientIds.add(readUuid(in));
            Instant issued=Instant.ofEpochMilli(in.readLong());
            Instant expires=Instant.ofEpochMilli(in.readLong());
            byte[] nonce=new byte[16];in.readFully(nonce);
            if(in.available()!=0)throw new IOException("Trailing Group grant data");
            GroupGrant grant=new GroupGrant(group,message,owner,ownerSpki,rev,rosterHash,
                sender,recipientIds,issued,expires,nonce);
            if(!Arrays.equals(input,grant(grant)))
                throw new IOException("Noncanonical Group grant encoding");
            return grant;
        }catch(IllegalArgumentException|java.time.DateTimeException bad){
            throw new IOException("Invalid Group grant component",bad);
        }
    }

    private static UUID readUuid(DataInputStream in)throws IOException{
        UUID id=new UUID(in.readLong(),in.readLong());
        if(id.equals(new UUID(0,0)))throw new IOException("Zero roster UUID");
        return id;
    }
    private static void uuid(DataOutputStream out,UUID id)throws IOException{
        GroupIdentity.requireId(id,"uuid");
        out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());
    }
    public static byte[] sha256(byte[] bytes){
        Objects.requireNonNull(bytes,"bytes");
        try{return MessageDigest.getInstance("SHA-256").digest(bytes);}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
