package org.openhab.binding.milllan.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

@NonNullByDefault
public class MillUtil { //TODO: (Nad) Header + JavaDocs

    /**
     * Not to be instantiated.
     */
    private MillUtil() {
    }

    /**
     * Evaluates if the specified character sequence is {@code null}, empty or
     * only consists of whitespace.
     *
     * @param cs the {@link CharSequence} to evaluate.
     * @return {@code false} if {@code cs} is {@code null}, empty or only consists of
     *         whitespace, {@code true} otherwise.
     */
    public static boolean isNotBlank(@Nullable CharSequence cs) {
        return !isBlank(cs);
    }

    /**
     * Evaluates if the specified character sequence is {@code null}, empty or
     * only consists of whitespace.
     *
     * @param cs the {@link CharSequence} to evaluate.
     * @return {@code true} if {@code cs} is {@code null}, empty or only
     *         consists of whitespace, {@code false} otherwise.
     */
    public static boolean isBlank(@Nullable CharSequence cs) {
        if (cs == null) {
            return true;
        }
        int strLen = cs.length();
        if (strLen == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(cs.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
