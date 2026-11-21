package ui.frames;

import com.formdev.flatlaf.FlatClientProperties;
import listener.ChatActionHandler;
import listener.ChatClientListener;
import model.*;
import ui.components.*;
import ui.dialogs.CreateGroupDialog;

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

    public MainChatFrame(ChatUser self, ChatActionHandler actions) {
        super(actions.liveMode() ? "IntraChat — Live LAN (mã hóa, đã xác minh)" : "IntraChat — Demo");
        this.self = self;
        this.actions = actions;
        this.visibleMessages = new LiveMessageVisibilityObserver(System::nanoTime,
            (conversationId,messageId) -> this.actions.onMessageVisible(conversationId,messageId));
        users.put(self.id(), self);
        setIconImage(LoginFrame.iconImage());
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1100, 720);
        setMinimumSize(new Dimension(760, 520));
        setLocationRelativeTo(null);
        JPanel root = panel(new BorderLayout());
        add(root);
        root.add(buildSidebar(), BorderLayout.WEST);
        media = new MessageMedia(work, this, this::showNotice);
        composer = new ComposerPanel(work, () -> activeId, this::send, this::showNotice);
        if (actions.liveMode()) {
            composer.setTextOnly(true);
            newGroup.setVisible(false);
        }
        root.add(buildChat());
        refreshDirectory();
        renderHistory(false);
        actions.connect(this);
        if (actions.liveMode()) {
            liveVisibilityTimer = new javax.swing.Timer(100, event -> checkLiveVisibleRows());
            liveVisibilityTimer.start();
        }
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
        profile.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, LINE),
                new EmptyBorder(10, 14, 10, 14)));
        profile.add(new JLabel(new AvatarIcon(self.displayName(), self.id(), 34, false, false)), BorderLayout.WEST);
        JPanel identity = panel(new GridLayout(2, 1));
        JLabel profileName = label(self.displayName(), 13, Font.BOLD, INK);
        profileName.setToolTipText(self.displayName());
        identity.add(profileName);
        identity.add(label(actions.liveMode()
                ? "Live LAN · Đã xác minh key · Đã xem = tin đã hiển thị"
                : "Phiên demo · Chỉ trên máy này", 11, Font.PLAIN, MUTED));
        profile.add(identity);
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
        newGroup.setEnabled(!actions.liveMode() && !creatingGroup && users.size() > 1);
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
        composer.refresh();
        if (id != null && !loaded.contains(id)) {
            load(id);
        }
        renderHistory(true);
    }

    private void refreshHeader() {
        Conversation chat = conversations.get(activeId);
        if (chat == null) {
            headerTitle.setText("Đoạn chat");
            headerAvatar.setIcon(null);
            headerState.setText(" ");
            return;
        }
        ConversationRenderer.Row row = row(chat);
        headerTitle.setText(row.title());
        headerAvatar.setIcon(new AvatarIcon(row.title(), row.avatarId(), 40, row.online(), chat.group()));
        headerState.setText(chat.group() ? chat.memberIds().size() + " thành viên"
                : row.online() ? "Đang hoạt động" : "Ngoại tuyến");
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

    private void createGroup() {
        List<ChatUser> candidates = users.values().stream().filter(user -> !user.id().equals(self.id())).toList();
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
                bubble.showReceipt(message == lastMine ? message.state() : null);
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
        return rows.computeIfAbsent(message.id(),
                id -> new MessageBubblePanel(message, mine, chat.group(), sender, media));
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
            snapshot.previews().forEach(this::updatePreview);
            if (activeId == null || !conversations.containsKey(activeId)) {
                switchChat(conversations.keySet().stream().findFirst().orElse(null));
            }
            refreshHeader();
            refreshDirectory();
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
    public void onConnectionError(String message) {
        onEdt(() -> showNotice(message));
    }

    /** The only read trigger: actual foreground Live Swing message row within scroll viewport. */
    private void checkLiveVisibleRows() {
        if (closed || !actions.liveMode() || activeId == null) return;
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
