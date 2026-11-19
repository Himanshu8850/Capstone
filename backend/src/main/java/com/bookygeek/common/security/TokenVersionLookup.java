package com.bookygeek.common.security;

import java.util.OptionalInt;

/**
 * Port implemented by the User module: the customer's current {@code token_version}, or empty when the customer no
 * longer exists. The security layer compares it with the version inside the token, so logout (which bumps the version)
 * invalidates every previously issued token. Keeping this as a port in {@code common.security} means the security
 * code never reads another module's repository or entity.
 */
@FunctionalInterface
public interface TokenVersionLookup {

    OptionalInt currentTokenVersion(long customerId);
}
