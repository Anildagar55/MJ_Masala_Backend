package in.mjmasala.api.domain;

/** Why an OTP was issued. A code for one purpose is never accepted for another. */
public enum OtpPurpose {
    /** Proving a new account's email and mobile belong to the person signing up. */
    REGISTER,
    /** Proving ownership of an account before letting the password be changed. */
    RESET
}
