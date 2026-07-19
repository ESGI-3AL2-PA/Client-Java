package com.connectedneighbours.sync;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Réponse de {@code POST /ingest} (§4.1). Les trois listes se lisent
 * différemment côté client :
 *
 * <ul>
 *   <li>{@code applied} — écrit côté serveur. On avance {@code base_updated_at}
 *       depuis l'ack et on retire la ligne en attente.</li>
 *   <li>{@code conflicts} — mis en quarantaine, rien n'a été écrit. On
 *       <em>garde</em> la ligne en attente (l'édition locale n'est pas perdue)
 *       et on lève le badge de conflit.</li>
 *   <li>{@code rejected} — refusé pour une raison d'autorisation
 *       (hors-district, entité en lecture seule). Ce n'est pas un conflit : on
 *       <em>supprime</em> la ligne et on remonte l'erreur, puisque rejouer ne
 *       pourra jamais réussir.</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IngestResult {

    private List<AppliedEvent> applied = List.of();
    private List<ConflictAck> conflicts = List.of();
    private List<RejectedEvent> rejected = List.of();

    public IngestResult() {
    }

    public List<AppliedEvent> getApplied() {
        return applied;
    }

    public void setApplied(List<AppliedEvent> applied) {
        this.applied = applied != null ? applied : List.of();
    }

    public List<ConflictAck> getConflicts() {
        return conflicts;
    }

    public void setConflicts(List<ConflictAck> conflicts) {
        this.conflicts = conflicts != null ? conflicts : List.of();
    }

    public List<RejectedEvent> getRejected() {
        return rejected;
    }

    public void setRejected(List<RejectedEvent> rejected) {
        this.rejected = rejected != null ? rejected : List.of();
    }

    /**
     * Un événement écrit côté serveur. {@code updatedAt} est la valeur
     * <em>persistée</em> — c'est elle qui devient le nouveau jeton de
     * concurrence optimiste local. Elle est {@code null} pour un DELETE.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AppliedEvent {

        private long id;
        private String mongoId;
        private String operation;
        private String updatedAt;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getMongoId() {
            return mongoId;
        }

        public void setMongoId(String mongoId) {
            this.mongoId = mongoId;
        }

        public String getOperation() {
            return operation;
        }

        public void setOperation(String operation) {
            this.operation = operation;
        }

        public String getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(String updatedAt) {
            this.updatedAt = updatedAt;
        }
    }

    /**
     * Un événement mis en quarantaine. {@code conflictId} pointe la ligne de
     * {@code sync_conflicts} à résoudre depuis l'écran des conflits.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConflictAck {

        private long id;
        private String mongoId;
        private String conflictId;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getMongoId() {
            return mongoId;
        }

        public void setMongoId(String mongoId) {
            this.mongoId = mongoId;
        }

        public String getConflictId() {
            return conflictId;
        }

        public void setConflictId(String conflictId) {
            this.conflictId = conflictId;
        }
    }

    /**
     * Un événement refusé. {@code reason} vaut aujourd'hui
     * {@code out-of-district} ou {@code read-only-entity}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RejectedEvent {

        private long id;
        private String reason;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
