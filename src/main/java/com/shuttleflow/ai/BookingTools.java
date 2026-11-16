package com.shuttleflow.ai;

import com.shuttleflow.dto.AppointmentDto;
import com.shuttleflow.dto.BookingRequest;
import com.shuttleflow.dto.SlotDto;
import com.shuttleflow.service.AppointmentService;
import com.shuttleflow.service.BookingConflictException;
import com.shuttleflow.service.ConflictException;
import com.shuttleflow.service.ForbiddenException;
import com.shuttleflow.service.InvalidRequestException;
import com.shuttleflow.service.NotFoundException;
import com.shuttleflow.service.SlotService;
import com.shuttleflow.service.UnauthorizedException;
import jakarta.servlet.http.HttpSession;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * The chat agent's tools. One instance per chat request, holding that request's session, so the
 * model acts as the logged-in user and can never name another user. Tools call services only,
 * never repositories, so every rule of the REST API (RBAC, locks, constraints) still applies.
 * Errors go back to the model as text it can explain to the user.
 */
public class BookingTools {
    static final int MAX_RESULTS = 10;
    static final int MAX_RANGE_DAYS = 14;

    private final SlotService slotService;
    private final AppointmentService appointmentService;
    private final HttpSession session;
    private final int turn;

    /** {@code turn} is this chat request's turn number; confirms need a pending action from an earlier one. */
    public BookingTools(SlotService slotService, AppointmentService appointmentService, HttpSession session,
                        int turn) {
        this.slotService = slotService;
        this.appointmentService = appointmentService;
        this.session = session;
        this.turn = turn;
    }

    @Tool(description = "Find open (bookable) slots between two dates, earliest first, at most 10. "
            + "Use before proposing a booking or when a requested slot is taken.")
    public String findOpenSlots(
            @ToolParam(description = "First day, YYYY-MM-DD. Omit for today.", required = false) String from,
            @ToolParam(description = "Last day, YYYY-MM-DD, at most 14 days after from. Omit for from + 6 days.",
                    required = false) String to,
            @ToolParam(description = "OPEN_PLAY for court rental or COACHING for a coach. Omit for both.",
                    required = false) String sessionType) {
        return run(() -> {
            LocalDate start = isBlank(from) ? LocalDate.now() : parseDate(from);
            LocalDate end = isBlank(to) ? start.plusDays(6) : parseDate(to);
            if (end.isBefore(start) || end.isAfter(start.plusDays(MAX_RANGE_DAYS))) {
                return "The end date must be on or after the start date and at most " + MAX_RANGE_DAYS
                        + " days later.";
            }
            if (start.isBefore(LocalDate.now())) {
                start = LocalDate.now();
            }
            String type = isBlank(sessionType) ? null : sessionType.trim().toUpperCase();
            // Page 1 of each day already holds that day's earliest slots, so merging them is enough.
            List<SlotDto> slots = new ArrayList<>();
            for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                slots.addAll(slotService.getAvailableSlots(null, type, null, day, 1));
            }
            slots.sort(Comparator.comparing(SlotDto::getStartTime).thenComparing(SlotDto::getSlotId));
            if (slots.isEmpty()) {
                return "No open slots between " + start + " and " + end + ".";
            }
            StringBuilder out = new StringBuilder();
            slots.stream().limit(MAX_RESULTS).forEach(s -> out.append("slotId=").append(s.getSlotId())
                    .append(" | ").append(s.getProviderName()).append(" | ").append(s.getServiceName())
                    .append(" | ").append(s.getStartTime()).append(" to ").append(s.getEndTime().toLocalTime())
                    .append(" | $").append(s.getPrice()).append('\n'));
            return out.toString().trim();
        });
    }

    @Tool(description = "List the logged-in customer's upcoming booked appointments, earliest first, at most 10.")
    public String listMyAppointments() {
        return run(() -> {
            List<AppointmentDto> appointments = appointmentService.list("upcoming", session);
            if (appointments.isEmpty()) {
                return "You have no upcoming appointments.";
            }
            StringBuilder out = new StringBuilder();
            appointments.stream().limit(MAX_RESULTS).forEach(a -> out.append("appointmentId=")
                    .append(a.getAppointmentId()).append(" | ").append(a.getProviderName()).append(" | ")
                    .append(a.getServiceName()).append(" | ").append(a.getStartTime()).append(" to ")
                    .append(a.getEndTime().toLocalTime()).append(" | ").append(a.getStatus()).append('\n'));
            return out.toString().trim();
        });
    }

    @Tool(description = "Step 1 of booking: record the slot the user wants. Does NOT book. Afterwards, show the "
            + "user the slot details and ask them to confirm; call confirmBooking only after they say yes.")
    public String proposeBooking(@ToolParam(description = "slotId from findOpenSlots") long slotId) {
        if (slotId < 1) {
            return "Error: slotId must be a positive number.";
        }
        session.setAttribute(PendingAction.SESSION_KEY, new PendingAction(PendingAction.Kind.BOOK, slotId, turn));
        return "Booking of slotId=" + slotId + " is proposed but NOT booked. Ask the user to confirm.";
    }

    @Tool(description = "Step 2 of booking: book the proposed slot. Only call after the user explicitly "
            + "confirmed in a new message.")
    public String confirmBooking() {
        return run(() -> {
            PendingAction pending = takeConfirmable(PendingAction.Kind.BOOK);
            if (pending == null) {
                return refusal("booking");
            }
            AppointmentDto booked = appointmentService.book(new BookingRequest(pending.targetId(), null), session);
            return "Booked: appointmentId=" + booked.getAppointmentId() + " | " + booked.getProviderName() + " | "
                    + booked.getServiceName() + " | " + booked.getStartTime() + " to "
                    + booked.getEndTime().toLocalTime();
        });
    }

    @Tool(description = "Step 1 of cancelling: record the appointment the user wants to cancel. Does NOT cancel. "
            + "Ask the user to confirm; call confirmCancel only after they say yes.")
    public String proposeCancel(@ToolParam(description = "appointmentId from listMyAppointments") long appointmentId) {
        if (appointmentId < 1) {
            return "Error: appointmentId must be a positive number.";
        }
        session.setAttribute(PendingAction.SESSION_KEY,
                new PendingAction(PendingAction.Kind.CANCEL, appointmentId, turn));
        return "Cancellation of appointmentId=" + appointmentId + " is proposed but NOT done. Ask the user to confirm.";
    }

    @Tool(description = "Step 2 of cancelling: cancel the proposed appointment. Only call after the user "
            + "explicitly confirmed in a new message.")
    public String confirmCancel() {
        return run(() -> {
            PendingAction pending = takeConfirmable(PendingAction.Kind.CANCEL);
            if (pending == null) {
                return refusal("cancellation");
            }
            appointmentService.cancel(pending.targetId(), session);
            return "Cancelled appointmentId=" + pending.targetId() + ".";
        });
    }

    /**
     * The code-level confirm-before-write guard: returns the pending action only if it has the right
     * kind and was proposed in the turn just before this one, so the user's latest message is the
     * reply to that proposal. An older proposal expires.
     * A used action is removed, so one "yes" can never commit twice.
     */
    private PendingAction takeConfirmable(PendingAction.Kind kind) {
        if (!(session.getAttribute(PendingAction.SESSION_KEY) instanceof PendingAction pending)
                || pending.kind() != kind || pending.turn() >= turn) {
            return null;
        }
        if (pending.turn() != turn - 1) {
            // Stale: the user moved on after the proposal, so this message is not the answer to it.
            session.removeAttribute(PendingAction.SESSION_KEY);
            return null;
        }
        session.removeAttribute(PendingAction.SESSION_KEY);
        return pending;
    }

    private String refusal(String what) {
        return "Error: no " + what + " is waiting for confirmation from an earlier message. Propose it first, "
                + "show the details and wait for the user to reply yes.";
    }

    /** Runs a tool body and turns the service layer's business errors into text for the model. */
    String run(Supplier<String> body) {
        try {
            return body.get();
        } catch (BookingConflictException | ConflictException | InvalidRequestException | NotFoundException
                 | ForbiddenException | UnauthorizedException e) {
            return "Error: " + e.getMessage();
        }
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new InvalidRequestException("Dates must use the format YYYY-MM-DD.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
