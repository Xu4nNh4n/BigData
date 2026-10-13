package com.api.supermarket.dto.response;



public class TokenResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType;
    private long accessTokenExpiresIn;

    //contructor
    public TokenResponse(String accessToken, String refreshToken, String tokenType, long accessTokenExpiresIn) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.tokenType = tokenType;
        this.accessTokenExpiresIn = accessTokenExpiresIn;
    }

    //getter and setter
    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public long getAccessTokenExpiresIn() {
        return accessTokenExpiresIn;
    }

    public void setAccessTokenExpiresIn(long accessTokenExpiresIn) {
        this.accessTokenExpiresIn = accessTokenExpiresIn;
    }

    


}
