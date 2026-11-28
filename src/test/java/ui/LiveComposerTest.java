package ui;

import model.MessageContent;
import org.junit.jupiter.api.Test;
import ui.components.ComposerPanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Editable Live drafts and multiline UTF-8 never silently become fake sends. */
class LiveComposerTest {
    private static <T extends Component> T find(Container root,Class<T> type,String accessible){
        for(Component child:root.getComponents()){
            if(type.isInstance(child)&&child instanceof JComponent jc
                &&accessible.equals(jc.getAccessibleContext().getAccessibleName()))
                return type.cast(child);
            if(child instanceof Container nested){
                var found=find(nested,type,accessible);
                if(found!=null)return found;
            }
        }
        return null;
    }
    private static void edt(Runnable action) throws Exception {
        SwingUtilities.invokeAndWait(action);
    }
    @Test void enterSendsOnceShiftEnterMakesMultilineAndEmojiArePreserved()throws Exception{
        AtomicReference<String> current=new AtomicReference<>("chat-A");
        List<MessageContent> sent=new ArrayList<>();
        List<String> errors=new ArrayList<>();
        var holder=new AtomicReference<ComposerPanel>();
        edt(()->holder.set(new ComposerPanel(Runnable::run,current::get,
            (chat,content)->{assertEquals("chat-A",chat);sent.add(content);},errors::add)));
        try{
            edt(()->{
                ComposerPanel composer=holder.get();
                JTextPane input=find(composer,JTextPane.class,"Nhập tin nhắn");
                assertNotNull(input,"Composer must support multiline user text");
                input.setText("Xin chào 👩🏽‍💻");
                Action enter=input.getActionMap().get("live-send-enter");
                Action shift=input.getActionMap().get("live-insert-newline");
                assertNotNull(enter);
                assertNotNull(shift);
                shift.actionPerformed(new ActionEvent(input,ActionEvent.ACTION_PERFORMED,""));
                input.setCaretPosition(input.getDocument().getLength());
                input.replaceSelection("dòng thứ hai 🇻🇳");
                assertEquals("Xin chào 👩🏽‍💻\ndòng thứ hai 🇻🇳",input.getText());
                enter.actionPerformed(new ActionEvent(input,ActionEvent.ACTION_PERFORMED,""));
                enter.actionPerformed(new ActionEvent(input,ActionEvent.ACTION_PERFORMED,""));
                assertEquals(1,sent.size(),"A second Enter on cleared input must not re-send");
                assertEquals("Xin chào 👩🏽‍💻\ndòng thứ hai 🇻🇳",sent.getFirst().text());
                assertEquals("",input.getText());
                assertTrue(errors.isEmpty());
            });
        }finally{edt(holder.get()::close);}
    }
    @Test void invalidUtf16OrOverlongUtf8IsNeverSentAndDraftRemainsEditable()throws Exception{
        AtomicReference<String> current=new AtomicReference<>("chat-A");
        List<MessageContent> sent=new ArrayList<>();
        List<String> errors=new ArrayList<>();
        var holder=new AtomicReference<ComposerPanel>();
        edt(()->holder.set(new ComposerPanel(Runnable::run,current::get,
            (chat,content)->sent.add(content),errors::add)));
        try{
            edt(()->{
                ComposerPanel composer=holder.get();
                JTextPane input=find(composer,JTextPane.class,"Nhập tin nhắn");
                assertNotNull(input);
                input.setText("\uD83D"); // isolated high surrogate, never encode with replacement
                input.getActionMap().get("live-send-enter").actionPerformed(
                    new ActionEvent(input,ActionEvent.ACTION_PERFORMED,""));
                assertTrue(sent.isEmpty());
                assertEquals("\uD83D",input.getText());
                input.setText("😀".repeat(8_001));
                input.getActionMap().get("live-send-enter").actionPerformed(
                    new ActionEvent(input,ActionEvent.ACTION_PERFORMED,""));
                assertTrue(sent.isEmpty());
                assertEquals("😀".repeat(8_001),input.getText());
                assertEquals(2,errors.size());
            });
        }finally{edt(holder.get()::close);}
    }
    @Test void offlineDraftNeverDispatchesAndConversationSwitchRestoresOnlyOwnRamText()throws Exception{
        AtomicReference<String> current=new AtomicReference<>("chat-A");
        List<String> sent=new ArrayList<>();
        var holder=new AtomicReference<ComposerPanel>();
        edt(()->holder.set(new ComposerPanel(Runnable::run,current::get,
            (chat,content)->sent.add(chat+":"+content.text()),m->{})));
        try{
            edt(()->{
                ComposerPanel composer=holder.get();
                JTextPane input=find(composer,JTextPane.class,"Nhập tin nhắn");
                assertNotNull(input);
                input.setText("Bản nháp A\nkhông gửi");
                current.set("chat-B");
                composer.switchDraft("chat-A","chat-B");
                assertEquals("",input.getText());
                input.setText("Bản nháp B");
                current.set("chat-A");
                composer.switchDraft("chat-B","chat-A");
                assertEquals("Bản nháp A\nkhông gửi",input.getText());
                current.set(null);
                composer.refresh();
                input.getActionMap().get("live-send-enter").actionPerformed(
                    new ActionEvent(input,ActionEvent.ACTION_PERFORMED,""));
                assertTrue(sent.isEmpty(),"Offline draft must not call network");
                assertEquals("Bản nháp A\nkhông gửi",input.getText());
                current.set("chat-A");
                composer.refresh();
                composer.restoreText("chat-A","chat-A","Lần gửi thất bại");
                assertTrue(input.getText().contains("Lần gửi thất bại"));
            });
        }finally{edt(holder.get()::close);}
    }
}
