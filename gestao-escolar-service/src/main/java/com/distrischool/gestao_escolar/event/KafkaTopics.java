package com.distrischool.gestao_escolar.event;

public final class KafkaTopics {

    public static final String STUDENT_CREATED = "student.created";
    public static final String ENROLLMENT_CREATED = "enrollment.created";
    public static final String ENROLLMENT_UPDATED = "enrollment.updated";
    public static final String ATTENDANCE_RECORDED = "attendance.recorded";
    public static final String GRADE_POSTED = "grade.posted";

    /** Consumido; produzido pelo gestao-administrativa-service. */
    public static final String SCHEDULE_UPDATED = "schedule.updated";
    public static final String SCHEDULE_UPDATED_DLT = SCHEDULE_UPDATED + ".DLT";

    private KafkaTopics() {
    }
}
