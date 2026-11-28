package ui.components;

import model.ChatMessage;
import model.DeliveryState;
import model.MessageType;
import vn.intrachat.application.FileTransferService;
import java.util.function.BiConsumer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Consumer;

import static ui.components.ChatStyle.*;

public final class MessageBubblePanel extends JPanel {
    private final JLabel receipt = label("", 11, Font.PLAIN, MUTED);
    private JButton manualRetry;
    private JPanel fileArea;
    private final MessageMedia media;

    public MessageBubblePanel(ChatMessage message, boolean mine, boolean group, String sender, MessageMedia media) {
        this(message,mine,group,sender,media,null,null,null);
    }

    /** Retrying an UNCERTAIN message is a separate explicit user command, never a timer. */
    public MessageBubblePanel(ChatMessage message,boolean mine,boolean group,String sender,
                              MessageMedia media,Consumer<String> onManualRetry) {
        this(message,mine,group,sender,media,onManualRetry,null,null);
    }
    public MessageBubblePanel(ChatMessage message, boolean mine, boolean group, String sender,
            MessageMedia media, Consumer<String> onManualRetry,
            FileTransferService.Transfer transfer, BiConsumer<String,String> fileAction) {
        super(new FlowLayout(mine ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 0));
        this.media = media;
        setOpaque(false);
        setBorder(new EmptyBorder(4, 18, 4, 18));
        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        float alignment = mine ? Component.RIGHT_ALIGNMENT : Component.LEFT_ALIGNMENT;
        String time = time(message.sentAt());
        if (group && !mine) {
            JLabel byline = label(sender, 11, Font.PLAIN, MUTED);
            byline.setBorder(new EmptyBorder(0, 5, 3, 5));
            byline.setAlignmentX(alignment);
            stack.add(byline);
        }
        Color bubbleColor = mine ? BLUE : LINE;
        Color ink = mine ? Color.WHITE : INK;
        JPanel bubble = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D draw = drawing(graphics);
                draw.setColor(bubbleColor);
                draw.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                draw.dispose();
                super.paintComponent(graphics);
            }
        };
        bubble.setOpaque(false);
        boolean visualMedia = message.content().type() == MessageType.IMAGE || message.content().type() == MessageType.VIDEO;
        bubble.setBorder(visualMedia ? new EmptyBorder(5, 5, 5, 5) : new EmptyBorder(7, 11, 7, 11));
        bubble.setAlignmentX(alignment);
        bubble.add(message.content().type() == MessageType.TEXT ? text(message.content().text(), ink)
                : transfer!=null && (message.content().type()==MessageType.FILE ||
                        transfer.localPath()==null || (!mine && transfer.state()!=FileTransferService.State.COMPLETED))
                    ? fileLabel(transfer,ink) : media.render(localContent(message, transfer), mine));
        bubble.setToolTipText(sender + " · " + time);
        bubble.getAccessibleContext().setAccessibleName(sender + " · " + time);
        stack.add(bubble);
        if(transfer!=null){
            fileArea=new JPanel();fileArea.setOpaque(false);
            fileArea.setLayout(new BoxLayout(fileArea,BoxLayout.Y_AXIS));
            fileArea.setAlignmentX(alignment);
            stack.add(fileArea);
            showFileTransfer(transfer,fileAction);
        } else if (message.content().type() != MessageType.TEXT && message.content().type() != MessageType.FILE
                && message.state() == DeliveryState.DELIVERED) {
            // History may have no live transfer object, but its local media is still exportable.
            JButton save = media.saveButton(message.content().attachment());
            save.setAlignmentX(alignment);
            stack.add(save);
        }

        JLabel timestamp = label(time, 11, Font.PLAIN, MUTED);
        timestamp.setBorder(new EmptyBorder(2, 5, 0, 5));
        timestamp.setAlignmentX(alignment);
        stack.add(timestamp);
        receipt.setName("message-receipt");
        receipt.setBorder(new EmptyBorder(2, 5, 0, 5));
        receipt.setAlignmentX(alignment);
        receipt.setVisible(false);
        stack.add(receipt);
        if(mine && onManualRetry!=null){
            manualRetry=new JButton("Gửi lại thủ công (có nguy cơ trùng tin)");
            manualRetry.setName("live-manual-retry");
            manualRetry.getAccessibleContext().setAccessibleName(
                "Gửi lại thủ công, có nguy cơ trùng tin");
            manualRetry.setFont(manualRetry.getFont().deriveFont(11f));
            manualRetry.setAlignmentX(alignment);
            manualRetry.setVisible(false);
            manualRetry.addActionListener(e->{
                manualRetry.setEnabled(false);
                onManualRetry.accept(message.id());
            });
            stack.add(manualRetry);
        }
        add(stack);
    }

    private static model.MessageContent localContent(ChatMessage message, FileTransferService.Transfer transfer) {
        if (transfer == null || transfer.localPath() == null)
            return message.content();
        return model.MessageContent.media(message.content().type(), new model.Attachment(
            transfer.localPath(), transfer.fileName(), transfer.fileSize(), 0, 0, 0));
    }

    private static JPanel fileLabel(FileTransferService.Transfer t,Color ink){
        JPanel panel=new JPanel(new GridLayout(2,1));panel.setOpaque(false);
        panel.add(label(t.messageType().name()+" · "+t.fileName(),13,Font.BOLD,ink));
        panel.add(label(MediaFiles.size(t.fileSize()),12,Font.PLAIN,ink));
        return panel;
    }
    public void showFileTransfer(FileTransferService.Transfer t,BiConsumer<String,String> action){
        if(fileArea==null)return;
        fileArea.removeAll();
        long bytes=t.transferredBytes();
        String state=t.state().name();
        String description=switch(t.state()){
            case OFFERED -> "Đang gửi lời mời";
            case WAITING_ACCEPT -> "Đang chuẩn bị tự lưu";
            case TRANSFERRING -> "Đang truyền";
            case COMPLETED -> "Đã truyền / lưu thành công";
            case REJECTED -> "Đã từ chối";
            case CANCELLED -> "Đã hủy";
            case FAILED -> "Lỗi: "+t.error();
        };
        fileArea.add(label(description+" · "+MediaFiles.size(bytes)+" / "+
            MediaFiles.size(t.fileSize()),11,Font.PLAIN,MUTED));
        JProgressBar progress=new JProgressBar(0,1000);
        progress.setValue(t.fileSize()==0?(t.state()==FileTransferService.State.COMPLETED?1000:0):
            (int)Math.min(1000,bytes*1000/t.fileSize()));
        progress.setPreferredSize(new Dimension(220,9));
        fileArea.add(progress);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT,4,0));buttons.setOpaque(false);
        // Incoming transfers are accepted automatically into the managed received-files directory.
        // Cancel remains available while the transfer is active.
        if(action!=null && (t.state()==FileTransferService.State.WAITING_ACCEPT||t.state()==FileTransferService.State.TRANSFERRING))
            addFileButton(buttons,"Hủy",()->action.accept("cancel",t.transferId().toString()));
        if(t.state()==FileTransferService.State.COMPLETED&&t.localPath()!=null) {
            if (action != null && t.messageType()==MessageType.FILE)
                addFileButton(buttons,"Mở tệp / vị trí",()->action.accept("open",t.transferId().toString()));
            buttons.add(media.saveButton(new model.Attachment(t.localPath(), t.fileName(), t.fileSize(), 0, 0, 0)));
        }
        fileArea.add(buttons);
        fileArea.revalidate();fileArea.repaint();
    }
    private static void addFileButton(JPanel target,String title,Runnable action){
        JButton button=new JButton(title);button.setFont(button.getFont().deriveFont(11f));
        button.addActionListener(e->action.run());target.add(button);
    }
    public void showReceipt(DeliveryState state) {
        showReceipt(state,null);
    }
    public void showReceipt(DeliveryState state,String groupRecipientCount) {
        receipt.setVisible(state != null || groupRecipientCount != null);
        if (state != null || groupRecipientCount != null) {
            receipt.setText(groupRecipientCount == null ? state.label() :
                (state == DeliveryState.UNCERTAIN || state == DeliveryState.FAILED
                    ? state.label()+" · "+groupRecipientCount : groupRecipientCount));
            receipt.setIcon(state == DeliveryState.SENT
                ? new ChatIcon(ChatIcon.Kind.CHECK,MUTED,13)
                : state == DeliveryState.DELIVERED
                    ? new ChatIcon(ChatIcon.Kind.DOUBLE_CHECK,MUTED,13) : null);
            receipt.setHorizontalTextPosition(SwingConstants.LEADING);
        }
        if(manualRetry!=null)manualRetry.setVisible(state==DeliveryState.UNCERTAIN);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    private static JTextPane text(String value, Color ink) {
        EmojiTextPane pane = new EmojiTextPane(ink, false);
        pane.setOpaque(false);
        pane.setBorder(null);
        pane.setMargin(new Insets(0, 0, 0, 0));
        pane.setText(value);
        pane.styleEmoji();
        int width = Math.min(340, Math.max(16, pane.getPreferredSize().width + 2));
        var view = pane.getUI().getRootView(pane);
        view.setSize(width, Integer.MAX_VALUE);
        pane.setPreferredSize(new Dimension(width, (int) Math.ceil(view.getPreferredSpan(javax.swing.text.View.Y_AXIS)) + 1));
        return pane;
    }
}
