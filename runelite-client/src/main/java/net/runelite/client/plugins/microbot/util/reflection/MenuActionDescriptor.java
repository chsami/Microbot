package net.runelite.client.plugins.microbot.util.reflection;

/**
 * Descriptor validation and garbage-value normalization shared by the runtime
 * resolver, build-time resource seeder, and persistent cache.
 */
final class MenuActionDescriptor {

    private static final String VANILLA_PREFIX =
            "(IIIIIILjava/lang/String;Ljava/lang/String;II";

    private MenuActionDescriptor() {
    }

    static boolean isVanilla(String descriptor) {
        if (descriptor == null
                || !descriptor.startsWith(VANILLA_PREFIX)
                || !descriptor.endsWith(")V")) {
            return false;
        }

        String garbageDescriptor = descriptor.substring(VANILLA_PREFIX.length(), descriptor.length() - 2);
        return garbageDescriptor.length() == 1 && "BSIJ".contains(garbageDescriptor);
    }

    static Object normalizeGarbage(Object value, String descriptor) {
        if (!isVanilla(descriptor)
                || !(value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long)) {
            return null;
        }

        long integralValue = ((Number) value).longValue();
        char garbageDescriptor = descriptor.charAt(descriptor.length() - 3);
        switch (garbageDescriptor) {
            case 'B':
                return integralValue >= Byte.MIN_VALUE && integralValue <= Byte.MAX_VALUE
                        ? Byte.valueOf((byte) integralValue) : null;
            case 'S':
                return integralValue >= Short.MIN_VALUE && integralValue <= Short.MAX_VALUE
                        ? Short.valueOf((short) integralValue) : null;
            case 'I':
                return integralValue >= Integer.MIN_VALUE && integralValue <= Integer.MAX_VALUE
                        ? Integer.valueOf((int) integralValue) : null;
            case 'J':
                return Long.valueOf(integralValue);
            default:
                return null;
        }
    }
}
