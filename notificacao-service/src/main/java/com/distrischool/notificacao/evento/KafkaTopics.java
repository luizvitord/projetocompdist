package com.distrischool.notificacao.evento;

import java.util.List;

/** Tópicos consumidos; todos produzidos por outros serviços. */
public final class KafkaTopics {

    // gestao-escolar-service
    public static final String GRADE_POSTED = "grade.posted";
    public static final String ATTENDANCE_RECORDED = "attendance.recorded";
    public static final String ENROLLMENT_CREATED = "enrollment.created";
    public static final String ENROLLMENT_UPDATED = "enrollment.updated";
    public static final String STUDENT_CREATED = "student.created";

    // gestao-administrativa-service
    public static final String TEACHER_ASSIGNED = "teacher.assigned";
    public static final String STAFF_CREATED = "staff.created";
    public static final String SCHEDULE_UPDATED = "schedule.updated";

    /** Sufixo da DLT, a mesma convenção do gestao-escolar-service ({@code <tópico>.DLT}). */
    public static final String SUFIXO_DLT = ".DLT";

    public static final List<String> TODOS = List.of(GRADE_POSTED, ATTENDANCE_RECORDED, ENROLLMENT_CREATED,
            ENROLLMENT_UPDATED, STUDENT_CREATED, TEACHER_ASSIGNED, STAFF_CREATED, SCHEDULE_UPDATED);

    private KafkaTopics() {
    }
}
