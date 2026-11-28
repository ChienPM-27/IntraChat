package ui.frames;

import com.formdev.flatlaf.FlatClientProperties;
import listener.ChatActionHandler;
import listener.ChatClientListener;
import model.*;
import vn.intrachat.application.CallService;
import vn.intrachat.application.FileTransferService;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import ui.components.*;
import ui.dialogs.CallDialog;
import ui.dialogs.CreateGroupDialog;
import ui.dialogs.GroupMembersDialog;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.text.Normalizer;
import java.util.List;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static ui.components.ChatStyle.*;

public final class MainChatFrame extends JFrame implements ChatClientListener {
    private final ChatUser self;
    private final ChatActionHandler actions;
    private final Map<String, ChatUser> users = new LinkedHashMap<>();
    private final Map<String, Conversation> conversations = new LinkedHashMap<>();
    private final Map<String, ChatMessage> previews = new HashMap<>();
    private final Map<String, ChatTimeline> timelines = new HashMap<>();
    private final Map<String, MessageBubblePanel> rows = new HashMap<>();
    private final Map<String,FileTransferService.Transfer> fileTransfers=new HashMap<>();
    private final Set<String> loading = new HashSet<>();
    private final Set<String> loaded = new HashSet<>();
    private final Map<String, String> historyErrors = new HashMap<>();
    private final ExecutorService work = Executors.newFixedThreadPool(3, task -> {
        Thread thread = new Thread(task, "intrachat-media");
        thread.setDaemon(true);
        return thread;
    });
    private final JTextField search = new JTextField();
    private final DefaultListModel<Conversation> listModel = new DefaultListModel<>();
    private final JList<Conversation> list = new JList<>(listModel);
    private final JPanel directoryCards = new JPanel(new CardLayout());
    private final JLabel noConversations = label("Không tìm thấy đoạn chat", 13, Font.PLAIN, MUTED);
    private final JButton newGroup = iconButton(ChatIcon.Kind.PLUS, "Tạo nhóm mới", BLUE);
    private final JButton manage = secondary("Nhóm");
    private final JButton voiceCallBtn = iconButton(ChatIcon.Kind.PHONE, "Gọi thoại P2P", BLUE);
    private final JButton videoCallBtn = iconButton(ChatIcon.Kind.VIDEO, "Gọi video P2P", BLUE);

    private final JLabel headerAvatar = new JLabel();
    private final JLabel headerTitle = label("Đoạn chat", 16, Font.BOLD, INK);
    private final JLabel headerState = label(" ", 12, Font.PLAIN, MUTED);
    private final JLabel notice = label(" ", 12, Font.PLAIN, MUTED);
    private final HistoryPanel history = new HistoryPanel();
    private final JScrollPane scroll = new JScrollPane(history);
    private final ComposerPanel composer;
    private final MessageMedia media;
    private String activeId;
    private boolean updatingList;
    private boolean creatingGroup;
    private boolean closed;
    private javax.swing.Timer liveVisibilityTimer;
    private final LiveMessageVisibilityObserver visibleMessages;
    private CallDialog callDialog;

    public MainChatFrame(ChatUser self, ChatActionHandler actions) {
        super("IntraChat — Live LAN TCP");
        this.self = self;
        if (Boolean.getBoolean("intrachat.testBot")) setTitle("IntraChat — Live LAN TCP — " + self.displayName());
        this.actions = actions;
        this.visibleMessages = new LiveMessageVisibilityObserver(System::nanoTime,
            (conversationId,messageId) -> this.actions.onMessageVisible(conversationId,messageId));
        users.put(self.id(), self);
        setIconImage(ChatIcon.appIconImage());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1100, 720);
        setMinimumSize(new Dimension(760, 520));
        setLocationRelativeTo(null);
        JPanel root = panel(new BorderLayout());
        add(root);
        root.add(buildSidebar(), BorderLayout.WEST);
        media = new MessageMedia(work, this, this::showNotice);
        composer = new ComposerPanel(work, () -> activeId, this::send, this::showNotice);
        newGroup.setVisible(actions.groupAvailable());
        root.add(buildChat());
        refreshDirectory();
        renderHistory(false);
        actions.connect(this);
        liveVisibilityTimer = new javax.swing.Timer(100, event -> checkLiveVisibleRows());
        liveVisibilityTimer.start();
    }

    private JPanel buildSidebar() {
        JPanel sidebar = panel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(300, 0));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, LINE));
        JPanel top = panel(new BorderLayout(0, 10));
        top.setBorder(new EmptyBorder(14, 14, 9, 12));
        JPanel heading = panel(new BorderLayout());
        heading.add(label("Đoạn chat", 22, Font.BOLD, INK));
        newGroup.setBackground(FIELD);
        newGroup.addActionListener(event -> createGroup());
        heading.add(newGroup, BorderLayout.EAST);
        top.add(heading, BorderLayout.NORTH);
        field(search, "Tìm kiếm đoạn chat");
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                new ChatIcon(ChatIcon.Kind.SEARCH, MUTED, 17));
        search.setPreferredSize(new Dimension(0, 37));
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { refreshDirectory(); }
            @Override public void removeUpdate(DocumentEvent event) { refreshDirectory(); }
            @Override public void changedUpdate(DocumentEvent event) { refreshDirectory(); }
        });
        top.add(search, BorderLayout.SOUTH);
        sidebar.add(top, BorderLayout.NORTH);

        list.setBackground(Color.WHITE);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(68);
        list.setFixedCellWidth(280);
        list.getAccessibleContext().setAccessibleName("Danh sách đoạn chat");
        list.setCellRenderer(new ConversationRenderer(this::row));
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !updatingList && list.getSelectedValue() != null) {
                switchChat(list.getSelectedValue().id());
            }
        });
        JScrollPane items = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        items.setBorder(null);
        items.getVerticalScrollBar().setUnitIncrement(24);
        directoryCards.add(items, "list");
        JPanel missing = panel(new FlowLayout(FlowLayout.CENTER, 0, 18));
        missing.add(noConversations);
        directoryCards.add(missing, "empty");
        sidebar.add(directoryCards);

        JPanel profile = panel(new BorderLayout(9, 0));
        profile.setName("messenger-profile");
        profile.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, LINE),
                new EmptyBorder(10, 14, 10, 14)));
        profile.add(new JLabel(new AvatarIcon(self.displayName(), self.id(), 34, false, false)), BorderLayout.WEST);
        JPanel identity = panel(new GridLayout(2, 1));
        JLabel profileName = label(self.displayName(), 13, Font.BOLD, INK);
        profileName.setToolTipText(self.displayName());
        identity.add(profileName);
        JLabel profileStatus = label("LAN TCP · Không mã hóa", 11, Font.PLAIN, MUTED);
        profileStatus.setToolTipText("Live LAN TCP · Không mã hóa · Đã xem = tin đã hiển thị");
        identity.add(profileStatus);
        profile.add(identity, BorderLayout.CENTER);
        if (actions.connectionSettingsAvailable()) {
            JButton settings = iconButton(ChatIcon.Kind.SETTINGS, "Cài đặt kết nối LAN", MUTED);
            settings.setName("messenger-lan-settings");
            settings.setBackground(FIELD);
            settings.addActionListener(event -> actions.openConnectionSettings());
            JPanel settingsSlot = panel(new GridBagLayout());
            settingsSlot.add(settings);
            profile.add(settingsSlot, BorderLayout.EAST);
        }
        sidebar.add(profile, BorderLayout.SOUTH);
        return sidebar;
    }

    private JPanel buildChat() {
        JPanel chat = panel(new BorderLayout());
        JPanel header = panel(new BorderLayout(10, 0));
        header.setPreferredSize(new Dimension(0, 60));
        header.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LINE),
                new EmptyBorder(8, 16, 8, 16)));
        header.add(headerAvatar, BorderLayout.WEST);
        JPanel heading = panel(new GridLayout(2, 1));
        heading.add(headerTitle);
        heading.add(headerState);
        header.add(heading);
        JPanel controls = panel(new FlowLayout(FlowLayout.TRAILING,6,0));
        if (actions.callAvailable()) {
            voiceCallBtn.setName("call-voice");
            voiceCallBtn.setBackground(FIELD);
            voiceCallBtn.setEnabled(false);
            voiceCallBtn.addActionListener(event -> startCall(CallService.Mode.VOICE));
            videoCallBtn.setName("call-video");
            videoCallBtn.setBackground(FIELD);
            videoCallBtn.setEnabled(false);
            videoCallBtn.addActionListener(event -> startCall(CallService.Mode.VIDEO));
            controls.add(voiceCallBtn);
            controls.add(videoCallBtn);
        }
        manage.setName("group-manage");
        manage.setVisible(false);
        manage.addActionListener(event -> manageGroup());
        controls.add(manage);

        header.add(controls,BorderLayout.EAST);
        chat.add(header, BorderLayout.NORTH);

        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setBackground(Color.WHITE);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.getAccessibleContext().setAccessibleName("Lịch sử tin nhắn");
        chat.add(scroll);

        JPanel footer = panel(new BorderLayout());
        notice.setBorder(new EmptyBorder(0, 18, 0, 18));
        notice.getAccessibleContext().setAccessibleName("Thông báo thao tác");
        notice.setVisible(false);
        footer.add(notice, BorderLayout.NORTH);
        footer.add(composer);
        chat.add(footer, BorderLayout.SOUTH);
        return chat;
    }

    private ChatUser peer(Conversation conversation) {
        if (conversation.group()) {
            return null;
        }
        return conversation.memberIds().stream().filter(id -> !id.equals(self.id())).findFirst().map(users::get)
                .orElse(null);
    }

    private ConversationRenderer.Row row(Conversation conversation) {
        ChatUser peer = peer(conversation);
        ChatMessage preview = previews.get(conversation.id());
        String summary = preview == null ? "Chưa có tin nhắn" : (preview.senderId().equals(self.id()) ? "Bạn: " : "")
                + preview.content().preview();
        return new ConversationRenderer.Row(peer == null ? conversation.title() : peer.displayName(),
                peer == null ? conversation.id() : peer.id(), peer != null && peer.online(),
                summary, preview == null ? "" : time(preview.sentAt()));
    }

    private void refreshDirectory() {
        String query = searchKey(search.getText().strip());
        updatingList = true;
        listModel.clear();
        for (Conversation conversation : conversations.values()) {
            if (searchKey(row(conversation).title()).contains(query)) {
                listModel.addElement(conversation);
                if (conversation.id().equals(activeId)) {
                    list.setSelectedIndex(listModel.size() - 1);
                }
            }
        }
        updatingList = false;
        noConversations.setText(query.isEmpty() ? "Chưa có đoạn chat" : "Không tìm thấy đoạn chat");
        ((CardLayout) directoryCards.getLayout()).show(directoryCards, listModel.isEmpty() ? "empty" : "list");
        newGroup.setEnabled(actions.groupAvailable() && !creatingGroup && users.values().stream()
            .filter(user->!user.id().equals(self.id())&&user.online()).count()>0);
    }

    static String searchKey(String value) {
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd');
    }

    private void switchChat(String id) {
        if (Objects.equals(activeId, id)) {
            if (historyErrors.containsKey(id)) {
                load(id);
            }
            return;
        }
        composer.cancelRecording();
        composer.switchDraft(activeId, id);
        media.stopPlayback();
        activeId = id;
        rows.clear();
        showNotice("");
        refreshHeader();
        refreshMediaAvailability();
        if (id != null && !loaded.contains(id)) {
            load(id);
        }
        renderHistory(true);
    }

    private void refreshMediaAvailability() {
        Conversation chat = conversations.get(activeId);
        ChatUser other = chat == null ? null : peer(chat);
        boolean enabled = chat != null && (chat.group()?actions.groupOwnerOnline(chat.id()):other != null && other.online());
        composer.setMediaEnabled(enabled, chat != null && chat.group()
            ? "Chủ nhóm cần online và đồng bộ nhóm để chuyển tiếp media."
            : "Cần kết nối với thiết bị nhận trước khi gửi media.");
    }

    private void refreshHeader() {
        Conversation chat = conversations.get(activeId);
        manage.setVisible(actions.groupAvailable() && chat != null && chat.group());
        if (chat == null) {
            headerTitle.setText("Đoạn chat");
            headerAvatar.setIcon(null);
            headerState.setText(" ");
            voiceCallBtn.setEnabled(false);
            videoCallBtn.setEnabled(false);
            return;
        }
        ConversationRenderer.Row row = row(chat);
        headerTitle.setText(row.title());
        headerAvatar.setIcon(new AvatarIcon(row.title(), row.avatarId(), 40, row.online(), chat.group()));
        headerState.setText(chat.group() ? chat.memberIds().size() + " thành viên" +
                (!actions.groupOwnerOnline(chat.id()) ? " · Chưa kết nối / đồng bộ chủ nhóm" : "")
                + " · Media tự lưu"
                : row.online() ? "Đang hoạt động" : "Ngoại tuyến");
        boolean canCall = actions.callAvailable() && (chat.group() ? actions.groupOwnerOnline(chat.id()) : row.online());
        voiceCallBtn.setToolTipText(chat.group() ? "Gọi thoại nhóm" : "Gọi thoại P2P");
        videoCallBtn.setToolTipText(chat.group() ? "Gọi video nhóm" : "Gọi video P2P");
        voiceCallBtn.setEnabled(canCall);
        videoCallBtn.setEnabled(canCall);
    }

    private void load(String id) {
        if (!loading.add(id)) {
            return;
        }
        historyErrors.remove(id);
        renderHistory(false);
        actions.loadHistory(id).whenComplete((messages, failure) -> onEdt(() -> {
            loading.remove(id);
            if (failure == null) {
                loaded.add(id);
                messages.forEach(this::merge);
                refreshDirectory();
            } else {
                historyErrors.put(id, error(failure, "Không thể tải lịch sử."));
            }
            if (id.equals(activeId)) {
                renderHistory(true);
            }
        }));
    }

    private void manageGroup(){
        Conversation group=conversations.get(activeId);
        if(group==null||!group.group())return;
        String ownerId=actions.groupOwnerId(group.id());
        new GroupMembersDialog(this,group,self,Map.copyOf(users),ownerId,actions.groupOwnerOnline(group.id()),
            member->actions.addGroupMember(group.id(),member).whenComplete((ok,error)->onEdt(()->{
                if(error!=null)showNotice("Không thể mời: "+error(error,"Lỗi kết nối"));
                else showNotice("Đã gửi lời mời tham gia nhóm");
            })),
            member->actions.removeGroupMember(group.id(),member).whenComplete((ok,error)->onEdt(()->{
                if(error!=null)showNotice("Không thể loại: "+error(error,"Lỗi kết nối"));
            })),
            ()->actions.leaveGroup(group.id()).whenComplete((ok,error)->onEdt(()->{
                if(error!=null)showNotice("Không thể rời nhóm: "+error(error,"Lỗi kết nối"));
            }))).setVisible(true);
    }

    private void createGroup() {
        List<ChatUser> candidates = users.values().stream().filter(user -> !user.id().equals(self.id()) && user.online()).toList();
        new CreateGroupDialog(this, candidates).open().ifPresent(selection -> {
            creatingGroup = true;
            newGroup.setEnabled(false);
            showNotice("Đang tạo nhóm…");
            actions.createGroup(selection.title(), selection.ids()).whenComplete((group, failure) -> onEdt(() -> {
                creatingGroup = false;
                if (failure == null) {
                    conversations.put(group.id(), group);
                    search.setText("");
                    switchChat(group.id());
                } else {
                    showNotice(error(failure, "Không thể tạo nhóm. Hãy thử lại."));
                }
                refreshDirectory();
            }));
        });
    }

    // Xử lý thao tác gửi của người dùng, không chặn giao diện.
    private void send(String destination, MessageContent content) {
        showNotice("Đang gửi…");
        actions.sendMessage(destination, content).whenComplete((message, failure) -> onEdt(() -> {
            if (failure != null) {
                if (content.type() == MessageType.TEXT) {
                    composer.restoreText(destination, activeId, content.text());
                }
                showNotice(error(failure, "Không gửi được tin nhắn. Hãy thử lại."));
                return;
            }
            showNotice("");
            receive(message);
        }));
    }

    // Gộp tin nhắn và làm mới hội thoại đang hiển thị.
    private void receive(ChatMessage message) {
        if (merge(message)) {
            refreshDirectory();
            if (message.conversationId().equals(activeId)) {
                renderHistory(true);
            }
        }
    }

    private boolean merge(ChatMessage message) {
        updatePreview(message.conversationId(), message);
        return timeline(message.conversationId()).merge(message);
    }

    private void updatePreview(String id, ChatMessage message) {
        previews.merge(id, message, (current, next) -> ChatMessage.ORDER.compare(current, next) < 0 ? next : current);
    }

    private ChatTimeline timeline(String id) {
        return timelines.computeIfAbsent(id, ChatTimeline::new);
    }

    private void renderHistory(boolean bottom) {
        Conversation chat = conversations.get(activeId);
        JScrollBar scrollBar = scroll.getVerticalScrollBar();
        int previousScroll = scrollBar.getValue();
        boolean pinned = previousScroll + scrollBar.getVisibleAmount() >= scrollBar.getMaximum() - 2;
        List<ChatMessage> messages = chat == null ? List.of() : timeline(activeId).orderedMessages();
        if (messages.isEmpty()) {
            history.removeAll();
            showEmptyHistory();
        } else {
            ChatMessage lastMine = lastOutgoing(messages);
            boolean rebuild = history.getComponentCount() != messages.size();
            for (int index = 0; index < messages.size(); index++) {
                ChatMessage message = messages.get(index);
                MessageBubblePanel bubble = bubble(chat, message);
                String groupReceipt=chat.group()&&self.id().equals(message.senderId())
                    ?actions.groupReceiptLabel(chat.id(),message.id()):null;
                bubble.showReceipt(groupReceipt!=null || shouldShowOutgoingReceipt(message,lastMine,self.id())
                    ? message.state() : null,groupReceipt);
                if (!rebuild && history.getComponent(index) != bubble) {
                    rebuild = true;
                }
            }
            if (rebuild) {
                int index = 0;
                for (ChatMessage message : messages) {
                    MessageBubblePanel row = rows.get(message.id());
                    if (index >= history.getComponentCount() || history.getComponent(index) != row) {
                        if (row.getParent() == history) {
                            history.setComponentZOrder(row, index);
                        } else {
                            history.add(row, index);
                        }
                    }
                    index++;
                }
                while (history.getComponentCount() > index) {
                    history.remove(index);
                }
            }
        }
        history.revalidate();
        history.repaint();
        String target = activeId;
        SwingUtilities.invokeLater(() -> {
            if (!closed && Objects.equals(target, activeId)) {
                scroll.validate();
                var bar = scroll.getVerticalScrollBar();
                bar.setValue(bottom || pinned ? bar.getMaximum() : previousScroll);
            }
        });
    }

    /**
     * Earlier uncertain sends also need a visible manual-retry control.
     */
    static boolean shouldShowOutgoingReceipt(ChatMessage message,
                                             ChatMessage lastOutgoing,String localId){
        if(message==null||localId==null||!localId.equals(message.senderId()))return false;
        return message==lastOutgoing
            ||message.state()==DeliveryState.UNCERTAIN;
    }

    private ChatMessage lastOutgoing(List<ChatMessage> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            if (messages.get(index).senderId().equals(self.id())) {
                return messages.get(index);
            }
        }
        return null;
    }

    private MessageBubblePanel bubble(Conversation chat, ChatMessage message) {
        ChatUser author = users.get(message.senderId());
        boolean mine = message.senderId().equals(self.id());
        String sender = mine ? "Bạn" : author == null ? "Người dùng không xác định" : author.displayName();
        return rows.computeIfAbsent(message.id(),id->
            new MessageBubblePanel(message,mine,chat.group(),sender,media,
                mine&&message.content().type()==MessageType.TEXT ? this::manuallyRetry : null,
                fileTransfers.get(message.id()),this::fileAction));
    }

    /** Only invoked by an explicit visible UNCERTAIN message bubble button. */
    private void manuallyRetry(String messageId){
        if(activeId==null||messageId==null)return;
        String conversation=activeId;
        showNotice("Đang gửi lại thủ công; tin có thể đã đến trước đó và bị trùng.");
        actions.retryMessage(conversation,messageId).whenComplete((result,failure)->onEdt(()->{
            if(failure!=null)
                showNotice("Không thể gửi lại: "+error(failure,"Phiên không còn xác minh hoặc tin không ở trạng thái Chưa rõ."));
            else
                showNotice("Đã yêu cầu gửi lại. Chỉ ACK phía nhận mới xác nhận Đã nhận.");
        }));
    }

    private void showEmptyHistory() {
        JPanel empty = panel(new GridBagLayout());
        String text = activeId == null ? "Chọn một đoạn chat để bắt đầu."
                : loading.contains(activeId) ? "Đang tải lịch sử…"
                : historyErrors.getOrDefault(activeId, "Chưa có tin nhắn. Hãy gửi lời chào!");
        empty.add(label(text, 14, Font.PLAIN, MUTED));
        if (historyErrors.containsKey(activeId)) {
            JButton retry = new JButton("Thử lại");
            retry.addActionListener(event -> load(activeId));
            empty.add(retry);
        }
        empty.setAlignmentX(Component.LEFT_ALIGNMENT);
        history.add(empty);
    }

    private void showNotice(String message) {
        notice.setText(message);
        notice.setVisible(!message.isBlank());
    }

    private void onEdt(Runnable action) {
        SwingUtilities.invokeLater(() -> {
            if (!closed) {
                action.run();
            }
        });
    }

    @Override
    public void onDirectoryChanged(ChatSnapshot snapshot) {
        onEdt(() -> {
            users.clear();
            users.put(self.id(), self);
            snapshot.users().forEach(user -> users.put(user.id(), user));
            conversations.clear();
            snapshot.conversations().forEach(chat -> conversations.put(chat.id(), chat));
            // Retain in-memory timeline when peers disconnect; persistence follows in P4.
            snapshot.previews().forEach(this::updatePreview);
            if (activeId == null || !conversations.containsKey(activeId)) {
                switchChat(conversations.keySet().stream().findFirst().orElse(null));
            }
            refreshHeader();
            refreshMediaAvailability();
            refreshDirectory();
        });
    }

    private void fileAction(String action,String id){
        FileTransferService.Transfer t=fileTransfers.get(id);
        if(t==null)return;
        try{
            switch(action){
                case "accept" -> work.submit(()->{
                    try{actions.acceptFile(id,null);}catch(IOException ex){onEdt(()->showNotice(ex.getMessage()));}
                });
                case "reject" -> work.submit(()->{
                    try{actions.rejectFile(id);}catch(IOException ex){onEdt(()->showNotice(ex.getMessage()));}
                });
                case "cancel" -> work.submit(()->actions.cancelFile(id));
                case "open" -> {
                    Path path=t.localPath();
                    if(t.messageType()!=MessageType.FILE) break;
                    if(path!=null&&Files.isRegularFile(path))work.submit(()->{
                        try{
                            if(java.awt.Desktop.isDesktopSupported())
                                java.awt.Desktop.getDesktop().open(path.toFile());
                        }catch(IOException ex){onEdt(()->showNotice("Không mở được file: "+ex.getMessage()));}
                    });
                    else showNotice("File không còn tồn tại ở đường dẫn cục bộ.");
                }
            }
        }catch(RuntimeException ex){showNotice("Lỗi thao tác file: "+ex.getMessage());}
    }
    @Override public void onFileTransfer(FileTransferService.Transfer t){
        onEdt(()->{
            String id=t.transferId().toString();
            FileTransferService.Transfer previous=fileTransfers.put(id,t);
            MessageBubblePanel bubble=rows.get(id);
            // Rebuild when the file becomes readable (or an offer arrives after history).
            boolean ownMedia = self.id().equals(t.senderId().toString())
                && t.messageType()!=MessageType.FILE && t.localPath()!=null;
            if (previous==null || (ownMedia ? !Objects.equals(previous.localPath(), t.localPath())
                    : previous.state()!=FileTransferService.State.COMPLETED
                        && t.state()==FileTransferService.State.COMPLETED)) {
                rows.remove(id);
            } else if(bubble!=null) {
                bubble.showFileTransfer(t,this::fileAction);
            }
            if(t.conversationId().toString().equals(activeId))renderHistory(false);
        });
    }
    @Override
    public void onMessageReceived(ChatMessage message) {
        onEdt(() -> receive(message));
    }

    @Override
    public void onDeliveryStateChanged(String conversationId, String messageId, DeliveryState state) {
        onEdt(() -> {
            if (timeline(conversationId).advanceState(messageId, state) && conversationId.equals(activeId)) {
                renderHistory(false);
            }
        });
    }

    @Override
    public void onGroupReceiptChanged(String conversationId,String messageId){
        onEdt(()->{
            if(conversationId.equals(activeId))renderHistory(false);
        });
    }

    @Override
    public void onConnectionError(String message) {
        onEdt(() -> showNotice(message));
    }

    private void startCall(CallService.Mode mode) {
        String targetConv = activeId;
        if (targetConv == null) return;
        work.submit(() -> {
            try {
                actions.startCall(targetConv, mode);
            } catch (IOException ex) {
                onEdt(() -> showNotice(ex.getMessage()));
            }
        });
    }

    private CallDialog ensureCallDialog() {
        if (callDialog == null) {
            callDialog = new CallDialog(
                this,
                id -> work.submit(() -> {
                    try { actions.acceptCall(id); } catch (IOException ex) { onEdt(() -> showNotice(ex.getMessage())); }
                }),
                id -> work.submit(() -> actions.rejectCall(id)),
                id -> work.submit(() -> actions.endCall(id)),
                muted -> work.submit(() -> actions.setCallMuted(muted)),
                camOn -> work.submit(() -> actions.setCallCameraOn(camOn)),
                screen -> work.submit(() -> actions.setCallScreenShare(screen))
            );
        }
        return callDialog;
    }

    @Override
    public void onCallStateChanged(CallService.CallSession session) {
        onEdt(() -> {
            if (closed || session == null) return;
            if (session.state() == CallService.State.ENDED) {
                if (callDialog != null) callDialog.updateSession(session);
                if (session.reason() != null && !session.reason().isBlank()) {
                    showNotice("Cuộc gọi: " + session.reason());
                }
                return;
            }
            ensureCallDialog().updateSession(session);
        });
    }

    @Override
    public void onLocalVideoFrame(java.awt.image.BufferedImage frame) {
        onEdt(() -> {
            if (!closed && callDialog != null) callDialog.setLocalVideoFrame(frame);
        });
    }

    @Override
    public void onRemoteVideoFrame(java.awt.image.BufferedImage frame) {
        onEdt(() -> {
            if (!closed && callDialog != null) callDialog.setRemoteVideoFrame(frame);
        });
    }

    @Override
    public void onParticipantVideoFrame(java.util.UUID participantId, java.awt.image.BufferedImage frame) {
        onEdt(() -> {
            if (!closed && callDialog != null) callDialog.setParticipantVideoFrame(participantId, frame);
        });
    }

    /** The only read trigger: actual foreground Live Swing message row within scroll viewport. */
    private void checkLiveVisibleRows() {
        if (closed || activeId == null) return;
        java.util.List<LiveMessageVisibilityObserver.Row> samples = new ArrayList<>();
        for (ChatMessage message : timeline(activeId).orderedMessages()) {
            MessageBubblePanel bubble = rows.get(message.id());
            if (bubble == null) continue;
            samples.add(new LiveMessageVisibilityObserver.Row(activeId, message.id(),
                !message.senderId().equals(self.id()), bubble.isShowing(), bubble.getBounds()));
        }
        visibleMessages.observe(true, isShowing() && isActive()
            && WindowsForegroundGuard.isForeground(this), activeId,
            scroll.getViewport().getViewRect(), samples);
    }

    @Override
    public void dispose() {
        if (!closed) {
            closed = true;
            if (liveVisibilityTimer != null) liveVisibilityTimer.stop();
            if (callDialog != null) callDialog.closeSilently();
            composer.close();
            media.close();
            work.shutdownNow();
            actions.close();
        }
        super.dispose();
    }

    private static final class HistoryPanel extends JPanel implements Scrollable {
        private HistoryPanel() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(Color.WHITE);
            setBorder(new EmptyBorder(10, 0, 8, 0));
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 20; }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(20, visible.height - 20);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() {
            return getParent() instanceof JViewport viewport && getPreferredSize().height < viewport.getHeight();
        }
    }
}
