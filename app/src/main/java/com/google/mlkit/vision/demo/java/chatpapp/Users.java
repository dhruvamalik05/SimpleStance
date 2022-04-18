package com.google.mlkit.vision.demo.java.chatpapp;

public class Users {
    String uid;
    String name;
    String emailID;
    String age;
    String imageUri;

    public Users() {
    }

    public Users(String uid, String name, String emailID, String age, String imageUri) {
        this.uid = uid;
        this.name = name;
        this.emailID = emailID;
        this.age = age;
        this.imageUri = imageUri;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmailID() {
        return emailID;
    }

    public void setEmailID(String emailID) {
        this.emailID = emailID;
    }

    public String getAge() {
        return age;
    }

    public void setAge(String age) {
        this.age = age;
    }

    public String getImageUri() {
        return imageUri;
    }

    public void setImageUri(String imageUri) {
        this.imageUri = imageUri;
    }
}
