package com.eventbooking.ui;

import com.eventbooking.client.BookingClient;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.Arrays;

public class LoginFrame extends JFrame {
    private static final Color NAVY = new Color(30, 48, 78);
    private static final Color BLUE = new Color(45, 105, 180);
    private final BookingClient client;
    private final JTextField emailField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JLabel status = new JLabel("Connect to your event booking server to continue.");
    private final JButton loginButton = new JButton("Log in");

    public LoginFrame(BookingClient client) {
        super("Distributed Event Booking");
        this.client = client;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(470, 360));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBorder(BorderFactory.createEmptyBorder(28, 36, 28, 36));
        JLabel title = new JLabel("Event Booking", JLabel.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 28));
        title.setForeground(NAVY);
        JLabel subtitle = new JLabel("Sign in to discover and reserve campus events.", JLabel.CENTER);
        subtitle.setForeground(new Color(90, 100, 115));
        JPanel heading = new JPanel(new GridLayout(2, 1, 0, 6));
        heading.add(title);
        heading.add(subtitle);
        root.add(heading, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridLayout(4, 1, 0, 6));
        form.add(new JLabel("Email address"));
        form.add(emailField);
        form.add(new JLabel("Password"));
        form.add(passwordField);
        root.add(form, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(3, 1, 0, 8));
        loginButton.setBackground(BLUE);
        loginButton.setForeground(Color.WHITE);
        loginButton.setFocusPainted(false);
        JButton register = new JButton("Create a customer account");
        status.setForeground(new Color(90, 100, 115));
        actions.add(loginButton);
        actions.add(register);
        actions.add(status);
        root.add(actions, BorderLayout.SOUTH);
        setContentPane(root);

        loginButton.addActionListener(event -> login());
        passwordField.addActionListener(event -> login());
        register.addActionListener(event -> register());
    }

    private void login() {
        String email = emailField.getText().trim();
        char[] password = passwordField.getPassword();
        setBusy(true, "Signing in...");
        new SwingWorker<BookingClient.LoginInfo, Void>() {
            @Override
            protected BookingClient.LoginInfo doInBackground() throws IOException {
                return client.login(email, password);
            }

            @Override
            protected void done() {
                Arrays.fill(password, '\0');
                try {
                    BookingClient.LoginInfo login = get();
                    new MainFrame(client, login).setVisible(true);
                    dispose();
                } catch (Exception ex) {
                    showError(ex);
                    setBusy(false, "Sign-in failed.");
                }
            }
        }.execute();
    }

    private void register() {
        JTextField name = new JTextField();
        JTextField email = new JTextField(emailField.getText().trim());
        JPasswordField password = new JPasswordField();
        JPanel form = new JPanel(new GridLayout(6, 1, 0, 5));
        form.add(new JLabel("Full name"));
        form.add(name);
        form.add(new JLabel("Email address"));
        form.add(email);
        form.add(new JLabel("Password (at least 8 characters)"));
        form.add(password);
        if (JOptionPane.showConfirmDialog(this, form, "Create customer account",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            Arrays.fill(password.getPassword(), '\0');
            return;
        }
        char[] secret = password.getPassword();
        setBusy(true, "Creating account...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws IOException {
                client.register(name.getText(), email.getText(), secret);
                return null;
            }

            @Override
            protected void done() {
                Arrays.fill(secret, '\0');
                try {
                    get();
                    emailField.setText(email.getText().trim());
                    passwordField.setText("");
                    setBusy(false, "Account created. Please log in.");
                } catch (Exception ex) {
                    showError(ex);
                    setBusy(false, "Registration failed.");
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy, String message) {
        loginButton.setEnabled(!busy);
        status.setText(message);
    }

    private void showError(Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        String message = cause.getMessage() == null ? "Request failed." : cause.getMessage();
        JOptionPane.showMessageDialog(this, message, "Request failed", JOptionPane.ERROR_MESSAGE);
    }
}
