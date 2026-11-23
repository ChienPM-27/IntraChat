package ui.components;

import model.MessageType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class MediaFilesP5Test {
 @TempDir Path dir;
 @Test void pngJpegAndAspectRatio()throws Exception{
  for(String ext:new String[]{"png","jpg"}){
   BufferedImage original=new BufferedImage(640,320,BufferedImage.TYPE_INT_RGB);
   Path file=dir.resolve("test."+ext);
   assertTrue(ImageIO.write(original,ext,file.toFile()));
   var metadata=MediaFiles.inspect(file,MessageType.IMAGE);
   assertEquals(640,metadata.width());assertEquals(320,metadata.height());
   BufferedImage small=MediaFiles.image(file,240);
   assertEquals(2.0,(double)small.getWidth()/small.getHeight(),0.02);
  }
 }
 @Test void corruptAndDeletedImageGracefullyReject()throws Exception{
  Path file=dir.resolve("bad.png");Files.write(file,new byte[]{1,2,3,4});
  assertThrows(IllegalArgumentException.class,()->MediaFiles.inspect(file,MessageType.IMAGE));
  Files.delete(file);
  assertThrows(IllegalArgumentException.class,()->MediaFiles.image(file,240));
 }
 @Test void wavAndUnsupportedExternalAudio()throws Exception{
  Path wav=dir.resolve("sample.wav");
  byte[] pcm=new byte[8000];
  var format=new AudioFormat(8000,8,1,true,false);
  try(var stream=new AudioInputStream(new java.io.ByteArrayInputStream(pcm),format,pcm.length)){
   AudioSystem.write(stream,AudioFileFormat.Type.WAVE,wav.toFile());
  }
  assertTrue(MediaFiles.inspect(wav,MessageType.SOUND).durationMillis()>0);
  try(var audio=MediaFiles.audio(wav)){assertEquals(8000,audio.getFormat().getSampleRate());}
  Path mp3=dir.resolve("sample.mp3");Files.write(mp3,new byte[]{1,2,3});
  assertEquals(MessageType.SOUND,MessageType.valueOf("SOUND"));
  assertEquals(0,MediaFiles.inspect(mp3,MessageType.SOUND).durationMillis());
  assertThrows(IllegalArgumentException.class,()->MediaFiles.audio(mp3));
 }
 @Test void videoSignatureAndSizeLimit()throws Exception{
  Path mp4=dir.resolve("movie.mp4");
  Files.write(mp4,new byte[]{0,0,0,20,'f','t','y','p','i','s','o','m'});
  assertEquals(MessageType.VIDEO,MessageType.valueOf("VIDEO"));
  assertEquals(Files.size(mp4),MediaFiles.inspect(mp4,MessageType.VIDEO).sizeBytes());
  Files.write(mp4,new byte[]{1,2,3});
  assertThrows(IllegalArgumentException.class,()->MediaFiles.inspect(mp4,MessageType.VIDEO));
 }
}
