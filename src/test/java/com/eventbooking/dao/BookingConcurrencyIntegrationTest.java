package com.eventbooking.dao;

import com.eventbooking.model.Booking;
import com.eventbooking.model.Event;
import com.eventbooking.service.BookingService;
import com.eventbooking.service.AuthService;
import com.eventbooking.util.Database;
import com.eventbooking.util.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "RUN_DB_INTEGRATION_TESTS", matches = "true")
class BookingConcurrencyIntegrationTest {
    @Test
    void concurrentBookingsNeverOverdrawAndCancellationRestoresOnce() throws Exception {
        Fixture fixture = new Fixture(1);
        try {
            List<Attempt> attempts = race(fixture, 1, 1);
            assertEquals(1, attempts.stream().filter(Attempt::succeeded).count());
            assertEquals(0, fixture.findEvent().availableSeats());

            Booking confirmed = attempts.stream().filter(Attempt::succeeded)
                    .map(Attempt::booking).findFirst().orElseThrow();
            fixture.service.cancel(confirmed.bookingId(), fixture.userIds.get(0), false);
            assertEquals(1, fixture.findEvent().availableSeats());
            assertThrows(IllegalArgumentException.class,
                    () -> fixture.service.cancel(confirmed.bookingId(), fixture.userIds.get(0), false));
            assertEquals(1, fixture.findEvent().availableSeats());
        } finally {
            fixture.close();
        }
    }

    @Test
    void twoClientsCanBookDifferentSeatsButCannotExceedRemainingCapacity() throws Exception {
        Fixture fixture = new Fixture(5);
        try {
            List<Attempt> attempts = race(fixture, 2, 2);
            assertEquals(2, attempts.stream().filter(Attempt::succeeded).count());
            assertEquals(1, fixture.findEvent().availableSeats());
            assertThrows(IllegalArgumentException.class,
                    () -> fixture.service.book(fixture.userIds.get(0), fixture.eventId, 2));
            assertEquals(1, fixture.findEvent().availableSeats());
        } finally {
            fixture.close();
        }
    }

    @Test
    void rejectsInvalidCredentialsEventIdsAndSeatCountsWithoutChangingInventory() throws Exception {
        Fixture fixture = new Fixture(5);
        try {
            AuthService auth = new AuthService(new UserDao());
            assertThrows(SecurityException.class, () -> auth.login(
                    "missing-" + UUID.randomUUID() + "@example.test", "incorrect-password".toCharArray()));
            assertThrows(IllegalArgumentException.class,
                    () -> fixture.service.book(fixture.userIds.get(0), Long.MAX_VALUE, 1));
            assertThrows(IllegalArgumentException.class,
                    () -> fixture.service.book(fixture.userIds.get(0), fixture.eventId, 0));
            assertEquals(5, fixture.findEvent().availableSeats());
        } finally {
            fixture.close();
        }
    }

    private List<Attempt> race(Fixture fixture, int firstSeats, int secondSeats) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Attempt>> futures = new ArrayList<>();
            int[] counts = {firstSeats, secondSeats};
            for (int i = 0; i < 2; i++) {
                long userId = fixture.userIds.get(i);
                int seats = counts[i];
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent test did not start.");
                    }
                    try {
                        return new Attempt(true, fixture.service.book(userId, fixture.eventId, seats));
                    } catch (IllegalArgumentException ex) {
                        return new Attempt(false, null);
                    }
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(futures.get(0).get(15, TimeUnit.SECONDS),
                    futures.get(1).get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private record Attempt(boolean succeeded, Booking booking) {
    }

    private static final class Fixture implements AutoCloseable {
        private final EventDao eventDao = new EventDao();
        private final BookingService service = new BookingService(new BookingDao(), eventDao);
        private final List<Long> userIds = new ArrayList<>();
        private final long eventId;

        private Fixture(int seats) throws SQLException {
            String suffix = UUID.randomUUID().toString();
            UserDao users = new UserDao();
            for (int index = 0; index < 2; index++) {
                userIds.add(users.create("Integration User " + index, index + "-" + suffix + "@example.test",
                        PasswordHasher.hash("integration-password".toCharArray()), "USER").userId());
            }
            Event event = eventDao.create(new com.eventbooking.util.Validation.EventInput(
                    "Concurrency Test " + suffix, java.time.LocalDateTime.now().plusDays(40),
                    "Integration Hall", seats));
            eventId = event.eventId();
        }

        private Event findEvent() throws SQLException {
            return eventDao.findAll().stream().filter(event -> event.eventId() == eventId)
                    .findFirst().orElseThrow();
        }

        @Override
        public void close() throws SQLException {
            try (Connection connection = Database.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement deleteBookings = connection.prepareStatement(
                        "DELETE FROM bookings WHERE event_id = ?")) {
                    deleteBookings.setLong(1, eventId);
                    deleteBookings.executeUpdate();
                }
                try (PreparedStatement deleteEvent = connection.prepareStatement(
                        "DELETE FROM events WHERE event_id = ?")) {
                    deleteEvent.setLong(1, eventId);
                    deleteEvent.executeUpdate();
                }
                try (PreparedStatement deleteUsers = connection.prepareStatement(
                        "DELETE FROM users WHERE user_id = ? OR user_id = ?")) {
                    deleteUsers.setLong(1, userIds.get(0));
                    deleteUsers.setLong(2, userIds.get(1));
                    deleteUsers.executeUpdate();
                }
                connection.commit();
            }
        }
    }
}
