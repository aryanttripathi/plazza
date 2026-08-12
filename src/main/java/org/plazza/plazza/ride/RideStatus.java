package org.plazza.plazza.ride;

/** Ride lifecycle. Both terminal states release the driver back into the available pool. */
public enum RideStatus {

    ONGOING,
    COMPLETED,
    CANCELLED;

    public boolean isTerminal() {
        return this != ONGOING;
    }
}
