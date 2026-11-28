package org.duckdns.gory26.gnucash.book;

/** A split's {@code splits.reconcile_state}. */
public enum ReconcileState {
    /** {@code n}: not reconciled. */
    NOT_RECONCILED('n'),
    /** {@code c}: cleared. */
    CLEARED('c'),
    /** {@code y}: reconciled, with a reconcile date. */
    RECONCILED('y'),
    /** {@code f}: frozen; read only, never written. */
    FROZEN('f'),
    /** {@code v}: voided; read only, never written. */
    VOIDED('v');

    private final char code;

    ReconcileState(char code) {
        this.code = code;
    }

    /** The letter stored in {@code splits.reconcile_state}. */
    public char code() {
        return code;
    }

    public static ReconcileState of(String code) {
        for(ReconcileState state : values()) {
            if(code != null && code.length() == 1 && state.code == code.charAt(0)) {
                return state;
            }
        }
        throw new IllegalArgumentException("unknown reconcile state: " + code);
    }
}
