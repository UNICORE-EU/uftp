package eu.unicore.uftp.standalone.util;

import java.io.Console;

public class ConsoleUtils {

	public static String readParameterFromConsole(String promt) {
        Console console = System.console();
        if (console == null) {
            return null;
        }
        return console.readLine(promt);
    }

	public static String readPassword() {
        return readPassword("Password:");
    }

	public static String readPassword(String prompt) {
        Console console = System.console();
        if (console == null) {
            return null;
        }
        return new String(console.readPassword(prompt));
    }

}
