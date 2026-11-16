package com.shuttleflow.ai;

import com.shuttleflow.repository.AppointmentRepository.DueReminder;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** The bonus self-check on its own: which LLM texts may reach a customer. */
class ReminderSelfCheckTest {
    private static DueReminder at(int hour, int minute) {
        LocalDateTime start = LocalDateTime.of(2026, 11, 17, hour, minute);
        return new DueReminder(1, "Alex Nguyen", "alex@shuttleflow.com", "Court 3", "Singles Court Rental",
                start, start.plusHours(1));
    }

    @Test
    void acceptsTheRightProviderAndTimeInCommonFormats() {
        assertThat(ReminderAgent.passesSelfCheck("Hi Alex, Court 3 is yours at 9:00 AM tomorrow!", at(9, 0))).isTrue();
        assertThat(ReminderAgent.passesSelfCheck("See you at court 3 at 9 a.m.", at(9, 0))).isTrue();
        assertThat(ReminderAgent.passesSelfCheck("Court 3 at 21:30 tonight.", at(21, 30))).isTrue();
        assertThat(ReminderAgent.passesSelfCheck("Court 3 at 9:30pm.", at(21, 30))).isTrue();
    }

    @Test
    void rejectsWrongTimeWrongProviderAndPlaceholders() {
        assertThat(ReminderAgent.passesSelfCheck("Court 3 at 9:00 PM.", at(9, 0))).isFalse();
        assertThat(ReminderAgent.passesSelfCheck("Court 3 at 10:00 AM.", at(9, 0))).isFalse();
        assertThat(ReminderAgent.passesSelfCheck("Coach Kim at 9:00 AM.", at(9, 0))).isFalse();
        assertThat(ReminderAgent.passesSelfCheck("Hi [name], Court 3 at 9:00 AM.", at(9, 0))).isFalse();
        assertThat(ReminderAgent.passesSelfCheck("Court 3 at 9:30 AM.", at(9, 0))).isFalse();
    }
}
