# ShuttleFlow Policies and FAQ

ShuttleFlow is the booking system for ShuttleFlow Arena, a badminton facility with court rentals
and private coaching. Rules marked "enforced by the system" are checked by the ShuttleFlow
application itself. Facility details (hours, equipment, contact) are demo information for this
course project.

## Hours

ShuttleFlow Arena is open every day from 7:00 AM to 10:00 PM. Courts and coaches can only be booked
during the slots that the court manager or coach has published; opening hours alone do not make a
time bookable.

## Courts and coaches

- Court 3 is in ShuttleFlow Arena, Building A. It is managed by the Court Manager.
- Coach Kim teaches in ShuttleFlow Arena, Training Room 1.
- Session types: OPEN_PLAY means renting a court; COACHING means a session with a coach.

## Services and prices

| Service | Provider | Length | Players | Price |
|---|---|---|---|---|
| Singles Court Rental | Court 3 | 60 minutes | up to 2 | $20.00 |
| Doubles Court Rental | Court 3 | 60 minutes | up to 4 | $20.00 |
| Private Coaching Session | Coach Kim | 60 minutes | 1 | $40.00 |

## Booking

- You need a customer account and must be logged in to book (enforced by the system).
- Only open slots that have not started yet can be booked (enforced by the system).
- Each slot can be booked by only one customer. If two people try to book the same slot at the
  same time, one booking succeeds and the other gets "Court is already booked." (enforced by the
  database).
- Slots can be filtered by date and session type, and are shown 10 per page, earliest first.
- After booking, you receive a booking confirmation notification.
- The booking assistant always shows the slot details and asks you to confirm before it books.

## Cancellation

- You can cancel your own booked appointment at any time before it starts, at no charge
  (enforced by the system).
- Appointments that have already started or ended cannot be cancelled (enforced by the system).
- You cannot cancel another customer's appointment (enforced by the system).
- A cancelled slot is reopened right away so someone else can book it, and you can book it again.
- After cancelling, you receive a cancellation notification.
- Cancelled appointments stay in your booking history.

## Reminders

- ShuttleFlow sends a reminder for each booked appointment that starts within the next 24 hours.
- Each appointment gets at most one reminder.

## Providers (court managers and coaches)

- Providers log in with a provider account and cannot book courts as customers (enforced by the
  system).
- Providers publish availability slots for their own services. A slot must start in the future,
  end after it starts, and cannot duplicate an existing start time for the same provider
  (enforced by the system).
- Providers can remove only their own open slots; booked slots cannot be removed (enforced by the
  system).
- Providers can see the appointments booked with them.

## Equipment

Shuttlecocks are provided for coaching sessions. Court renters bring their own rackets and
shuttlecocks; non-marking indoor shoes are required on all courts.

## Contact

For questions about bookings, email support@shuttleflow.com or ask at the front desk in
Building A.
