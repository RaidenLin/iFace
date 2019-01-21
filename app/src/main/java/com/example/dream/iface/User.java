package com.example.dream.iface;

public class User {
    private String stu_id;
    private String user_name;
    private String sex;
    private String user_class;

    public String getStu_id() {
        return stu_id;
    }

    public void setStu_id(String stu_id) {
        this.stu_id = stu_id;
    }

    public String getUser_name() {
        return user_name;
    }

    public void setUser_name(String user_name) {
        this.user_name = user_name;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getUser_class() {
        return user_class;
    }

    public void setUser_class(String user_class) {
        this.user_class = user_class;
    }
}
