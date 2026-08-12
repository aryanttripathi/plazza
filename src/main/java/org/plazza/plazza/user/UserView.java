package org.plazza.plazza.user;

/**
 * What other modules are allowed to know about a rider.
 * <p>
 * Crossing a module boundary as an immutable record rather than a managed JPA entity means callers
 * cannot mutate user state behind the owning module's back, and the entity stays free to change.
 */
public record UserView(String id, String name, String phone) {
}
