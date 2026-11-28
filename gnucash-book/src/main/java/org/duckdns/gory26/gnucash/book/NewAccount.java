package org.duckdns.gory26.gnucash.book;

import java.util.Objects;

/**
 * An account for {@link GnuCashBook#createAccount}, under a GUID the caller chooses. Its SCU is the commodity's
 * fraction. Whether {@code type} suits the parent's type is left to the caller.
 *
 * @param guid       32 lowercase hex characters, not yet used by an account
 * @param name       not blank, without the {@code :} path separator, unique among its siblings
 * @param parentGuid an account of the book, or {@link GnuCashBook#rootAccountGuid()} for a top-level account
 */
public record NewAccount(String guid,
                         String name,
                         String parentGuid,
                         AccountType type,
                         String commodityGuid,
                         boolean placeholder) {

    public NewAccount {
        Slots.checkedGuid(guid, "the account GUID");
        if(name == null || name.isBlank()) {
            throw new IllegalArgumentException("an account name must not be blank");
        }
        if(name.contains(":")) {
            throw new IllegalArgumentException("an account name must not contain ':': " + name);
        }
        Objects.requireNonNull(parentGuid, "parentGuid");
        Objects.requireNonNull(commodityGuid, "commodityGuid");
        if(Objects.requireNonNull(type, "type") == AccountType.ROOT) {
            throw new IllegalArgumentException("a book has one root account");
        }
    }
}
