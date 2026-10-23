package com.eventbooking.client;

import com.eventbooking.ui.LoginFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class ClientMain {
    private ClientMain() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
                System.err.println("Using the default Swing theme: " + ex.getMessage());
            }
            new LoginFrame(new BookingClient()).setVisible(true);
        });
    }
}
