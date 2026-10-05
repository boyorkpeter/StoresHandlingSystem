package com.registry.storesapp;

import java.io.PrintStream;

public class Launcher {

    public static void main(String[] args) {
        // Save the original error stream
        PrintStream originalErr = System.err;

        // Temporarily redirect error stream to a dummy stream to choke the JavaFX module warning
        System.setErr(new PrintStream(new java.io.OutputStream() {
            @Override
            public void write(int b) {
                // Do nothing: swallows the text
            }
        }));

        try {
            // Wake up JavaFX (the warning happens right here inside main)
            LoginApplication.main(args);
        } finally {
            // Restore the original error stream immediately so real code bugs still show up
            System.setErr(originalErr);
        }
    }
}