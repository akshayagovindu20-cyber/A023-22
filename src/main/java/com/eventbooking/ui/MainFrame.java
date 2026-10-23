package com.eventbooking.ui;

import com.eventbooking.client.BookingClient;
import com.eventbooking.model.Booking;
import com.eventbooking.model.Event;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class MainFrame extends JFrame {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final BookingClient client;
    private final BookingClient.LoginInfo login;
    private final JLabel status = new JLabel("Ready");
    private final DefaultTableModel eventModel = model(
            "ID", "Event", "Date and time", "Venue", "Total seats", "Booked", "Available");
    private final DefaultTableModel bookingModel = model(
            "Booking ID", "Event", "Seats", "Status", "Booked at");
    private final DefaultTableModel adminBookingModel = model(
            "Booking ID", "Customer", "Event", "Seats", "Status", "Booked at");
    private final JTable eventTable = new JTable(eventModel);
    private final JTable bookingTable = new JTable(bookingModel);
    private final JTable adminBookingTable = new JTable(adminBookingModel);
    private List<Event> currentEvents = List.of();
    private List<Booking> currentBookings = List.of();

    public MainFrame(BookingClient client, BookingClient.LoginInfo login) {
        super("Event Booking — " + login.fullName());
        this.client = client;
        this.login = login;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1050, 650);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
        JLabel heading = new JLabel(("ADMIN".equals(login.role()) ? "Administrator dashboard" : "Welcome, "
                + login.fullName()));
        heading.setFont(new Font("Segoe UI", Font.BOLD, 22));
        root.add(heading, BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        if ("ADMIN".equals(login.role())) {
            tabs.addTab("Events", adminEventsPanel());
            tabs.addTab("All bookings", adminBookingsPanel());
        } else {
            tabs.addTab("Browse events", customerEventsPanel());
            tabs.addTab("My bookings", customerBookingsPanel());
        }
        root.add(tabs, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        status.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 2));
        JButton logout = new JButton("Log out");
        footer.add(status, BorderLayout.CENTER);
        footer.add(logout, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
        logout.addActionListener(event -> logout());
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                logout();
            }
        });
        refreshEvents();
        if (!"ADMIN".equals(login.role())) {
            refreshBookings();
        } else {
            refreshAdminBookings();
        }
    }

    private JPanel customerEventsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JScrollPane(eventTable), BorderLayout.CENTER);
        JButton refresh = new JButton("Refresh");
        JButton book = new JButton("Book selected event");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(refresh);
        actions.add(book);
        panel.add(actions, BorderLayout.SOUTH);
        refresh.addActionListener(event -> refreshEvents());
        book.addActionListener(event -> bookSelectedEvent());
        return panel;
    }

    private JPanel customerBookingsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JScrollPane(bookingTable), BorderLayout.CENTER);
        JButton refresh = new JButton("Refresh");
        JButton cancel = new JButton("Cancel selected booking");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(refresh);
        actions.add(cancel);
        panel.add(actions, BorderLayout.SOUTH);
        refresh.addActionListener(event -> refreshBookings());
        cancel.addActionListener(event -> cancelCustomerBooking());
        return panel;
    }

    private JPanel adminEventsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JScrollPane(eventTable), BorderLayout.CENTER);
        JButton refresh = new JButton("Refresh");
        JButton add = new JButton("Add event");
        JButton edit = new JButton("Edit selected");
        JButton delete = new JButton("Delete selected");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(refresh);
        actions.add(add);
        actions.add(edit);
        actions.add(delete);
        panel.add(actions, BorderLayout.SOUTH);
        refresh.addActionListener(event -> refreshEvents());
        add.addActionListener(event -> showEventDialog(null));
        edit.addActionListener(event -> showEventDialog(selectedEvent()));
        delete.addActionListener(event -> deleteSelectedEvent());
        return panel;
    }

    private JPanel adminBookingsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JScrollPane(adminBookingTable), BorderLayout.CENTER);
        JButton refresh = new JButton("Refresh");
        JButton cancel = new JButton("Cancel selected booking");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(refresh);
        actions.add(cancel);
        panel.add(actions, BorderLayout.SOUTH);
        refresh.addActionListener(event -> refreshAdminBookings());
        cancel.addActionListener(event -> cancelAdminBooking());
        return panel;
    }

    private void bookSelectedEvent() {
        Event selected = selectedEvent();
        if (selected == null) {
            showMessage("Select an event first.", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (selected.availableSeats() == 0) {
            showMessage("There are no seats available for this event.", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JSpinner seats = new JSpinner(new SpinnerNumberModel(1, 1,
                Math.min(20, selected.availableSeats()), 1));
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT));
        form.add(new JLabel("Number of seats (maximum 20 per booking):"));
        form.add(seats);
        if (JOptionPane.showConfirmDialog(this, form,
                "Book " + selected.eventName(), JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        int count = (Integer) seats.getValue();
        runAsync(() -> client.book(selected.eventId(), count), booking -> {
            JOptionPane.showMessageDialog(this, "Your seats are confirmed.\n\nBooking ID: "
                    + booking.bookingId() + "\nEvent: " + booking.eventName()
                    + "\nSeats: " + booking.seatsBooked(), "Booking confirmed",
                    JOptionPane.INFORMATION_MESSAGE);
            refreshEvents();
            refreshBookings();
        });
    }

    private void cancelCustomerBooking() {
        if (!selectedRowIsConfirmed(bookingTable, bookingModel, currentBookings)) {
            return;
        }
        Booking booking = currentBookings.get(bookingTable.convertRowIndexToModel(bookingTable.getSelectedRow()));
        cancelBooking(booking.bookingId(), this::refreshBookings);
    }

    private void cancelAdminBooking() {
        int row = adminBookingTable.getSelectedRow();
        if (row < 0) {
            showMessage("Select a booking first.", JOptionPane.WARNING_MESSAGE);
            return;
        }
        Booking booking = currentBookings.get(adminBookingTable.convertRowIndexToModel(row));
        if (!"CONFIRMED".equals(booking.bookingStatus())) {
            showMessage("This booking is already cancelled.", JOptionPane.WARNING_MESSAGE);
            return;
        }
        cancelBooking(booking.bookingId(), () -> {
            refreshAdminBookings();
            refreshEvents();
        });
    }

    private void cancelBooking(String bookingId, Runnable refresh) {
        if (JOptionPane.showConfirmDialog(this, "Cancel booking " + bookingId + "?",
                "Confirm cancellation", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        runAsync(() -> {
            client.cancel(bookingId);
            return null;
        }, ignored -> {
            status.setText("Booking cancelled and seats restored.");
            refresh.run();
        });
    }

    private boolean selectedRowIsConfirmed(JTable table, DefaultTableModel model, List<Booking> bookings) {
        int row = table.getSelectedRow();
        if (row < 0) {
            showMessage("Select a booking first.", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        Booking selected = bookings.get(table.convertRowIndexToModel(row));
        if (!"CONFIRMED".equals(selected.bookingStatus())) {
            showMessage("This booking is already cancelled.", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private void showEventDialog(Event existing) {
        javax.swing.JTextField name = new javax.swing.JTextField(existing == null ? "" : existing.eventName(), 24);
        javax.swing.JTextField date = new javax.swing.JTextField(existing == null ? ""
                : existing.eventDate().withNano(0).toString(), 24);
        javax.swing.JTextField venue = new javax.swing.JTextField(existing == null ? "" : existing.venue(), 24);
        JSpinner seats = new JSpinner(new SpinnerNumberModel(
                existing == null ? 50 : existing.totalSeats(), 1, 100_000, 1));
        JPanel form = new JPanel(new java.awt.GridLayout(8, 1, 0, 4));
        form.add(new JLabel("Event name"));
        form.add(name);
        form.add(new JLabel("Date/time (yyyy-MM-ddTHH:mm:ss)"));
        form.add(date);
        form.add(new JLabel("Venue"));
        form.add(venue);
        form.add(new JLabel("Total seats"));
        form.add(seats);
        String title = existing == null ? "Add event" : "Edit event";
        if (JOptionPane.showConfirmDialog(this, form, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        int count = (Integer) seats.getValue();
        runAsync(() -> existing == null
                        ? client.createEvent(name.getText(), date.getText(), venue.getText(), count)
                        : client.updateEvent(existing.eventId(), name.getText(), date.getText(), venue.getText(), count),
                event -> {
                    status.setText(existing == null ? "Event created." : "Event updated.");
                    refreshEvents();
                });
    }

    private void deleteSelectedEvent() {
        Event selected = selectedEvent();
        if (selected == null) {
            showMessage("Select an event first.", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (JOptionPane.showConfirmDialog(this,
                "Delete \"" + selected.eventName() + "\"?\nEvents with booking history cannot be deleted.",
                "Delete event", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        runAsync(() -> {
            client.deleteEvent(selected.eventId());
            return null;
        }, ignored -> {
            status.setText("Event deleted.");
            refreshEvents();
        });
    }

    private Event selectedEvent() {
        int row = eventTable.getSelectedRow();
        return row < 0 ? null : currentEvents.get(eventTable.convertRowIndexToModel(row));
    }

    private void refreshEvents() {
        runAsync(client::events, events -> {
            currentEvents = events;
            eventModel.setRowCount(0);
            for (Event event : events) {
                eventModel.addRow(new Object[]{event.eventId(), event.eventName(),
                        DATE_FORMAT.format(event.eventDate()), event.venue(),
                        event.totalSeats(), event.totalSeats() - event.availableSeats(), event.availableSeats()});
            }
            status.setText(events.size() + " event(s) loaded.");
        });
    }

    private void refreshBookings() {
        runAsync(client::myBookings, bookings -> {
            currentBookings = bookings;
            bookingModel.setRowCount(0);
            for (Booking booking : bookings) {
                bookingModel.addRow(new Object[]{booking.bookingId(), booking.eventName(),
                        booking.seatsBooked(), booking.bookingStatus(), DATE_FORMAT.format(booking.bookingTime())});
            }
            status.setText(bookings.size() + " booking(s) loaded.");
        });
    }

    private void refreshAdminBookings() {
        runAsync(client::allBookings, bookings -> {
            currentBookings = bookings;
            adminBookingModel.setRowCount(0);
            for (Booking booking : bookings) {
                adminBookingModel.addRow(new Object[]{booking.bookingId(), booking.customerName(),
                        booking.eventName(), booking.seatsBooked(), booking.bookingStatus(),
                        DATE_FORMAT.format(booking.bookingTime())});
            }
            status.setText(bookings.size() + " booking(s) in the system.");
        });
    }

    private <T> void runAsync(Callable<T> operation, Consumer<T> onSuccess) {
        runAsync(operation, onSuccess, ex -> showMessage(errorMessage(ex), JOptionPane.ERROR_MESSAGE));
    }

    private <T> void runAsync(Callable<T> operation, Consumer<T> onSuccess, Consumer<Exception> onFailure) {
        status.setText("Working...");
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return operation.call();
            }

            @Override
            protected void done() {
                try {
                    T result = get();
                    onSuccess.accept(result);
                } catch (Exception ex) {
                    onFailure.accept(ex);
                }
            }
        }.execute();
    }

    private String errorMessage(Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null ? "The request failed." : cause.getMessage();
    }

    private void logout() {
        setEnabled(false);
        runAsync(() -> {
            client.logout();
            return null;
        }, ignored -> finishLogout(), ex -> {
            client.close();
            finishLogout();
        });
    }

    private void finishLogout() {
        client.close();
        dispose();
        new LoginFrame(client).setVisible(true);
    }

    private void showMessage(String message, int type) {
        JOptionPane.showMessageDialog(this, message, "Event Booking", type);
    }

    private static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }
}
