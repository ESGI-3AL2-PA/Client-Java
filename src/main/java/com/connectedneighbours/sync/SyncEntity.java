package com.connectedneighbours.sync;

/**
 * Les entités transportées par le flux de synchronisation, et la table H2
 * correspondante. Le nom {@code wire} est celui utilisé par l'api dans
 * {@code /ingest} et {@code /changes}.
 *
 * <p>{@code DISTRICT} est <em>lecture seule</em> (§5.3 du design) : les quartiers
 * sont gérés sur le web et ne descendent que serveur → client. Ils n'ont pas
 * d'{@code updatedAt}, donc pas de jeton de concurrence optimiste, et ne sont
 * jamais poussés.</p>
 */
public enum SyncEntity {

    USER("user", "users", true),
    INCIDENT("incident", "incidents", true),
    DISTRICT("district", "districts", false);

    private final String wire;
    private final String table;
    private final boolean pushable;

    SyncEntity(String wire, String table, boolean pushable) {
        this.wire = wire;
        this.table = table;
        this.pushable = pushable;
    }

    /**
     * @return l'entité correspondant au nom du protocole, ou {@code null} si
     * l'api envoie une entité que ce client ne connaît pas encore (le flux doit
     * rester lisible par une version plus ancienne).
     */
    public static SyncEntity fromWire(String wire) {
        for (SyncEntity entity : values()) {
            if (entity.wire.equals(wire)) {
                return entity;
            }
        }
        return null;
    }

    public String getWire() {
        return wire;
    }

    public String getTable() {
        return table;
    }

    public boolean isPushable() {
        return pushable;
    }
}
