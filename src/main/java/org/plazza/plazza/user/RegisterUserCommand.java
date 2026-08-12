package org.plazza.plazza.user;

/** Everything needed to register a rider, already normalised by the API layer. */
public record RegisterUserCommand(String name, String phone) {
}
