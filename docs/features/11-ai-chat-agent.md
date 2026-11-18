# Feature 11: AI booking chat agent
Endpoint: POST /ai/chat `{ "message": "..." }` → `{ "reply": "..." }`
Status: implemented (2026-11-16)

A customer books, cancels and checks bookings by chatting. Gemini (through
Spring AI) calls tools that wrap the existing services.

## Tools (`BookingTools`, one instance per request, holding the caller's identity)
- `findOpenSlots(from, to, sessionType)`: open slots in a date range (at most 14 days), earliest first, at most 10
- `listMyAppointments()`: the caller's upcoming appointments, at most 10
- `proposeBooking(slotId)` / `confirmBooking()`
- `proposeCancel(appointmentId)` / `confirmCancel()`
- `lookupPolicy(question)`: RAG search over the policy file (feature 13)

## Acceptance criteria
- Customers only: logged out → `401`, provider → `403`
- Blank message or more than 1000 characters → `400`
- Model unavailable (no or bad key, rate limit, timeout) → `503 {"error": "The AI assistant is unavailable right now. Please try again later."}`
- Tools call `SlotService` / `AppointmentService` only, never repositories, so RBAC, the `FOR UPDATE` lock and `UNIQUE (active_slot_id)` still apply
- The caller is never a tool argument: the model cannot act as another user
- Confirm-before-write in code: `propose` stores a pending action tagged with the chat turn; `confirm` runs only for a pending action of the same kind from the immediately previous turn, and uses it up; an older proposal expires. The turn counter is incremented atomically per session
- One message at a time per customer: a message sent while the previous one is still being answered gets `409`, so a confirm can only answer a proposal the customer has seen
- Two customers confirming the same slot: one books, the other gets "Court is already booked." and the agent offers alternatives
- Service errors come back to the model as text (`Error: ...`), not as HTTP errors
- Memory: a random UUID in the `ai_conversations` row keys `MessageChatMemoryAdvisor`, so follow-ups ("actually make it 2pm") work; a new login starts a new conversation
