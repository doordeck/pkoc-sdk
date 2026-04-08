package com.sentryinteractive.opencredential.sdk

import com.sentryinteractive.opencredential.api.common.Identity

/**
 * A typed identity — either an email address or a phone number.
 */
sealed class OCIdentity {
    abstract val value: String

    data class Email(val email: String) : OCIdentity() {
        override val value: String get() = email
    }

    data class Phone(val phone: String) : OCIdentity() {
        override val value: String get() = phone
    }

    internal fun toProto(): Identity = when (this) {
        is Email -> Identity.newBuilder().setEmail(email).build()
        is Phone -> Identity.newBuilder().setPhone(phone).build()
    }

    internal companion object {
        fun fromProto(identity: Identity): OCIdentity? = when {
            identity.hasEmail() -> Email(identity.email)
            identity.hasPhone() -> Phone(identity.phone)
            else -> null
        }
    }
}
