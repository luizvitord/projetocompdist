package com.distrischool.gestao_administrativa.event;

public final class KafkaTopics {

    public static final String TEACHER_ASSIGNED = "teacher.assigned";
    public static final String SCHEDULE_UPDATED = "schedule.updated";
    public static final String STAFF_CREATED = "staff.created";

    private KafkaTopics() {
    }
}
