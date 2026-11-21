package vn.intrachat.domain.history;

/** Domain decision only; authenticated evidence verification precedes any DB write. */
public enum HistoryConflictDecision { INSERT, DUPLICATE, QUARANTINE }
