package model;

public class Admin {
    public static final String USERNAME = "admin";
    public static final String PASSWORD = "admin12345678";

    private final String name;

    public Admin() {
        this.name = USERNAME;
    }

    public String getName() {
        return name;
    }
}
