package com.distrischool.gestao_escolar.event;

public final class KafkaTopics {

    public static final String STUDENT_CREATED = "student.created";

    /** Consumido; produzido pelo gestao-administrativa-service. */
    public static final String SCHEDULE_UPDATED = "schedule.updated";
    public static final String SCHEDULE_UPDATED_DLT = SCHEDULE_UPDATED + ".DLT";

    private KafkaTopics() {
    }
}
