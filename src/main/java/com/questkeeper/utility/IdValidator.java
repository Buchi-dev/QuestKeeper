package com.questkeeper.utility;

import java.util.regex.Pattern;

public final class IdValidator {
    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9_]{3,50}$");
    private IdValidator() { }
    public static boolean isValid(String value) { return value != null && PATTERN.matcher(value).matches(); }
}
