package de.fhg.iais.roberta.connection.arduino;

public enum ArduinoType {
    UNO ("Uno"),
    MEGA ("Mega"),
    NANO ("Nano");

    private final String text;

    ArduinoType(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return this.text;
    }

    public static ArduinoType fromString(String text) {
        for (ArduinoType b : ArduinoType.values()) {
            if (text.equalsIgnoreCase(b.toString())) {
                return b;
            }
        }
        throw new IllegalArgumentException("No constant with text " + text + " found");
    }
}
