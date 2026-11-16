# Milestone 4 AI transcripts (real Gemini, gemini-3.5-flash-lite)

Recorded 2026-11-16 against the running app (POST /ai/chat, /ai/ask, /ai/reminders/run) for M4 checklist item 9 and the report.

## Scenario 1: multi-step booking (find, propose, wait for yes, confirm)

USER: Book the earliest open court this week
ASSISTANT: I found an open court for you:
- Court 3 (Singles Court Rental)
- Tue Nov 17, 9:00-10:00 AM
- Price: $20.00

Would you like to confirm this booking?

USER: yes
ASSISTANT: Your booking is confirmed! 

- **Appointment ID:** 2
- **Court 3** (Singles Court Rental)
- **Date & Time:** Tue Nov 17, 2026, 9:00 - 10:00 AM
- **Price:** $20.00


## Scenario 3: multi-turn follow-up

USER: Book me a court tomorrow evening
ASSISTANT: I found an open court for tomorrow evening:

* **Court 3** (Doubles Court Rental)
* **Date:** Tue Nov 17, 2026
* **Time:** 6:00 PM – 7:00 PM
* **Price:** $20.00

Would you like to confirm this booking?

USER: actually make it Thursday at 7pm
ASSISTANT: I found an open court for Thursday at 7:00 PM:

* **Court 3** (Singles Court Rental)
* **Date:** Thu Nov 19, 2026
* **Time:** 7:00 PM – 8:00 PM
* **Price:** $20.00

Would you like to confirm this booking?

USER: yes please
ASSISTANT: Your court booking is confirmed! 

* **Court:** Court 3 (Singles Court Rental)
* **Date:** Thu Nov 19, 2026
* **Time:** 7:00 PM – 8:00 PM
* **Appointment ID:** 3


## Scenario 2: slot taken between propose and confirm

USER: Book a coaching session with Coach Kim tomorrow at 4pm
ASSISTANT: I found a coaching session with Coach Kim tomorrow (Tuesday, Nov 17) from 4:00 to 5:00 PM for $40.00. Would you like to confirm this booking?

(meanwhile Jamie books slot 5 through POST /appointments: HTTP 201)

USER: yes
ASSISTANT: That slot is no longer available. Here are the nearest open coaching sessions with Coach Kim:

1. Thu Nov 19, 3:00-4:00 PM ($40.00)
2. Sat Nov 21, 5:00-6:00 PM ($40.00)

Would you like to book one of these instead?


## Policy Q&A (POST /ai/ask)

QUESTION: Can I cancel a booking, and is there a fee?
ANSWER: Yes, you can cancel your own booked appointment at any time before it starts, at no charge.
SOURCES: ['Cancellation', 'Booking', 'Contact']

QUESTION: How much is private coaching?
ANSWER: Private coaching is $40.00 for a 60-minute session.
SOURCES: ['Services and prices', 'Courts and coaches', 'Equipment']

QUESTION: Do you have a swimming pool?
ANSWER: I don't know. Please email support@shuttleflow.com.
SOURCES: ['Contact', 'Equipment', 'Courts and coaches']


## Reminder agent (POST /ai/reminders/run as the court manager)
Run 1: {"scanned":2,"queued":2,"fallbacks":0}
Run 2: {"scanned":0,"queued":0,"fallbacks":0}
Outbox:
REMINDER:1|SENT|Hi Alex, friendly reminder about your Singles Court Rental at Court 3 starting Monday Nov 16 at 7:52 PM. See you on the court!
REMINDER:2|SENT|Hi Alex, this is a friendly reminder for your Singles Court Rental at Court 3, starting Tuesday Nov 17 at 9:00 AM. We can't wait to see you on the court!
Metrics: {"bookingsCreated":3,"bookingConflicts":1,"notificationsSent":5,"notificationFailures":0,"pendingNotifications":0,"remindersQueued":2,"reminderFallbacks":0}
