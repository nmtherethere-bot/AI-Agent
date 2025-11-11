package com.example.agent;

public class AgentCliRunner {
    public static void main(String[] args) {
        System.out.println("Agent CLI Runner started.");
        if (args != null && args.length > 0) {
            System.out.println("Args: ");
            for (int i = 0; i < args.length; i++) {
                System.out.println("  [" + i + "]: " + args[i]);
            }
        }
        System.out.println("Environment hint: run .\\env.bat on Windows or 'source .env' on bash to load keys.");
    }
}