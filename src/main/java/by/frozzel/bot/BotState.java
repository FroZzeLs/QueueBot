package by.frozzel.bot;

public enum BotState {
    MAIN_MENU,

    // Claim Super Admin
    CLAIM_ADMIN_ENTER_FIO, // <--- НОВОЕ СОСТОЯНИЕ

    // Admin - Group
    ADMIN_GROUP_MENU,
    ADD_STUDENT_NAME,
    ADD_STUDENT_SUBGROUP,
    ADD_STUDENT_TAG,
    REMOVE_STUDENT_NAME,
    ASSIGN_ADMIN_NAME,

    // Admin - Subjects
    ADMIN_SUBJECT_MENU,
    ADD_SUBJECT_NAME,
    REMOVE_SUBJECT_NAME,

    // Queue Flow
    QUEUE_LIST_MENU,
    QUEUE_TYPE_SELECTION,
    QUEUE_SUBGROUP_SELECTION,
    QUEUE_VIEW,

    // Actions
    MARK_LAST_PASSED_NAME,
    REQUEST_SWAP_NAME
}