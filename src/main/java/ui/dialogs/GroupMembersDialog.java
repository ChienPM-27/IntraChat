package ui.dialogs;

import model.ChatUser;
import model.Conversation;
import ui.components.AvatarIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import static ui.components.ChatStyle.*;

/** Member roster, with explicit confirmation before any membership change. */
public final class GroupMembersDialog extends JDialog {
    public GroupMembersDialog(Window owner, Conversation group, ChatUser self,
                              Map<String,ChatUser> users, String ownerId,
                              boolean ownerReady, Consumer<String> remove, Runnable leave) {
        super(owner,"Thành viên nhóm",ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        boolean isOwner=Objects.equals(ownerId,self.id());
        JPanel root=panel(new BorderLayout(0,16));
        root.setBorder(new EmptyBorder(20,22,18,22));
        setContentPane(root);
        JPanel heading=panel(new BorderLayout(12,0));
        heading.add(new JLabel(new AvatarIcon(group.title(),group.id(),46,false,true)),BorderLayout.WEST);
        JPanel titles=panel(new GridLayout(2,1,0,4));
        JLabel title=label(group.title(),18,Font.BOLD,INK);
        title.setToolTipText(group.title());
        titles.add(title);
        titles.add(label(group.memberIds().size()+" thành viên",13,Font.PLAIN,MUTED));
        heading.add(titles);
        root.add(heading,BorderLayout.NORTH);

        JPanel roster=panel(new GridLayout(0,1));
        roster.setName("group-member-roster");
        for(String id:group.memberIds().stream().sorted(java.util.Comparator.comparing(id->!Objects.equals(ownerId,id))).toList()){
            boolean own=id.equals(self.id()), leader=id.equals(ownerId);
            ChatUser user=own?self:users.get(id);
            String name=user==null?id:user.displayName();
            boolean connected=own||user!=null&&user.online();
            JPanel row=panel(new BorderLayout(12,0));
            row.setName("group-member-"+id);
            row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,1,0,LINE),new EmptyBorder(12,12,12,12)));
            row.add(new JLabel(new AvatarIcon(name,id,40,connected,false)),BorderLayout.WEST);
            JPanel identity=panel(new GridLayout(2,1,0,3));
            JLabel display=label(name+(own?" (Bạn)":""),14,Font.BOLD,INK);
            display.setToolTipText(name+" · "+id);
            identity.add(display);
            String role=leader?"Chủ nhóm":"Thành viên";
            JLabel detail=label(role+" · "+(own?"Thiết bị này":connected?"Đã kết nối trực tiếp":"Không có kết nối trực tiếp"),12,Font.PLAIN,leader?BLUE:MUTED);
            identity.add(detail);
            row.add(identity);
            if(isOwner&&!own){
                JButton kick=secondary("Loại");
                kick.setForeground(RED);
                kick.setName("group-remove-"+id);
                kick.setToolTipText("Loại "+name+" khỏi nhóm");
                kick.getAccessibleContext().setAccessibleName("Loại "+name+" khỏi nhóm");
                kick.addActionListener(e->{
                    if(confirm("Loại thành viên","Loại "+name+" khỏi nhóm?","Loại thành viên")){
                        dispose();remove.accept(id);
                    }
                });
                JPanel slot=panel(new GridBagLayout());slot.add(kick);row.add(slot,BorderLayout.EAST);
            }
            roster.add(row);
        }
        JPanel listArea=panel(new BorderLayout());
        listArea.add(roster,BorderLayout.NORTH);
        JScrollPane scroll=new JScrollPane(listArea);
        scroll.setBorder(BorderFactory.createLineBorder(LINE,1,true));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.setPreferredSize(new Dimension(470,Math.min(350,Math.max(146,group.memberIds().size()*74))));
        root.add(scroll,BorderLayout.CENTER);
        JPanel footer=panel(new BorderLayout(0,8));
        footer.add(label("Trạng thái kết nối chỉ phản ánh liên kết trực tiếp với máy này.",12,Font.PLAIN,MUTED),BorderLayout.NORTH);
        JPanel buttons=panel(new FlowLayout(FlowLayout.TRAILING,8,0));
        if(!isOwner){
            JButton exit=secondary("Rời nhóm");exit.setForeground(RED);exit.setName("group-leave");
            exit.setEnabled(ownerReady);
            exit.setToolTipText(ownerReady?"Rời nhóm":"Cần kết nối và đồng bộ với chủ nhóm");
            exit.addActionListener(e->{
                if(confirm("Rời nhóm","Bạn muốn rời nhóm "+group.title()+"?","Rời nhóm")){
                    dispose();leave.run();
                }
            });buttons.add(exit);
        }
        JButton close=primary("Đóng");close.setName("group-members-close");close.addActionListener(e->dispose());
        buttons.add(close);footer.add(buttons,BorderLayout.SOUTH);root.add(footer,BorderLayout.SOUTH);
        getRootPane().setDefaultButton(close);
        getRootPane().registerKeyboardAction(e->dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();setMinimumSize(new Dimension(480,getHeight()));setLocationRelativeTo(owner);
    }

    private boolean confirm(String title,String message,String action){
        JDialog dialog=new JDialog(this,title,ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel root=panel(new BorderLayout(0,16));root.setBorder(new EmptyBorder(18,20,18,20));
        JTextArea text=new JTextArea(message,2,32);text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);
        text.setBackground(Color.WHITE);text.setForeground(INK);text.setFocusable(false);root.add(text);
        boolean[] accepted={false};
        JPanel buttons=panel(new FlowLayout(FlowLayout.TRAILING,8,0));
        JButton cancel=secondary("Hủy"),yes=primary(action);
        yes.setBackground(RED);yes.setName("group-confirm");cancel.setName("group-confirm-cancel");
        cancel.addActionListener(e->dialog.dispose());yes.addActionListener(e->{accepted[0]=true;dialog.dispose();});
        buttons.add(cancel);buttons.add(yes);root.add(buttons,BorderLayout.SOUTH);
        dialog.setContentPane(root);dialog.getRootPane().setDefaultButton(cancel);
        dialog.getRootPane().registerKeyboardAction(e->dialog.dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack();dialog.setLocationRelativeTo(this);dialog.setVisible(true);return accepted[0];
    }
}
