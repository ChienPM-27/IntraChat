package ui.dialogs;

import model.ChatUser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ui.components.ChatStyle.*;

public final class CreateGroupDialog extends JDialog {
    private final JTextField name = new JTextField();
    private final Map<String, JCheckBox> members = new LinkedHashMap<>();
    private final JLabel error = label(" ", 12, Font.PLAIN, RED);
    private Selection selection;

    public CreateGroupDialog(Window owner, List<ChatUser> users) {
        super(owner, "Tạo nhóm mới", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel root = panel(new BorderLayout(0, 14));
        root.setBorder(new EmptyBorder(18, 20, 16, 20));
        setContentPane(root);
        JPanel details = panel(new BorderLayout(0, 8));
        JLabel title = label("Tên nhóm", 14, Font.BOLD, INK);
        title.setLabelFor(name);
        details.add(title, BorderLayout.NORTH);
        field(name, "Nhập tên nhóm");
        name.setPreferredSize(new Dimension(340, 36));
        details.add(name);
        details.add(label("Chọn thành viên", 14, Font.BOLD, INK), BorderLayout.SOUTH);
        root.add(details, BorderLayout.NORTH);

        JPanel list = panel(null);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        for (ChatUser user : users) {
            JCheckBox option = new JCheckBox();
            option.putClientProperty("html.disable", true);
            option.setText(user.displayName() + (user.online() ? " · Đang hoạt động" : " · Ngoại tuyến"));
            option.setBackground(Color.WHITE);
            option.setBorder(new EmptyBorder(5, 5, 5, 5));
            members.put(user.id(), option);
            list.add(option);
        }
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(340, 125));
        scroll.setBorder(BorderFactory.createLineBorder(LINE));
        root.add(scroll);

        JPanel footer = panel(new BorderLayout(0, 6));
        JPanel buttons = panel(new FlowLayout(FlowLayout.TRAILING, 6, 0));
        JButton cancel = secondary("Hủy");
        cancel.setName("group-cancel");
        cancel.addActionListener(event -> dispose());
        JButton create = primary("Tạo nhóm");
        create.setName("group-create");
        create.addActionListener(event -> confirm());
        buttons.add(cancel);
        buttons.add(create);
        footer.add(error, BorderLayout.NORTH);
        footer.add(buttons, BorderLayout.SOUTH);
        root.add(footer, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(create);
        getRootPane().registerKeyboardAction(event -> dispose(), KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(owner);
    }

    private void confirm() {
        String title = name.getText().strip();
        List<String> selected = members.entrySet().stream().filter(entry -> entry.getValue().isSelected())
                .map(Map.Entry::getKey).toList();
        if (title.isEmpty() || title.length() > 60 || selected.isEmpty()) {
            error.setText("Nhập tên nhóm (tối đa 60 ký tự) và chọn thành viên.");
            return;
        }
        selection = new Selection(title, selected);
        dispose();
    }

    public Optional<Selection> open() {
        setVisible(true);
        return Optional.ofNullable(selection);
    }

    public record Selection(String title, List<String> ids) {
    }
}
