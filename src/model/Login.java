package model;

public class Login {
    private String name;
    private String userType;
    private String secret;

    public Login(String name, String userType, String secret) {
        this.name = name;
        this.userType = userType;
        this.secret = secret;
    }

    public String getName() { return name; }
    public String getUserType() { return userType; }
    public String getSecret() { return secret; }
}
