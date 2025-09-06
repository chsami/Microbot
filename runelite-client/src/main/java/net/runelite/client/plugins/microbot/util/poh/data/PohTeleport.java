package net.runelite.client.plugins.microbot.util.poh.data;

import net.runelite.api.coords.WorldPoint;

public interface PohTeleport {

    /**
     * Executes the teleport.
     *
     * @return true if successful, false otherwise.
     */
    boolean execute();

    WorldPoint getDestination();

    /**
     * Gets the duration of the teleport equal to the amount of tiles that can be ran in the same time.
     *
     * @return number of tiles that can be ran in the same time.
     */
    int getDuration();

    /**
     * Returns a string containing information about the PohTransport, including its Method and Destination.,
     *
     * @return PohTeleportMethod -> Destination.toString()
     */
    String displayInfo();

    /**
     * Enum value used to trace back the PohTransport from the Enum class it originates from
     *
     * @return Enum's value.name()
     */
    String name();

    /**
     * Generates a tab-separated value (TSV) formatted string containing information
     * about the current object for the following header:
     * "Destination	isMembers	EnumValue	Display info	Wilderness level	Duration"
     *
     * @return A string in TSV format composed of the destination coordinates,
     * a constant string "Y", the object's enum value name, display info, a constant integer 19,
     * and the duration, separated by tabs.
     */
    default String getTsvValue() {
        StringBuilder sb = new StringBuilder();
        WorldPoint dest = getDestination();
        String destination = dest.getX() + " " + dest.getY() + " " + dest.getPlane();
        sb
                .append(destination)
                .append("\t")
                .append("Y")
                .append("\t")
                .append(name())
                .append("\t")
                .append(getClass().getSimpleName())
                .append("\t")
                .append(displayInfo())
                .append("\t")
                .append(19)
                .append("\t")
                .append(getDuration())
                .append("\n");

        return sb.toString();
    }
}
